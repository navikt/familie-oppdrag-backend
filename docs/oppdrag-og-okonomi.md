# Oppdrag og økonomi

Slik henger familie-oppdrag sammen med saksapplikasjonene og Oppdragssystemet (OS). Begrepene er definert i [GLOSSARY.md](../GLOSSARY.md).

## Hva familie-oppdrag gjør

Familie-oppdrag er et lag mellom saksapplikasjonene for familieytelsene og Oppdragssystemet. Saksapplikasjonene slipper å kjenne XML-formatene, køene og SOAP-tjenestene til Oppdragssystemet.

Flyten for en utbetaling:

1. Saksapplikasjonen beregner andeler og lager et utbetalingsoppdrag med biblioteket [familie-utbetalingsgenerator](https://github.com/navikt/familie-utbetalingsgenerator).
2. Saksapplikasjonen sender utbetalingsoppdraget til `POST /api/oppdrag`.
3. Familie-oppdrag lagrer oppdraget i `oppdrag_lager`, gjør det om til XML og legger det på en MQ-kø som Oppdragssystemet leser.
4. Oppdragssystemet svarer med en kvittering på en annen MQ-kø. Familie-oppdrag oppdaterer statusen i `oppdrag_lager`.
5. Saksapplikasjonen spør familie-oppdrag om statusen til den er kvittert.

Simulering går som et SOAP-kall via [familie-ws-proxy](https://github.com/navikt/familie-ws-proxy). Avstemming går på en egen MQ-kø.

## Kjeder

En kjede er utbetalingsperiodene for én person og én ytelsestype i en fagsak. I barnetrygd har hvert barn typisk sin egen kjede for ordinær barnetrygd, og søkeren har egne kjeder, for eksempel for utvidet barnetrygd og småbarnstillegg. Vi kunne hatt én kjede for alt, men med én kjede per barn endrer vi bare kjedene til barna som blir berørt av behandlingen.

Hver utbetalingsperiode har en `periodeId` og en `forrigePeriodeId`:

- `periodeId` er unik i fagsaken og telles fortløpende på tvers av alle kjedene.
- `forrigePeriodeId` peker på utbetalingsperioden som ble sendt før i samme kjede. Det er den forrige som ble sendt, ikke den med nærmest fom eller tom.

Fordi nummereringen er felles for hele fagsaken, blir det hopp i `periodeId` innenfor én kjede:

| Behandling | Kjede | `periodeId` | `forrigePeriodeId` |
|---|---|---|---|
| 1 | Barn A | 0 | |
| 1 | Barn A | 1 | 0 |
| 1 | Barn B | 2 | |
| 2 | Barn A | 3 | 1 |

En ny utbetalingsperiode overskriver hele kjeden fra og med sin fom. Perioder i kjeden etter denne datoen faller bort hvis de ikke sendes på nytt. Det betyr at den siste utbetalingsperioden som er sendt for et tidsrom, bestemmer beløpet.

Et opphør sendes som en endring på en eksisterende utbetalingsperiode, med samme `periodeId` og en dato kjeden skal stoppe fra.

Oppdragssystemet kjenner utbetalingsperioden som en oppdragslinje med en delytelse-ID. Familie-oppdrag lager delytelse-ID-en ved å sette saksnummeret og `periodeId` etter hverandre.

Mer om kjeding:

- [kjeding.md fra Helved](https://github.com/navikt/helved-utbetaling/blob/4507bd30fd492831895c785e810d7b9de1d97c1c/dokumentasjon/kjeding.md)
- [familie-utbetalingsgenerator](https://github.com/navikt/familie-utbetalingsgenerator), særlig KDoc i `Utbetalingsgenerator.kt` og `BeståendeAndelerBeregner.kt`

## Kvittering og status

Oppdragssystemet svarer på hvert utbetalingsoppdrag med en kvittering. Alvorlighetsgraden i kvitteringen bestemmer statusen hos oss:

| Alvorlighetsgrad | Betydning | Status i `oppdrag_lager` |
|---|---|---|
| | Sendt, ingen kvittering ennå | `LAGT_PÅ_KØ` |
| 00 | OK | `KVITTERT_OK` |
| 04 | Godkjent med varsel | `KVITTERT_MED_MANGLER` |
| 08 | Avvist, funksjonell feil | `KVITTERT_FUNKSJONELL_FEIL` |
| 12 | Avvist, teknisk feil | `KVITTERT_TEKNISK_FEIL` |
| Ingen | Kvitteringen har ingen melding | `KVITTERT_UKJENT` |

Saksapplikasjonen henter status med `POST /api/status` og fagsystem, personident og behandlings-ID. Svaret har statusen og den beskrivende meldingen fra kvitteringen (`beskrMelding`). Oppslaget leser bare versjon 0 av oppdraget.

Sender saksapplikasjonen samme behandling til familie-oppdrag to ganger, svarer familie-oppdrag `409 Conflict` uten å sende noe til Oppdragssystemet. Det er ikke det samme som at Oppdragssystemet avviser oppdraget.

### Når Oppdragssystemet avviser et oppdrag

Statusen og `beskrMelding` er ofte ikke nok til å forstå hva som gikk galt. Hele kvitteringsmeldingen ligger i kolonnen `kvitteringsmelding` i `oppdrag_lager`. Denne spørringen viser kvitteringen for en behandling:

```sql
SELECT fagsystem, fagsak_id, behandling_id, versjon, status, opprettet_tidspunkt,
       kvitteringsmelding ->> 'alvorlighetsgrad' AS alvorlighetsgrad,
       kvitteringsmelding ->> 'kodeMelding'      AS kode_melding,
       kvitteringsmelding ->> 'beskrMelding'     AS beskr_melding
FROM oppdrag_lager
WHERE fagsak_id = :fagsakId
  AND behandling_id = :behandlingId
ORDER BY versjon;
```

Denne viser hvilke feilkoder som har kommet tilbake:

```sql
SELECT kvitteringsmelding ->> 'alvorlighetsgrad' AS alvorlighetsgrad,
       kvitteringsmelding ->> 'kodeMelding'      AS kode_melding,
       kvitteringsmelding ->> 'beskrMelding'     AS beskr_melding,
       count(*)                                  AS antall
FROM oppdrag_lager
WHERE status <> 'KVITTERT_OK'
  AND kvitteringsmelding IS NOT NULL
GROUP BY 1, 2, 3
ORDER BY antall DESC;
```

Eksempler på avvisninger vi har sett:

| `kodeMelding` | `beskrMelding` |
|---|---|
| `BB50022F` | DELYTELSE-ID finnes i oppdragsbasen fra før |
| `BB50025F` | Referert linje er korrigert tidligere |
| `BB50050F` | Ugyldig endring av linjestatus på korrigert linje |
| `BB15003F` | For mange overlappende oppdrag på fagområdet |

`BB50022F` kommer når Oppdragssystemet allerede har fått en utbetalingsperiode med samme delytelse-ID. Det skjer typisk når et oppdrag gikk gjennom, men vi fikk tidsavbrudd og sendte det på nytt.

Familie-oppdrag håndterer ikke disse kodene. Er meldingen uklar, spør vi Oppdrag hva den betyr.

## Simulering

Simulering er en tørrkjøring. Den viser saksbehandleren hva som ville skjedd hvis behandlingen ble iverksatt slik den er nå. Saksapplikasjonen sender samme utbetalingsoppdrag som ved iverksetting til `POST /api/simulering/v1`. Familie-oppdrag sender det til Oppdragssystemet og returnerer posteringene, gruppert per mottaker. Forespørsel og svar lagres i `simulering_lager`.

Vi bruker to typer posteringer:

- **YTEL**: Positive posteringer er nye utbetalinger. Negative posteringer tilbakefører det som er utbetalt før. Er summen ulik 0, endrer behandlingen hva brukeren får utbetalt.
- **FEIL**: Summen av de positive FEIL-posteringene er feilutbetalingen.

`POST /api/simulering/feilutbetalinger` regner ut feilutbetalingen per periode fra siste lagrede simulering:

- Feilutbetalt beløp er summen av de positive FEIL-posteringene.
- Tidligere utbetalt er summen av de negative YTEL-posteringene, uten fortegn.
- Nytt beløp er summen av de positive YTEL-posteringene minus feilutbetalt beløp.

Simuleringssvaret kan også ha andre typer posteringer. Se [Åpne spørsmål](#åpne-spørsmål) om motregning.

### Regler i saksapplikasjonene

- **Enslig forsørger:** Er feilutbetalingen under 4 rettsgebyr, kan saksbehandler velge at det ikke blir tilbakekreving. Det forenkler tilbakekrevingsløypa. Rettsgebyret er 1 345 kroner i 2026, så grensen er 5 380 kroner.
- **Barnetrygd:** En etterbetaling og en feilutbetaling skal ikke trekkes fra hverandre, selv om de går i null. Brukeren skal kunne klage på feilutbetalingen.

## Manuelle posteringer

En manuell postering betyr at Nav Økonomi Stønad (NØS) har endret utbetalingene til en stønadsmottaker for hånd. Det bryter ofte med dobbel bokføring. I ettertid er det vanskelig å vite hva hensikten var, og vi må tolke det ut fra sammenhengen. Derfor må saksbehandleren følge dem opp.

Det er saksapplikasjonen som oppdager manuelle posteringer i simuleringssvaret, ikke familie-oppdrag:

- I enslig forsørger sier saksapplikasjonen fra til saksbehandleren.
- I barnetrygd må saksbehandleren avgjøre hva som skal gjøres.

## Avstemming

Vi kjenner ikke alle detaljene i hvordan Oppdragssystemet behandler avstemmingene.

### Grensesnittavstemming

Grensesnittavstemmingen kjører daglig. Saksapplikasjonen kaller `POST /api/grensesnittavstemming` med fagsystem og tidsrom. Familie-oppdrag sender:

1. En startmelding.
2. Detaljlinjer for oppdrag som mangler kvittering, har fått varsel eller er avvist.
3. Antall og sum per gruppe: godkjent, varsel, mangler og avvist.
4. En sluttmelding.

Er samme avstemmings-ID kjørt før, gjør familie-oppdrag ingenting.

### Konsistensavstemming

Konsistensavstemmingen kjører én gang i måneden, på kjøredatoene fra Oppdrag. Den sender alle aktive utbetalingsperioder for hver fagsak, slik at Oppdragssystemet kan sammenligne med det de har.

Meldingene er en startmelding, en datamelding per fagsak, en totalmelding og en avsluttmelding. Saksapplikasjonen kan dele kjøringen i flere kall til `POST /api/v2/konsistensavstemming`:

- `sendStartmelding` og `sendAvsluttmelding` styrer om kallet sender startmelding og avsluttmelding.
- `transaksjonsId` knytter kallene sammen. Familie-oppdrag lagrer delsummene i `mellomlagring_konsistensavstemming` til siste kall.

Enslig forsørger sender hele konsistensavstemmingen i ett kall. Barnetrygd deler den i flere kall.

Avbrytes et kall, for eksempel fordi en applikasjon starter på nytt, blir avsluttmeldingen aldri sendt. Meldingene som allerede er lagt på køen blir liggende, fordi MQ-sendingen ikke er en del av databasetransaksjonen. Da må hele konsistensavstemmingen kjøres på nytt.

Oppdrag sender avviksmeldingene på e-post, ofte som skjermbilde eller Excel-ark. Meldingene er kryptiske. Et typisk avvik er at Oppdragssystemet ikke har oppdatert et fødselsnummer.

## Kravgrunnlag

Når Oppdragssystemet oppdager en feilutbetaling, sender det et kravgrunnlag på en kø som tilbakekrevingsløsningen leser. Kravgrunnlaget går ikke via familie-oppdrag. Køen bestilles fra Oppdrag når vi setter opp en ny applikasjon.

## Faste rutiner

### Kjøredatoer for konsistensavstemming

Oppdrag sender en liste med kjøredatoer for konsistensavstemming hvert år, på Slack. Kommer ikke listen, må vi purre.

## Åpne spørsmål

### Motregning

Ordet motregning brukes om minst tre ting, og vi må undersøke det nærmere:

- En egen type postering i simuleringssvaret.
- Posteringer fra Infotrygd når utbetalingen overlapper med det Infotrygd har sendt. Vi skal til slutt ta over hele utbetalingen.
- Trekk av tilbakekrevingskrav i framtidige utbetalinger, som Gjenny kaller restanser.
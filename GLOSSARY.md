# familie-oppdrag

Familie-oppdrag står mellom saksapplikasjonene for familieytelsene og Oppdragssystemet. Den skjuler integrasjonen mot Oppdragssystemet, slik at saksapplikasjonene slipper å kjenne formatene og køene der.

## Systemer og aktører

**Oppdragssystemet (OS)**:
Navs system for utbetaling av ytelser. Det tar imot utbetalingsoppdrag, utbetaler og bokfører.
_Unngå_: oppdrag (om selve systemet)

**Saksapplikasjon**:
Applikasjonen der en ytelse saksbehandles, for eksempel barnetrygd, kontantstøtte eller enslig forsørger. Den beregner hva som skal utbetales og sender det via familie-oppdrag.

**Nav Økonomi Stønad (NØS)**:
Enhet i Nav som kan endre utbetalinger i Oppdragssystemet for hånd.

## Utbetaling

**Utbetalingsoppdrag**:
Alt en behandling sender til Oppdragssystemet for én fagsak: en samling utbetalingsperioder.

**Utbetalingsperiode**:
Ett beløp som skal utbetales i en periode, med fra og med-dato (fom) og til og med-dato (tom). Oppdragssystemet kaller det en oppdragslinje.
_Unngå_: kjedeelement

**Andel**:
Saksapplikasjonens beregnede beløp for én person og ytelsestype i en periode. En andel blir til en utbetalingsperiode når den sendes.

**Kjede**:
Utbetalingsperiodene for én person og én ytelsestype i en fagsak, lenket i den rekkefølgen de ble sendt.

**PeriodeId**:
Nummeret til en utbetalingsperiode. Det er unikt i fagsaken og telles fortløpende på tvers av alle kjedene.
_Unngå_: kjede-ID

**ForrigePeriodeId**:
PeriodeId-en til utbetalingsperioden som ble sendt før i samme kjede.

**Delytelse-ID**:
Oppdragssystemets ID for en utbetalingsperiode, laget av saksnummeret og periodeId-en.

**Opphør**:
Melding om at en kjede ikke skal utbetale noe fra og med en gitt dato.

## Kvittering

**Kvittering**:
Svaret fra Oppdragssystemet på et utbetalingsoppdrag.

**Alvorlighetsgrad**:
Koden i kvitteringen som sier hvordan det gikk: 00 er OK, 04 er godkjent med varsel, 08 er avvist med funksjonell feil og 12 er avvist med teknisk feil.
_Unngå_: feilkode

## Simulering

**Simulering**:
En tørrkjøring som viser hva Oppdragssystemet ville gjort hvis behandlingen ble iverksatt slik den er nå.

**Postering**:
En bokføringslinje i Oppdragssystemet. Simuleringen svarer med posteringene som ville blitt ført.

**Feilutbetaling**:
Beløp som er utbetalt, men som brukeren ikke hadde rett til.

**Manuell postering**:
En postering som NØS har lagt inn for hånd på en stønadsmottaker, utenom saksapplikasjonen.

**Kravgrunnlag**:
Oppdragssystemets grunnlag for å kreve tilbake en feilutbetaling.

## Avstemming

**Grensesnittavstemming**:
Daglig kontroll av at antall og sum for oppdragene vi har sendt stemmer med det Oppdragssystemet har mottatt.

**Konsistensavstemming**:
Månedlig kontroll av at alle aktive utbetalingsperioder hos oss stemmer med Oppdragssystemet, sak for sak.

**Kjøredato**:
En dag Oppdragssystemet har satt av til konsistensavstemming. Oppdragssystemet sender kjøredatoene for et helt år om gangen.

**Avviksmelding**:
Oppdragssystemets liste over saker der konsistensavstemmingen ikke stemmer.

package no.nav.familie.oppdrag.simulering.sokos

import no.nav.familie.kontrakter.felles.oppdrag.Utbetalingsoppdrag
import no.nav.familie.kontrakter.felles.simulering.DetaljertSimuleringResultat
import no.nav.familie.kontrakter.felles.simulering.FagOmrådeKode
import no.nav.familie.kontrakter.felles.simulering.PosteringType
import no.nav.familie.kontrakter.felles.simulering.SimuleringMottaker
import no.nav.familie.kontrakter.felles.simulering.SimulertPostering
import no.nav.familie.oppdrag.config.IntegrasjonException
import no.nav.familie.oppdrag.config.Integrasjonssystem
import no.nav.familie.oppdrag.simulering.SimuleringUtil.hentOrgNrEllerFnr
import no.nav.familie.oppdrag.simulering.SimuleringUtil.parseDato
import no.nav.familie.oppdrag.simulering.SimuleringUtil.utledBetalingType
import no.nav.familie.oppdrag.simulering.SimuleringUtil.utledMottakerType
import no.nav.familie.oppdrag.simulering.sokos.SokosSimulerBeregningResponse.BeregningStoppnivaa
import no.nav.familie.oppdrag.simulering.sokos.SokosSimulerBeregningResponse.PeriodeStoppnivaa
import no.nav.familie.oppdrag.simulering.sokos.SokosSimulerBeregningResponse.StoppnivaaDetaljer
import org.slf4j.LoggerFactory

object SokosSimuleringResultatMapper {
    private val logger = LoggerFactory.getLogger(SokosSimuleringResultatMapper::class.java)

    // Alvorlighetsgrad fra Oppdrag: 0 OK, 4 varsel, 8 funksjonell feil, 12 teknisk feil.
    private const val STATUSKODE_FUNKSJONELL_FEIL = 8

    // Kontrakten tillater maks 1200 periodeStoppnivaa og 25 detaljer per stoppnivå.
    private const val MAKS_ANTALL_PERIODER = 1200
    private const val MAKS_ANTALL_DETALJER_PER_STOPPNIVAA = 25

    fun tilDetaljertSimuleringResultat(
        respons: SokosSimulerBeregningResponse,
        utbetalingsoppdrag: Utbetalingsoppdrag,
    ): DetaljertSimuleringResultat {
        val responsSimulering = påkrevd(respons.operationResponse?.container?.responsSimulering, "responsSimulering")

        val statusKode = påkrevd(responsSimulering.statusKode, "statusKode")
        if (statusKode >= STATUSKODE_FUNKSJONELL_FEIL) {
            throw IntegrasjonException(
                Integrasjonssystem.SIMULERING,
                "Simulering mot sokos-oppdrag-proxy feilet med statusKode=$statusKode statusMelding=${responsSimulering.statusMelding}",
            )
        }

        val perioder = responsSimulering.beregning?.periodeStoppnivaa ?: return DetaljertSimuleringResultat(emptyList())
        if (kanVæreAvkuttet(perioder)) {
            logger.warn(
                "Full liste i simuleringssvar fra sokos-oppdrag-proxy, kan være avkuttet. saksnummer=${utbetalingsoppdrag.saksnummer}",
            )
        }
        val brukerId = hentOrgNrEllerFnr(utbetalingsoppdrag.aktoer)
        return DetaljertSimuleringResultat(
            perioder
                .mapNotNull { it.beregningStoppnivaa }
                .groupBy { påkrevd(it.utbetalesTilId, "utbetalesTilId") }
                .map { (utbetalesTilId, stoppnivaaer) ->
                    SimuleringMottaker(
                        mottakerNummer = utbetalesTilId,
                        simulertPostering =
                            stoppnivaaer.flatMap { stoppnivaa ->
                                stoppnivaa.stoppnivaaDetaljer.orEmpty().map { detaljer -> tilPostering(stoppnivaa, detaljer) }
                            },
                        mottakerType =
                            utledMottakerType(utbetalesTilId, harSammeAktørIdSomBruker = hentOrgNrEllerFnr(utbetalesTilId) == brukerId),
                    )
                },
        )
    }

    // Er en liste full, kan Oppdrag ha kuttet svaret.
    private fun kanVæreAvkuttet(perioder: List<PeriodeStoppnivaa>): Boolean =
        perioder.size >= MAKS_ANTALL_PERIODER ||
            perioder.any { (it.beregningStoppnivaa?.stoppnivaaDetaljer?.size ?: 0) >= MAKS_ANTALL_DETALJER_PER_STOPPNIVAA }

    private fun tilPostering(
        stoppnivaa: BeregningStoppnivaa,
        detaljer: StoppnivaaDetaljer,
    ): SimulertPostering {
        val beløp = påkrevd(detaljer.belop, "belop")
        return SimulertPostering(
            betalingType = utledBetalingType(beløp),
            erFeilkonto = stoppnivaa.feilkonto ?: false,
            beløp = beløp,
            fagOmrådeKode = FagOmrådeKode.fraKode(påkrevd(stoppnivaa.kodeFagomraade, "kodeFagomraade").trim()),
            fom = parseDato(påkrevd(detaljer.faktiskFom, "faktiskFom")),
            tom = parseDato(påkrevd(detaljer.faktiskTom, "faktiskTom")),
            forfallsdato = parseDato(påkrevd(stoppnivaa.datoForfall, "datoForfall")),
            posteringType = PosteringType.fraKode(påkrevd(detaljer.typeKlasse, "typeKlasse")),
            utenInntrekk = false,
            klassekode = detaljer.klassekode?.trim(),
            fagsakId = påkrevd(stoppnivaa.fagsystemId, "fagsystemId").trim(),
        )
    }

    private fun <T> påkrevd(
        verdi: T?,
        felt: String,
    ): T = verdi ?: throw IllegalStateException("Mangler $felt i simuleringsrespons fra sokos-oppdrag-proxy")
}

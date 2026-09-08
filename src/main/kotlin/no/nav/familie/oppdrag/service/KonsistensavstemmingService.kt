package no.nav.familie.oppdrag.service

import no.nav.familie.kontrakter.felles.oppdrag.KonsistensavstemmingRequestV2
import no.nav.familie.kontrakter.felles.oppdrag.KonsistensavstemmingUtbetalingsoppdrag
import no.nav.familie.kontrakter.felles.oppdrag.Utbetalingsoppdrag
import no.nav.familie.kontrakter.felles.oppdrag.Utbetalingsperiode
import no.nav.familie.oppdrag.avstemming.AvstemmingSender
import no.nav.familie.oppdrag.konsistensavstemming.KonsistensavstemmingMapper
import no.nav.familie.oppdrag.repository.OppdragLagerRepository
import no.nav.familie.oppdrag.repository.UtbetalingsoppdragForKonsistensavstemming
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Objects.isNull
import java.util.UUID

@Service
class KonsistensavstemmingService(
    private val avstemmingSender: AvstemmingSender,
    private val oppdragLagerRepository: OppdragLagerRepository,
    private val mellomlagringKonsistensavstemmingService: MellomlagringKonsistensavstemmingService,
) {
    @Transactional
    fun utførKonsistensavstemming(
        request: KonsistensavstemmingUtbetalingsoppdrag,
        sendStartmelding: Boolean,
        sendAvsluttmelding: Boolean,
        transaksjonsId: UUID?,
    ) {
        utførKonsistensavstemming(
            KonsistensavstemmingMetaInfo(
                fagsystem = Fagsystem.valueOf(request.fagsystem),
                transaksjonsId = transaksjonsId,
                avstemmingstidspunkt = request.avstemmingstidspunkt,
                sendStartmelding = sendStartmelding,
                sendAvsluttmelding = sendAvsluttmelding,
                utbetalingsoppdrag = request.utbetalingsoppdrag,
            ),
        )
    }

    private fun utførKonsistensavstemming(metaInfo: KonsistensavstemmingMetaInfo) {
        if (metaInfo.erFørsteBatchIEnSplittetBatch()) {
            mellomlagringKonsistensavstemmingService.sjekkAtDetteErFørsteMelding(metaInfo.transaksjonsId!!)
        }

        val konsistensavstemmingMapper = opprettKonsistensavstemmingMapper(metaInfo)

        val meldinger = konsistensavstemmingMapper.lagAvstemmingsmeldinger()

        if (meldinger.isEmpty()) {
            LOG.info("Ingen oppdrag å utføre konsistensavstemming for")
            return
        }

        LOG.info(
            "Utfører konsistensavstemming for id ${konsistensavstemmingMapper.avstemmingId} " +
                "antall meldinger er ${meldinger.size}",
        )
        meldinger.forEach {
            avstemmingSender.sendKonsistensAvstemming(it)
        }

        if (metaInfo.erSplittetBatchMenIkkeSisteBatch()) {
            mellomlagringKonsistensavstemmingService.opprettInnslagIMellomlagring(
                metaInfo,
                konsistensavstemmingMapper.antallOppdrag,
                konsistensavstemmingMapper.totalBeløp,
            )
        }
        LOG.info("Fullført konsistensavstemming for id ${konsistensavstemmingMapper.avstemmingId}")
    }

    fun hentSisteUtbetalingsoppdragForFagsaker(
        fagsystem: String,
        fagsakIder: Set<String>,
    ): List<UtbetalingsoppdragForKonsistensavstemming> =
        oppdragLagerRepository.hentSisteUtbetalingsoppdragForFagsaker(fagsystem, fagsakIder)

    @Transactional
    fun utførKonsistensavstemming(
        request: KonsistensavstemmingRequestV2,
        sendStartMelding: Boolean,
        sendAvsluttmelding: Boolean,
        transaksjonsId: UUID?,
    ) {
        sjekkAtTransaktionsIdErSattHvisSplittetBatch(sendStartMelding, sendAvsluttmelding, transaksjonsId)

        val fagsystem = request.fagsystem
        val avstemmingstidspunkt = request.avstemmingstidspunkt

        val perioderPåBehandling = request.perioderForBehandlinger.associate { it.behandlingId to it.perioder }
        verifyUnikeBehandlinger(perioderPåBehandling, request)

        val fødselsnummerPåBehandling = request.perioderForBehandlinger.associate { it.behandlingId to it.aktivFødselsnummer }
        val utbetalesTilPåBehandling = request.perioderForBehandlinger.associate { it.behandlingId to it.utebetalesTil }

        val utbetalingsoppdragForKonsistensavstemming =
            oppdragLagerRepository.hentUtbetalingsoppdragForKonsistensavstemming(fagsystem, perioderPåBehandling.keys)

        val utbetalingsoppdrag =
            leggAktuellePerioderISisteUtbetalingsoppdraget(
                utbetalingsoppdragForKonsistensavstemming,
                perioderPåBehandling,
                fødselsnummerPåBehandling,
                utbetalesTilPåBehandling,
            )

        utførKonsistensavstemming(
            KonsistensavstemmingMetaInfo(
                fagsystem = Fagsystem.valueOf(fagsystem),
                transaksjonsId = transaksjonsId,
                avstemmingstidspunkt = avstemmingstidspunkt,
                sendStartmelding = sendStartMelding,
                sendAvsluttmelding = sendAvsluttmelding,
                utbetalingsoppdrag = utbetalingsoppdrag,
            ),
        )
    }

    private fun sjekkAtTransaktionsIdErSattHvisSplittetBatch(
        sendStartMelding: Boolean,
        sendAvsluttmelding: Boolean,
        transaksjonsId: UUID?,
    ) {
        if (!(sendStartMelding && sendAvsluttmelding) && isNull(transaksjonsId)) {
            throw Exception("Er sendStartmelding eller sendAvsluttmelding satt til false må transaksjonsId være definert.")
        }
    }

    private fun opprettKonsistensavstemmingMapper(metaInfo: KonsistensavstemmingMetaInfo): KonsistensavstemmingMapper {
        val aggregertAntallOppdrag = mellomlagringKonsistensavstemmingService.hentAggregertAntallOppdrag(metaInfo)
        val aggregertTotalBeløp = mellomlagringKonsistensavstemmingService.hentAggregertBeløp(metaInfo)

        return KonsistensavstemmingMapper(
            fagsystem = metaInfo.fagsystem.name,
            utbetalingsoppdrag = metaInfo.utbetalingsoppdrag,
            avstemmingsDato = metaInfo.avstemmingstidspunkt,
            sendStartmelding = metaInfo.sendStartmelding,
            sendAvsluttmelding = metaInfo.sendAvsluttmelding,
            aggregertAntallOppdrag = aggregertAntallOppdrag,
            aggregertTotalBeløp = aggregertTotalBeløp,
            transaksjonsId = metaInfo.transaksjonsId,
        )
    }

    /**
     * Legger inn alle (filtrerte) perioder for en gitt fagsak i det siste utbetalingsoppdraget
     */
    private fun leggAktuellePerioderISisteUtbetalingsoppdraget(
        utbetalingsoppdrag: List<UtbetalingsoppdragForKonsistensavstemming>,
        perioderPåBehandling: Map<String, Set<Long>>,
        fødselsnummerPåBehandling: Map<String, String>,
        utbetalesTilPåBehandling: Map<String, String?>,
    ): List<Utbetalingsoppdrag> {
        val utbetalingsoppdragPåFagsak = utbetalingsoppdrag.groupBy { it.fagsakId }

        return utbetalingsoppdragPåFagsak.map { (saksnummer, utbetalingsoppdragListe) ->
            val senesteUtbetalingsoppdrag =
                utbetalingsoppdragListe
                    .maxByOrNull { oppdrag ->
                        oppdrag.utbetalingsoppdrag.utbetalingsperiode.maxOf { it.periodeId }
                    }?.utbetalingsoppdrag ?: error("Finner ikke seneste behandling for fagsak=$saksnummer")

            val behandlingsIderForFagsak = utbetalingsoppdragListe.map { it.behandlingId }.toSet()

            val aktuellePeriodeIderForFagsak =
                perioderPåBehandling
                    .filter { behandlingsIderForFagsak.contains(it.key) }
                    .values
                    .flatten()
                    .toSet()

            var aktivtFødselsnummer: String? = null
            val perioderTilKonsistensavstemming =
                utbetalingsoppdragListe
                    .flatMap {
                        aktivtFødselsnummer = hentFødselsnummerForBehandling(fødselsnummerPåBehandling, it.behandlingId)
                        val utbetalesTil = utbetalesTilPåBehandling[it.behandlingId] ?: aktivtFødselsnummer

                        it.utbetalingsoppdrag.utbetalingsperiode
                            .filter { utbetalingsperiode -> aktuellePeriodeIderForFagsak.contains(utbetalingsperiode.periodeId) }
                            // Setter aktivt fødselsnummer på behandling som mottok fra fagsystem
                            .map { utbetalingsperiode ->
                                utbetalingsperiode.copy(utbetalesTil = utbetalesTil ?: utbetalingsperiode.utbetalesTil)
                            }
                    }
                    // Dersom det finnes opphør i fagsaken hvor vi har opphørt ved å avkorte en eksisterende periode vil perioden forekomme minimum 2 ganger i listen.
                    // Derfor må vi filtrere bort perioder med samme periodeId og kun ta med perioden som stammer fra det seneste vedtaket.
                    .filtrerPerioderMedLikPeriodeIdEtterSenesteVedtakDato()

            senesteUtbetalingsoppdrag.let {
                it.copy(
                    utbetalingsperiode = perioderTilKonsistensavstemming,
                    // Setter aktivt fødselsnummer på behandling som mottok fra fagsystem
                    aktoer = aktivtFødselsnummer ?: it.aktoer,
                )
            }
        }
    }

    private fun List<Utbetalingsperiode>.filtrerPerioderMedLikPeriodeIdEtterSenesteVedtakDato(): List<Utbetalingsperiode> =
        this
            .groupBy { utbetalingsperiode -> utbetalingsperiode.periodeId }
            .mapValues { (_, utbetalingsperioder) ->
                utbetalingsperioder.maxBy {
                    it.datoForVedtak
                }
            }.values
            .toList()

    private fun verifyUnikeBehandlinger(
        periodeIderPåBehandling: Map<String, Set<Long>>,
        request: KonsistensavstemmingRequestV2,
    ) {
        if (periodeIderPåBehandling.size != request.perioderForBehandlinger.size) {
            val duplikateBehandlinger =
                request.perioderForBehandlinger
                    .map { it.behandlingId }
                    .groupingBy { it }
                    .eachCount()
                    .filter { it.value > 1 }
            error("Behandling finnes flere ganger i requesten: ${duplikateBehandlinger.keys}")
        }
    }

    private fun hentFødselsnummerForBehandling(
        fødselsnummerPåBehandling: Map<String, String>,
        behandlingId: String,
    ): String =
        fødselsnummerPåBehandling[behandlingId]
            ?: error("Finnes ikke et aktivt fødselsnummer for behandlingId $behandlingId")

    companion object {
        val LOG: Logger = LoggerFactory.getLogger(KonsistensavstemmingService::class.java)
    }
}

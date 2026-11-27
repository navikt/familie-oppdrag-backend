package no.nav.familie.oppdrag.service

import no.nav.familie.kontrakter.felles.oppdrag.Utbetalingsoppdrag
import no.nav.familie.oppdrag.repository.MellomlagringKonsistensavstemming
import no.nav.familie.oppdrag.repository.MellomlagringKonsistensavstemmingRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID

@Service
class MellomlagringKonsistensavstemmingService(
    private val mellomlagringKonsistensavstemmingRepository: MellomlagringKonsistensavstemmingRepository,
) {
    fun hentAggregertBeløp(metaInfo: KonsistensavstemmingMetaInfo): Long =
        if (metaInfo.erSisteBatchIEnSplittetBatch()) {
            mellomlagringKonsistensavstemmingRepository.hentAggregertTotalBeløp(metaInfo.transaksjonsId!!)
        } else {
            0L
        }

    fun hentAggregertAntallOppdrag(metaInfo: KonsistensavstemmingMetaInfo): Int =
        if (metaInfo.erSisteBatchIEnSplittetBatch()) {
            mellomlagringKonsistensavstemmingRepository.hentAggregertAntallOppdrag(metaInfo.transaksjonsId!!)
        } else {
            0
        }

    fun opprettInnslagIMellomlagring(
        metaInfo: KonsistensavstemmingMetaInfo,
        antalOppdrag: Int,
        totalBeløp: Long,
    ) {
        val mellomlagring =
            MellomlagringKonsistensavstemming(
                fagsystem = metaInfo.fagsystem,
                transaksjonsId = metaInfo.transaksjonsId!!,
                antallOppdrag = antalOppdrag,
                totalBeløp = totalBeløp,
            )
        mellomlagringKonsistensavstemmingRepository.insert(mellomlagring)
        LOG.info("Opprettet mellomlagring for transaksjonsId ${metaInfo.transaksjonsId}")
    }

    fun sjekkAtDetteErFørsteMelding(transaksjonsId: UUID) {
        if (mellomlagringKonsistensavstemmingRepository.findAllByTransaksjonsId(transaksjonsId).isNotEmpty()) {
            throw Exception("Skal sende startmelding men det er ikke første mottatte batch med transaskjsonId= $transaksjonsId")
        }
    }

    companion object {
        val LOG: Logger = LoggerFactory.getLogger(MellomlagringKonsistensavstemmingService::class.java)
    }
}

data class KonsistensavstemmingMetaInfo(
    val fagsystem: Fagsystem,
    val transaksjonsId: UUID?,
    val avstemmingstidspunkt: LocalDateTime,
    val sendStartmelding: Boolean,
    val sendAvsluttmelding: Boolean,
    val utbetalingsoppdrag: List<Utbetalingsoppdrag>,
) {
    fun erFørsteBatchIEnSplittetBatch(): Boolean = sendStartmelding && !sendAvsluttmelding

    fun erSisteBatchIEnSplittetBatch(): Boolean = !sendStartmelding && sendAvsluttmelding

    fun erSplittetBatchMenIkkeSisteBatch(): Boolean = erSplittetBatch() && !erSisteBatchIEnSplittetBatch()

    fun erSplittetBatch(): Boolean = !sendStartmelding || !sendAvsluttmelding
}

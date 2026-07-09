package no.nav.familie.oppdrag.tss

import no.nav.familie.kontrakter.ba.tss.SamhandlerAdresse
import no.nav.familie.kontrakter.ba.tss.SamhandlerInfo
import no.nav.familie.kontrakter.ba.tss.SøkSamhandlerInfo
import no.nav.familie.oppdrag.tss.TssSamhandlerIdentType.ORGNR
import no.rtv.namespacetss.SamhAvdPraType
import no.rtv.namespacetss.Samhandler
import no.rtv.namespacetss.TssSamhandlerData
import no.rtv.namespacetss.TypeKomp940
import no.rtv.namespacetss.TypeOD910
import no.rtv.namespacetss.TypeSamhAdr
import no.rtv.namespacetss.TypeSamhAvd
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Profile("dev")
@Service
class TssOppslagService(
    private val tssMQClient: TssMQClient,
) {
    private val logger = LoggerFactory.getLogger(TssOppslagService::class.java)

    fun hentSamhandlerDataForOrganisasjonB910(tssSamhandlerIdent: TssSamhandlerIdent): TypeOD910 {
        val samhandlerData = tssMQClient.getOrgInfo(tssSamhandlerIdent)
        validateB910response(tssSamhandlerIdent, samhandlerData)
        return samhandlerData.tssOutputData.samhandlerODataB910
    }

    fun hentSamhandlerInformasjon(tssSamhandlerIdent: TssSamhandlerIdent): SamhandlerInfo {
        val samhandlerData910 = hentSamhandlerDataForOrganisasjonB910(tssSamhandlerIdent)

        if (samhandlerData910.antallSamhandlere.toInt() > 1) {
            logger.warn("Fant flere samhandlere for $tssSamhandlerIdent")
        }
        val enkeltSamhandler = samhandlerData910.enkeltSamhandler.first()
        val orgNr = if (tssSamhandlerIdent.type == ORGNR) tssSamhandlerIdent.ident else null
        return mapSamhandler(enkeltSamhandler, orgNr)
    }

    fun hentInformasjonOmSamhandlerInstB940(
        navn: String?,
        postNummer: String?,
        område: String?,
        side: Int,
    ): TssSamhandlerData = tssMQClient.søkOrgInfo(navn, postNummer, område, side)

    fun hentInformasjonOmSamhandlerInst(
        navn: String?,
        postNummer: String?,
        område: String?,
        side: Int,
    ): SøkSamhandlerInfo {
        val samhandlerData = hentInformasjonOmSamhandlerInstB940(navn, postNummer, område, side)
        val finnesMerInfo = validateB940response("$navn $postNummer $område", samhandlerData)
        val samhandlerODataB940 = samhandlerData.tssOutputData.samhandlerODataB940
        val samhandlere =
            samhandlerODataB940.enkeltSamhandler
                .filter { enkeltSamhandler -> enkeltSamhandler.samhandlerAvd125.samhAvd.any { erInfotrygdTssAvdeling(it) } }
                .map { mapSamhandler(it) }
        return SøkSamhandlerInfo(finnesMerInfo, samhandlere)
    }

    private fun validateB910response(
        inputData: TssSamhandlerIdent,
        tssResponse: TssSamhandlerData,
    ) {
        commonResponseValidation(tssResponse)
        val svarStatus = tssResponse.tssOutputData.svarStatus

        if (svarStatus.alvorligGrad != TSS_STATUS_OK) {
            if (svarStatus.kodeMelding == TSS_KODEMELDING_INGEN_FUNNET) {
                throw TssNoDataFoundException("Ingen treff med inputData=$inputData")
            }
            throw TssResponseException(svarStatus.beskrMelding, svarStatus.alvorligGrad, svarStatus.kodeMelding)
        }
        if (tssResponse.tssOutputData.ingenReturData != null) {
            throw TssNoDataFoundException("Ingen returdata for TSS request med inputData=$inputData")
        }
    }

    private fun validateB940response(
        inputData: String?,
        tssResponse: TssSamhandlerData,
    ): Boolean {
        commonResponseValidation(tssResponse)
        val svarStatus = tssResponse.tssOutputData.svarStatus
        if (svarStatus.alvorligGrad != TSS_STATUS_OK) {
            if (svarStatus.kodeMelding == TSS_KODEMELDING_INGEN_FUNNET) {
                throw TssNoDataFoundException("Ingen treff med med inputData=$inputData")
            }
            if (svarStatus.kodeMelding == TSS_KODEMELDING_MER_INFO) return true
            if (svarStatus.kodeMelding == TSS_KODEMELDING_INGEN_FLERE_FOREKOMSTER) return false
            throw TssResponseException(svarStatus.beskrMelding, svarStatus.alvorligGrad, svarStatus.kodeMelding)
        }
        if (tssResponse.tssOutputData.ingenReturData != null) {
            throw TssNoDataFoundException("Ingen returdata for TSS request med inputData=$inputData")
        }
        return false
    }

    private fun commonResponseValidation(tssResponse: TssSamhandlerData) {
        if (erTomResponse(tssResponse)) {
            throw TssConnectionException("Ingen response. Mest sannsynlig timeout mot TSS")
        }
    }

    private fun erTomResponse(tssResponse: TssSamhandlerData) =
        tssResponse.tssOutputData == null ||
            tssResponse.tssOutputData.svarStatus == null ||
            tssResponse.tssOutputData.svarStatus.alvorligGrad == null

    private fun mapSamhandler(
        enkeltSamhandler: Samhandler,
        orgnr: String?,
    ): SamhandlerInfo {
        val navn =
            enkeltSamhandler.samhandler110.samhandler
                .first()
                .navnSamh
        val orgnr =
            orgnr ?: enkeltSamhandler.alternativId111.samhId
                .filter { it.kodeAltIdentType == "ORG" && it.datoIdentTom.isBlank() }
                .firstOrNull()
                ?.idAlternativ
        val (tssId, avdNr) = mapTssEksternIdOgAvdNr(enkeltSamhandler.samhandlerAvd125)

        val avdelingsAdresser = madAdresse(enkeltSamhandler.adresse130, avdNr)
        return SamhandlerInfo(tssEksternId = tssId, navn = navn, adresser = avdelingsAdresser, orgNummer = orgnr?.trimStart('0'))
    }

    private fun mapSamhandler(enkeltSamhandler: TypeKomp940): SamhandlerInfo {
        val navn =
            enkeltSamhandler.samhandler110.samhandler
                .first()
                .navnSamh
        val (tssId, avdNr) = mapTssEksternIdOgAvdNr(enkeltSamhandler.samhandlerAvd125)

        val avdelingsAdresser = madAdresse(enkeltSamhandler.adresse130, avdNr)
        return SamhandlerInfo(tssId, navn, avdelingsAdresser)
    }

    private fun mapTssEksternIdOgAvdNr(samhandlerAvd125: TypeSamhAvd): Pair<String, String> {
        val tssId = samhandlerAvd125.samhAvd.first { erInfotrygdTssAvdeling(it) }.idOffTSS
        val avdNr = samhandlerAvd125.samhAvd.first { erInfotrygdTssAvdeling(it) }.avdNr
        return Pair(tssId, avdNr)
    }

    /**
     Info fra Tore på Infotrygd:
     Når vi søker mot TSS (vedrørende BA…) har vi avdNr = 01 som et søkekriterie, og får bare den forekomsten tilbake.
     */
    private fun erInfotrygdTssAvdeling(it: SamhAvdPraType) = it.avdNr == TSS_INFOTRYGD_BA_AVDELING

    private fun madAdresse(
        adresse130: TypeSamhAdr,
        avdNr: String?,
    ) = adresse130.adresseSamh
        .filter {
            it.avdNr == avdNr && it.gyldigAdresse == "J"
        }.mapNotNull {
            val adresseLinje = if (it.antAdrLinje.isNullOrBlank()) emptyList() else it.adrLinjeInfo.adresseLinje
            SamhandlerAdresse(adresseLinje, it.postNr, it.poststed, it.beskrAdresseType)
        }

    companion object {
        const val TSS_KODEMELDING_INGEN_FUNNET = "B9XX008F"
        const val TSS_KODEMELDING_MER_INFO = "B9XX018I"
        const val TSS_KODEMELDING_INGEN_FLERE_FOREKOMSTER = "B9XX021I"
        const val TSS_STATUS_OK = "00"
        const val TSS_INFOTRYGD_BA_AVDELING = "01"
    }
}

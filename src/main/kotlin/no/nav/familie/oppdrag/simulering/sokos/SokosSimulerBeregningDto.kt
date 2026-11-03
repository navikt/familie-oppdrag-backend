package no.nav.familie.oppdrag.simulering.sokos

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

@JsonInclude(JsonInclude.Include.NON_NULL)
data class SokosSimulerBeregningRequest(
    @JsonProperty("OsSimulerBeregningOperation")
    val operation: Operation,
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class Operation(
        @JsonProperty("ZS1ICont")
        val container: Container,
    )

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class Container(
        val requestSimulering: RequestSimulering,
    )

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class RequestSimulering(
        val simuleringsPeriode: SimuleringsPeriode? = null,
        val oppdrag: Oppdrag,
        val bilagstype: List<Bilagstype>? = null,
        val avstemmingsnokkel: List<Avstemmingsnokkel>? = null,
        val omposter: List<Omposter>? = null,
        val avventPeriode: List<AvventPeriode>? = null,
        val enhetOppdrag: List<Enhet>? = null,
        val tekstOppdrag: List<Tekst>? = null,
        val oppdragslinje: List<Oppdragslinje> = emptyList(),
    )

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class SimuleringsPeriode(
        val datoSimulerFom: String? = null,
        val datoSimulerTom: String? = null,
    )

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class Oppdrag(
        val kodeEndring: String? = null,
        val kodeStatus: String? = null,
        val datoStatusFom: String? = null,
        val kodeFagomraade: String? = null,
        val fagsystemId: String? = null,
        val oppdragsId: Long? = null,
        val utbetFrekvens: String? = null,
        val datoForfall: String? = null,
        val stonadId: String? = null,
        val oppdragGjelderId: String? = null,
        val datoOppdragGjelderFom: String? = null,
        val saksbehId: String? = null,
    )

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class Bilagstype(
        val typeBilag: String? = null,
    )

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class Avstemmingsnokkel(
        val kodeKomponent: String? = null,
        val nokkelAvstemming: String? = null,
        val tidspktMelding: String? = null,
    )

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class Omposter(
        val ompostering: String? = null,
        val datoOmposterFom: String? = null,
        val feilreg: Boolean? = null,
    )

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class AvventPeriode(
        val datoAvventFom: String? = null,
        val datoAvventTom: String? = null,
        val kodeAarsakAvvent: String? = null,
        val datoOverfores: String? = null,
        val feilreg: Boolean? = null,
    )

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class Enhet(
        val typeEnhet: String? = null,
        val enhet: String? = null,
        val datoEnhetFom: String? = null,
    )

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class Tekst(
        val tekstLnr: Int? = null,
        val tekstKode: String? = null,
        val tekst: String? = null,
        val datoTekstFom: String? = null,
        val datoTekstTom: String? = null,
        val feilreg: Boolean? = null,
    )

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class Oppdragslinje(
        val kodeEndringLinje: String? = null,
        val kodeStatusLinje: String? = null,
        val datoStatusFom: String? = null,
        val vedtakId: String? = null,
        val delytelseId: String? = null,
        val linjeId: Int? = null,
        val kodeKlassifik: String? = null,
        val datoKlassifikFom: String? = null,
        val datoVedtakFom: String? = null,
        val datoVedtakTom: String? = null,
        val sats: BigDecimal? = null,
        val typeSats: String? = null,
        val skyldnerId: String? = null,
        val datoSkyldnerFom: String? = null,
        val kravhaverId: String? = null,
        val datoKravhaverFom: String? = null,
        val kid: String? = null,
        val datoKidFom: String? = null,
        val brukKjoreplan: Boolean? = null,
        val saksbehId: String? = null,
        val utbetalesTilId: String? = null,
        val datoUtbetalesTilIdFom: String? = null,
        val kodeArbeidsgiver: String? = null,
        val henvisning: String? = null,
        val typeSoknad: String? = null,
        val refFagsystemId: String? = null,
        val refOppdragsId: Long? = null,
        val refDelytelseId: String? = null,
        val refLinjeId: Int? = null,
        val refusjonsInfo: List<RefusjonsInfo>? = null,
        val satsVedtak: List<SatsVedtak>? = null,
        val tekstOppdragslinje: List<Tekst>? = null,
        val enhetOppdragslinje: List<Enhet>? = null,
        val gradOppdragslinje: List<Grad>? = null,
        val attestant: List<Attestant>? = null,
        // Stavemåten "Oppdags" følger Swagger-kontrakten.
        val valutaOppdagslinje: List<Valuta>? = null,
    )

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class RefusjonsInfo(
        val refunderesId: String? = null,
        val maksDato: String? = null,
        val datoFom: String? = null,
    )

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class SatsVedtak(
        val vedtakssats: BigDecimal? = null,
    )

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class Grad(
        val typeGrad: String? = null,
        val grad: Int? = null,
    )

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class Attestant(
        val attestantId: String? = null,
        val datoUgyldigFom: String? = null,
    )

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class Valuta(
        val typeValuta: String? = null,
        val valuta: String? = null,
        val datoValutaFom: String? = null,
        val feilreg: Boolean? = null,
    )
}

data class SokosSimulerBeregningResponse(
    @JsonProperty("OsSimulerBeregningOperationResponse")
    val operationResponse: OperationResponse? = null,
) {
    data class OperationResponse(
        @JsonProperty("ZS1OCont")
        val container: Container? = null,
    )

    data class Container(
        val responsSimulering: ResponsSimulering? = null,
    )

    data class ResponsSimulering(
        val statusKode: Int? = null,
        val statusMelding: String? = null,
        val beregning: Beregning? = null,
    )

    data class Beregning(
        val gjelderId: String? = null,
        val gjelderNavn: String? = null,
        val datoBeregnet: String? = null,
        val kodeFaggruppe: String? = null,
        val belopTotalt: BigDecimal? = null,
        val periodeStoppnivaa: List<PeriodeStoppnivaa>? = null,
    )

    data class PeriodeStoppnivaa(
        val beregningsPeriode: BeregningsPeriode? = null,
        val beregningStoppnivaa: BeregningStoppnivaa? = null,
    )

    data class BeregningsPeriode(
        val periodeFom: String? = null,
        val periodeTom: String? = null,
    )

    data class BeregningStoppnivaa(
        val kodeFagomraade: String? = null,
        val stoppNivaaId: Int? = null,
        val behandlendeEnhet: String? = null,
        val oppdragsId: Long? = null,
        val fagsystemId: String? = null,
        val kid: String? = null,
        val utbetalesTilId: String? = null,
        val utbetalesTilNavn: String? = null,
        val typeBilag: String? = null,
        val datoForfall: String? = null,
        val feilkonto: Boolean? = null,
        val stoppnivaaDetaljer: List<StoppnivaaDetaljer>? = null,
    )

    data class StoppnivaaDetaljer(
        val faktiskFom: String? = null,
        val faktiskTom: String? = null,
        val kontoStreng: String? = null,
        val behandlingskode: String? = null,
        val belop: BigDecimal? = null,
        val trekkVedtakId: Long? = null,
        val stonadId: String? = null,
        val korrigering: String? = null,
        val tilbakeforing: String? = null,
        val linjeId: Int? = null,
        val sats: BigDecimal? = null,
        val typeSats: String? = null,
        val antallSats: BigDecimal? = null,
        val saksbehId: String? = null,
        val uforeGrad: Int? = null,
        val delytelseId: String? = null,
        val bostedsenhet: String? = null,
        val skyldnerId: String? = null,
        val klassekode: String? = null,
        val klasseKodeBeskrivelse: String? = null,
        val typeKlasse: String? = null,
        val typeKlasseBeskrivelse: String? = null,
        val refunderesOrgNr: String? = null,
    )
}

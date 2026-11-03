package no.nav.familie.oppdrag.simulering.sokos

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

// Bare feltene vi bruker fra Swagger-kontrakten til sokos-oppdrag-proxy. Proxyen fyller ut resten av requesten.
data class SokosSimulerBeregningRequest(
    @JsonProperty("OsSimulerBeregningOperation") val operation: Operation,
) {
    data class Operation(
        @JsonProperty("ZS1ICont") val container: Container,
    )

    data class Container(
        val requestSimulering: RequestSimulering,
    )

    data class RequestSimulering(
        val oppdrag: Oppdrag,
        val enhetOppdrag: List<Enhet>,
        val oppdragslinje: List<Oppdragslinje>,
    )

    data class Oppdrag(
        val kodeEndring: String,
        val kodeFagomraade: String,
        val fagsystemId: String,
        val utbetFrekvens: String,
        val oppdragGjelderId: String,
        val datoOppdragGjelderFom: String,
        val saksbehId: String,
    )

    data class Enhet(
        val typeEnhet: String,
        val enhet: String,
        val datoEnhetFom: String,
    )

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class Oppdragslinje(
        val kodeEndringLinje: String,
        val kodeStatusLinje: String?,
        val datoStatusFom: String?,
        val vedtakId: String,
        val delytelseId: String,
        val kodeKlassifik: String,
        val datoVedtakFom: String,
        val datoVedtakTom: String,
        val sats: BigDecimal,
        val typeSats: String,
        val brukKjoreplan: Boolean,
        val saksbehId: String,
        val utbetalesTilId: String,
        val henvisning: String,
        val refFagsystemId: String?,
        val refDelytelseId: String?,
        val attestant: List<Attestant>,
        val gradOppdragslinje: List<Grad>?,
    )

    data class Attestant(
        val attestantId: String,
    )

    data class Grad(
        val typeGrad: String,
        val grad: Int,
    )
}

// Alt er nullable fordi proxyen sender null for felt og lister som mangler. Ukjente felt ignoreres.
data class SokosSimulerBeregningResponse(
    @JsonProperty("OsSimulerBeregningOperationResponse") val operationResponse: OperationResponse? = null,
) {
    data class OperationResponse(
        @JsonProperty("ZS1OCont") val container: Container? = null,
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
        val periodeStoppnivaa: List<PeriodeStoppnivaa>? = null,
    )

    data class PeriodeStoppnivaa(
        val beregningStoppnivaa: BeregningStoppnivaa? = null,
    )

    data class BeregningStoppnivaa(
        val kodeFagomraade: String? = null,
        val fagsystemId: String? = null,
        val utbetalesTilId: String? = null,
        val datoForfall: String? = null,
        val feilkonto: Boolean? = null,
        val stoppnivaaDetaljer: List<StoppnivaaDetaljer>? = null,
    )

    data class StoppnivaaDetaljer(
        val faktiskFom: String? = null,
        val faktiskTom: String? = null,
        val belop: BigDecimal? = null,
        val klassekode: String? = null,
        val typeKlasse: String? = null,
    )
}

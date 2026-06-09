package no.nav.familie.oppdrag.tss

import org.springframework.context.annotation.Profile
import io.swagger.v3.oas.annotations.Operation
import no.nav.familie.kontrakter.ba.tss.SamhandlerInfo
import no.nav.familie.kontrakter.ba.tss.SøkSamhandlerInfo
import no.nav.familie.kontrakter.ba.tss.SøkSamhandlerInfoRequest
import no.nav.familie.kontrakter.felles.Ressurs
import no.rtv.namespacetss.TOutputElementer
import no.rtv.namespacetss.TypeOD910
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Profile("never")
@RestController
@RequestMapping("/api/tss")
class TssController(
    private val tssOppslagService: TssOppslagService,
) {
    @Operation(summary = "Henter informasjon om samhandler ved bruk av ORGNR og TSS-tjensten B910")
    @PostMapping(path = ["/proxy/b910"])
    fun hentSamhandlerDataForOrganisasjonProxy(
        @RequestBody tssSamhandlerIdent: TssSamhandlerIdent,
    ): Ressurs<TypeOD910> = Ressurs.success(tssOppslagService.hentSamhandlerDataForOrganisasjonB910(tssSamhandlerIdent))

    @Operation(
        summary =
            "Søk informasjon samhandlere av type INST ved bruk av navn og TSS-tjensten B940 og TSS-tjensten B940. " +
                "Returnerer TSS-output data i rå format",
    )
    @PostMapping(path = ["/proxy/b940"])
    fun søkSamhnadlerinfoFraNavnProxy(
        @RequestBody request: SøkSamhandlerInfoRequest,
    ): Ressurs<TOutputElementer> =
        Ressurs.success(
            tssOppslagService.hentInformasjonOmSamhandlerInstB940(request.navn, request.postNr, request.område, request.side).tssOutputData,
        )

    @Operation(summary = "Henter informasjon om samhandler ved bruk av ORGNR og TSS-tjensten B910")
    @GetMapping(path = ["/orgnr/{orgnr}"])
    fun hentSamhandlerDataForOrganisasjon(
        @PathVariable("orgnr") orgnr: String,
    ): Ressurs<SamhandlerInfo> =
        Ressurs.success(
            tssOppslagService.hentSamhandlerInformasjon(TssSamhandlerIdent(ident = orgnr, type = TssSamhandlerIdentType.ORGNR)),
        )

    @Operation(summary = "Henter informasjon om samhandler ved bruk av ekstern id fra TSS og TSS-tjensten B910")
    @GetMapping(path = ["/tssnr/{tssnr}"])
    fun hentSamhandlerDataForTssEksternId(
        @PathVariable("tssnr") tssnr: String,
    ): Ressurs<SamhandlerInfo> =
        Ressurs.success(
            tssOppslagService.hentSamhandlerInformasjon(TssSamhandlerIdent(ident = tssnr, type = TssSamhandlerIdentType.TSS)),
        )

    @Operation(summary = "Søk samhandlere ved bruk av navn og TSS-tjensten B940. Første side er 0")
    @PostMapping(path = ["/navn"])
    fun søkSamhnadlerinfoFraNavn(
        @RequestBody request: SøkSamhandlerInfoRequest,
    ): Ressurs<SøkSamhandlerInfo> =
        Ressurs.success(
            tssOppslagService.hentInformasjonOmSamhandlerInst(request.navn, request.postNr, request.område, request.side),
        )
}

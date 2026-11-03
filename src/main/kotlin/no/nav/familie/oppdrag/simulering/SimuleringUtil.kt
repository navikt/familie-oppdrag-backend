package no.nav.familie.oppdrag.simulering

import no.nav.familie.kontrakter.felles.simulering.BetalingType
import no.nav.familie.kontrakter.felles.simulering.MottakerType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object SimuleringUtil {
    private val DATO_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun hentOrgNrEllerFnr(orgNrEllerFnr: String): String =
        if (erOrgNr(orgNrEllerFnr)) {
            orgNrEllerFnr.substring(2)
        } else {
            orgNrEllerFnr
        }

    fun utledMottakerType(
        utbetalesTilId: String,
        harSammeAktørIdSomBruker: Boolean,
    ): MottakerType {
        if (harSammeAktørIdSomBruker) {
            return MottakerType.BRUKER
        }
        return if (erOrgNr(utbetalesTilId)) {
            MottakerType.ARBG_ORG
        } else {
            MottakerType.ARBG_PRIV
        }
    }

    fun utledBetalingType(belop: BigDecimal): BetalingType =
        if (belop > BigDecimal.ZERO) {
            BetalingType.DEBIT
        } else {
            BetalingType.KREDIT
        }

    fun parseDato(dato: String): LocalDate = LocalDate.parse(dato, DATO_FORMAT)

    private fun erOrgNr(verdi: String): Boolean {
        if (verdi.isBlank()) {
            throw IllegalArgumentException("org.nr verdi er tom")
        }
        // orgNr i responsen fra økonomi starter med "00"
        return "00" == verdi.substring(0, 2)
    }
}

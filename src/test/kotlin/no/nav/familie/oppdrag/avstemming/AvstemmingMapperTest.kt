package no.nav.familie.oppdrag.avstemming

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class AvstemmingMapperTest {
    @ParameterizedTest
    @CsvSource(
        "EFOG, EF",
        "EFBT, EF",
        "EFSP, EF",
        "BA, BA",
        "KS, KS",
        "IT28, GHGSORMG",
    )
    fun `skal mappe fagområde til avleverende komponentkode`(
        fagområde: String,
        forventetKomponentkode: String,
    ) {
        assertEquals(forventetKomponentkode, AvstemmingMapper.fagområdeTilAvleverendeKomponentKode(fagområde))
    }

    @Test
    fun `skal kaste feil for ukjent fagområde`() {
        assertThrows<Error> { AvstemmingMapper.fagområdeTilAvleverendeKomponentKode("UKJENT") }
    }
}

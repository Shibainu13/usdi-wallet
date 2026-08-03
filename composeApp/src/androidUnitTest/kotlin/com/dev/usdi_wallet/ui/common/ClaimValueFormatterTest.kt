package com.dev.usdi_wallet.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClaimValueFormatterTest {
    @Test
    fun claimNamesHideTypeSuffixesAndUseDisplayText() {
        assertEquals("Age", formatClaimName("age_num"))
        assertEquals("Full name", formatClaimName("full_name_str"))
        assertEquals("Expiry", formatClaimName("expiry_date"))
        assertEquals("Birth date", formatClaimName("birth_date_date"))
    }

    @Test
    fun dateClaimDisplayValuesUseIsoDateFormat() {
        assertEquals("2026-08-02", formatClaimDisplayValue("expiry_date", "20260802"))
        assertEquals("2026-08-02", formatClaimDisplayValue("expiry_date", 20260802))
        assertEquals("20260802", formatClaimDisplayValue("expiry_str", "20260802"))
        assertEquals("20261302", formatClaimDisplayValue("expiry_date", "20261302"))
    }

    @Test
    fun numericPredicateInputOnlyAcceptsIntegers() {
        assertTrue(isClaimInputCandidate("123", ClaimInputFormat.INTEGER))
        assertTrue(isClaimInputCandidate("-123", ClaimInputFormat.INTEGER))
        assertFalse(isClaimInputCandidate("12a", ClaimInputFormat.INTEGER))
        assertTrue(isFinalClaimInputValid("123", ClaimInputFormat.INTEGER))
        assertFalse(isFinalClaimInputValid("-", ClaimInputFormat.INTEGER))
    }

    @Test
    fun datePredicateInputRequiresIsoDateFormat() {
        assertTrue(isClaimInputCandidate("2026-08-02", ClaimInputFormat.DATE))
        assertFalse(isClaimInputCandidate("20260802", ClaimInputFormat.DATE))
        assertFalse(isClaimInputCandidate("2026--", ClaimInputFormat.DATE))
        assertTrue(isFinalClaimInputValid("2024-02-29", ClaimInputFormat.DATE))
        assertFalse(isFinalClaimInputValid("2023-02-29", ClaimInputFormat.DATE))
        assertFalse(isFinalClaimInputValid("2024-13-01", ClaimInputFormat.DATE))
    }

    @Test
    fun datePredicateWireValueRemovesDashes() {
        assertEquals("20260802", claimPredicateWireValue("expiry_date", "2026-08-02"))
    }
}

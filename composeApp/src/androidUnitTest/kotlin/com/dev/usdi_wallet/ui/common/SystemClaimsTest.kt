package com.dev.usdi_wallet.ui.common

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SystemClaimsTest {
    @Test
    fun revocationSystemClaimsAreHiddenCaseInsensitively() {
        assertTrue(isSystemIndexClaim("index"))
        assertTrue(isSystemIndexClaim(" Index "))
        assertTrue(isSystemIndexClaim("index_str"))
        assertTrue(isSystemIndexClaim("INDEX_NUM"))
        assertTrue(isSystemIndexClaim("credentialID"))
        assertTrue(isSystemIndexClaim("credential_id"))
        assertTrue(isSystemIndexClaim("credential-id_str"))
        assertFalse(isUserVisibleClaim("INDEX"))
        assertFalse(isUserVisibleClaim("credentialID"))
    }

    @Test
    fun nonIndexClaimsRemainVisible() {
        assertFalse(isSystemIndexClaim("student_id"))
        assertTrue(isUserVisibleClaim("student_id"))
    }
}

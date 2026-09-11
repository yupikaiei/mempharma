package com.mempharma.app.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-logic tests for the alert-tone mapping. The DataStore itself and the
 * actual playback need an Android runtime, so only the stateless
 * [parseAlertTone] helper is exercised here.
 */
class AlertToneTest {

    @Test
    fun blankOrMissing_isSystemDefault() {
        assertEquals(AlertTone.SystemDefault, parseAlertTone(""))
        assertEquals(AlertTone.SystemDefault, parseAlertTone("   "))
        assertEquals(AlertTone.SystemDefault, parseAlertTone(null))
    }

    @Test
    fun silentSentinel_isSilent() {
        assertEquals(AlertTone.Silent, parseAlertTone(ALERT_SILENT))
    }

    @Test
    fun uri_isCustom() {
        val uri = "content://settings/system/alarm_alert"
        assertEquals(AlertTone.Custom(uri), parseAlertTone(uri))
    }
}

package com.mempharma.app.data.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around the platform SMS APIs.
 *
 * Kept deliberately small and free of any decision logic so that all the
 * "when/what" rules live in the unit-tested
 * [com.mempharma.app.domain.SmsTrigger] instead.
 */
@Singleton
class SmsSender @Inject constructor() {

    /** Whether MemPharma is currently allowed to send texts. */
    fun canSend(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Send [message] to [number], splitting it into several parts when it is
     * longer than one SMS. Returns false when the message could not be handed
     * to the radio (no permission, bad number, no telephony service).
     */
    fun send(context: Context, number: String, message: String): Boolean {
        if (number.isBlank() || message.isBlank()) return false
        if (!canSend(context)) return false
        return runCatching {
            val manager = context.getSystemService(SmsManager::class.java) ?: return false
            val parts = manager.divideMessage(message)
            if (parts.size > 1) {
                manager.sendMultipartTextMessage(number, null, parts, null, null)
            } else {
                manager.sendTextMessage(number, null, message, null, null)
            }
            true
        }.getOrDefault(false)
    }
}

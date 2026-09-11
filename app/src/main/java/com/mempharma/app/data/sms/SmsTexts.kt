package com.mempharma.app.data.sms

import android.content.Context
import com.mempharma.app.R
import com.mempharma.app.data.settings.withAppLanguage
import com.mempharma.app.domain.SmsTemplates

/**
 * Builds the refill-text wording from the app's string resources, so the pure
 * rules in [com.mempharma.app.domain.SmsTrigger] never hardcode a language.
 */
object SmsTexts {

    fun templates(appContext: Context): SmsTemplates {
        // The message goes out in the language chosen in Settings.
        val context = appContext.withAppLanguage()
        return SmsTemplates(
            out = context.getString(R.string.sms_message_out),
            low = context.getString(R.string.sms_message_low),
            test = context.getString(R.string.sms_test_message),
            testNoun = context.getString(R.string.sms_test_noun),
            possessive = context.getString(R.string.sms_possessive),
            possessiveEndingS = context.getString(R.string.sms_possessive_ending_s)
        )
    }
}

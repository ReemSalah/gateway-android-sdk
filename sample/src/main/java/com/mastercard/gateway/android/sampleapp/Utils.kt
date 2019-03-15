package com.mastercard.gateway.android.sampleapp

import android.util.Log
import androidx.appcompat.widget.AppCompatEditText
import com.mastercard.gateway.android.sdk.Gateway
import java.net.HttpURLConnection

fun AppCompatEditText.isNotBlank(): Boolean {
    return this.text!!.isNotBlank()
}

internal class Logger {

    fun logRequest(c: HttpURLConnection, data: String?) {
        var log = "REQUEST: ${c.requestMethod} ${c.url}"

        if (data != null) {
            log += "\n-- Data: $data"
        }

        // log request headers
        c.requestProperties.keys.forEach { key ->
            c.requestProperties[key]!!.forEach { value ->
                log += "\n-- $key: $value"
            }
        }

        logMultiline(log)
    }

    fun logResponse(c: HttpURLConnection, data: String?) {
        var log = "RESPONSE: "

        // log response headers
        var i = 0
        c.headerFields.keys.forEach { key ->
            c.headerFields[key]!!.forEach { value ->
                if (i == 0 && key == null) {
                    log += value

                    if (data != null && data.isNotEmpty()) {
                        log += "\n-- Data: $data"
                    }
                } else {
                    log += "\n-- " + (if (key == null) "" else "$key: ") + value
                }
                i++
            }
        }

        logMultiline(log)
    }

    private fun logMultiline(message: String) {
        message.split("\n".toRegex()).forEach(this::logDebug)
    }

    fun logDebug(message: String) {
        if (message.isNotEmpty()) {
            Log.d(Gateway::class.java.simpleName, message)
        }
    }
}
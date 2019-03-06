package com.mastercard.gateway.threeds2

import android.content.Context

interface ThreeDS2Service {

    fun initialize(applicationContext: Context, configParameters: ConfigParameters, locale: String?, uiCustomization: UiCustomization?)

    fun createTransaction(directoryServerID: String, messageVersion: String?): Transaction

    fun cleanup()

    fun getSDKVersion(): String

    fun getWarnings(): List<Warning>
}
package com.mastercard.gateway.threeds2

import android.content.Context

class Mock3DS2Service(val callback: Callback) : ThreeDS2Service {

    companion object {
        const val SDK_REF_NUMBER = "1234567890"
    }

    interface Callback {
        fun initializeComplete()
        fun initializeError()
    }

    var initialized: Boolean = false
        private set

    var deviceData: String? = null
        private set

    internal val warnings = ArrayList<Warning>()

    override fun initialize(applicationContext: Context, configParameters: ConfigParameters, locale: String?, uiCustomization: UiCustomization?) {
        if (initialized) throw SDKAlreadyInitializedException("SDK already initialized")

        // ... start a background thread to initialize ...
        initialized = true
        deviceData = "here is some device data"

        // callback method execution
        callback.initializeComplete()
    }

    override fun createTransaction(directoryServerID: String, messageVersion: String?): Transaction {
        if (!initialized) throw SDKNotInitializedException("SDK not initialized")

        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun cleanup() {
        deviceData = null
        initialized = false
    }

    override fun getSDKVersion(): String {
        return BuildConfig.VERSION_NAME
    }

    override fun getWarnings(): List<Warning> {
        return warnings
    }
}
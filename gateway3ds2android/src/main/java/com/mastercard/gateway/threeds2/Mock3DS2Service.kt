package com.mastercard.gateway.threeds2

import android.content.Context
import java.lang.Exception

class Mock3DS2Service() : ThreeDS2Service {

    companion object {
        const val SDK_REF_NUMBER = "1234567890"
    }

    interface InitializeCallback {
        fun initializeComplete()
        fun initializeError(e: Exception)
    }

    var initialized: Boolean = false
        private set

    var deviceData: String? = null
        private set

    var initializeCallback: InitializeCallback? = null

    private val warnings = ArrayList<Warning>()

    override fun initialize(applicationContext: Context, configParameters: ConfigParameters, locale: String?, uiCustomization: UiCustomization?) {
        if (initialized) throw SDKAlreadyInitializedException("SDK already initialized")

        // ... start a background thread to initialize ...
        initialized = true
        deviceData = "here is some device data"

        // initializeCallback method execution
        initializeCallback?.initializeComplete()
    }

    override fun createTransaction(directoryServerID: String, messageVersion: String?): Transaction {
        if (!initialized) throw SDKNotInitializedException("SDK not initialized")

        return MockTransaction()
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
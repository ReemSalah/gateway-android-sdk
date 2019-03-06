package com.mastercard.gateway.threeds2

import android.content.Context

class Mock3DS2Service : ThreeDS2Service {

    var initialized: Boolean = false
        internal set

    override fun initialize(applicationContext: Context, configParameters: ConfigParameters, locale: String?, uiCustomization: UiCustomization?) {
        if (initialized) throw SDKAlreadyInitializedException("SDK already initialized")
        // TODO start a background thread to initialize

        // TODO callback method execution

        initialized = true
    }

    override fun createTransaction(directoryServerID: String, messageVersion: String?): Transaction {
        if (!initialized) throw SDKNotInitializedException("SDK not initialized")

        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun cleanup() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getSDKVersion(): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getWarnings(): List<Warning> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
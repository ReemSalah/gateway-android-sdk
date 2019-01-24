package com.mastercard.gateway.android.sdk

import java.util.HashMap

internal class GatewayRequest(val url: String, val method: Method, val payload: GatewayMap, val headers: MutableMap<String, String> = mutableMapOf()) {

    // internally supported request methods
    internal enum class Method {
        PUT
    }
}

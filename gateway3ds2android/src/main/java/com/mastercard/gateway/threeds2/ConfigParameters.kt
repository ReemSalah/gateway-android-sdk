package com.mastercard.gateway.threeds2

class ConfigParameters {

    companion object {
        const val DEFAULT_GROUP = "default"
    }

    private val params = HashMap<String, HashMap<String, String>>()

    @JvmOverloads
    fun addParam(group: String = DEFAULT_GROUP, paramName: String, paramValue: String) {
        if (!params.containsKey(group)) {
            params[group] = HashMap()
        }

        params[group]?.run { put(paramName, paramValue) }
    }

    @JvmOverloads
    fun getParam(group: String = DEFAULT_GROUP, paramName: String): String? {
        return params[group]?.run { get(paramName) }
    }

    @JvmOverloads
    fun removeParam(group: String = DEFAULT_GROUP, paramName: String): String? {
        return params[group]?.run { remove(paramName) }
    }
}
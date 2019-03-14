/*
 * Copyright (c) 2016 Mastercard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mastercard.gateway.android.sdk

import android.os.Handler
import com.google.gson.Gson
import io.reactivex.Single
import java.io.ByteArrayInputStream
import java.net.URL
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

internal class GatewayComms {

    internal var logger: Logger = BaseLogger()
    internal var gson = Gson()

    internal fun runGatewayRequest(request: GatewayRequest, callback: GatewayCallback) {
        // create handler on current thread
        val handler = Handler { msg -> handleCallbackMessage(callback, msg.obj) }

        Thread {
            val m = handler.obtainMessage()
            try {
                m.obj = executeGatewayRequest(request)
            } catch (e: Exception) {
                m.obj = e
            }

            handler.sendMessage(m)
        }.start()
    }

    internal fun runGatewayRequest(request: GatewayRequest): Single<GatewayMap> {
        return Single.fromCallable { executeGatewayRequest(request) }
    }

    // handler callback method when executing a request on a new thread
    internal fun handleCallbackMessage(callback: GatewayCallback?, arg: Any): Boolean {
        if (callback != null) {
            if (arg is Throwable) {
                callback.onError(arg)
            } else {
                callback.onSuccess(arg as GatewayMap)
            }
        }
        return true
    }

    internal fun executeGatewayRequest(request: GatewayRequest): GatewayMap {
        // init connection
        val c = createHttpsUrlConnection(request)

        // encode request data to json
        val requestData = gson.toJson(request.payload)

        // log request data
        logger.logRequest(c, requestData)

        // write request data
        if (requestData != null) {
            val os = c.outputStream
            os.write(requestData.toByteArray(charset("UTF-8")))
            os.close()
        }

        // initiate the connection
        c.connect()

        var responseData: String? = null
        val statusCode = c.responseCode
        val isStatusOk = isStatusCodeOk(statusCode)

        // if connection has output stream, get the data
        // socket time-out exceptions will be thrown here
        if (c.doInput) {
            val inputStream = if (isStatusOk) c.inputStream else c.errorStream
            responseData = inputStream.readTextAndClose()
        }

        c.disconnect()

        // log response
        logger.logResponse(c, responseData)

        // parse the response body
        val response = GatewayMap(responseData)

        // if response static is good, return response
        if (isStatusOk) {
            return response
        }

        // otherwise, create a gateway exception and throw it
        var message = response["error.explanation"] as String?
        if (message == null) {
            message = "An error occurred"
        }

        throw GatewayException(message, statusCode, response)
    }

    internal fun createSslContext(): SSLContext {
        // create and initialize a KeyStore
        val keyStore = createSslKeyStore()

        // create a TrustManager that trusts the INTERMEDIATE_CA in our KeyStore
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(keyStore)

        val trustManagers = tmf.trustManagers

        val context = SSLContext.getInstance("TLS")
        context.init(null, trustManagers, null)

        return context
    }

    internal fun createSslKeyStore(): KeyStore {
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)

        // add our trusted cert to the keystore
        keyStore.setCertificateEntry("gateway.mastercard.com", readCertificate(INTERMEDIATE_CA))

        return keyStore
    }

    internal fun readCertificate(cert: String): X509Certificate {
        val bytes = cert.toByteArray()
        val inputStream = ByteArrayInputStream(bytes)

        return CertificateFactory.getInstance("X.509").generateCertificate(inputStream) as X509Certificate
    }

    internal fun createHttpsUrlConnection(request: GatewayRequest): HttpsURLConnection {
        // parse url
        val url = URL(request.url)

        // init ssl context with limiting trust managers
        val context = createSslContext()

        val c = url.openConnection() as HttpsURLConnection
        c.sslSocketFactory = context.socketFactory
        c.connectTimeout = CONNECTION_TIMEOUT
        c.readTimeout = READ_TIMEOUT
        c.requestMethod = request.method.name
        c.doOutput = true

        c.setRequestProperty("User-Agent", USER_AGENT)
        c.setRequestProperty("Content-Type", "application/json")

        // add extra headers
        for (key in request.headers.keys) {
            c.setRequestProperty(key, request.headers[key])
        }

        return c
    }

    internal fun isStatusCodeOk(statusCode: Int): Boolean {
        return statusCode in 200..299
    }

    companion object {
        const val MIN_API_VERSION = 39
        const val CONNECTION_TIMEOUT = 15000
        const val READ_TIMEOUT = 60000
        const val USER_AGENT = "Gateway-Android-SDK/" + BuildConfig.VERSION_NAME
        const val INTERMEDIATE_CA = "-----BEGIN CERTIFICATE-----\n" +
                "MIIFAzCCA+ugAwIBAgIEUdNg7jANBgkqhkiG9w0BAQsFADCBvjELMAkGA1UEBhMC\n" +
                "VVMxFjAUBgNVBAoTDUVudHJ1c3QsIEluYy4xKDAmBgNVBAsTH1NlZSB3d3cuZW50\n" +
                "cnVzdC5uZXQvbGVnYWwtdGVybXMxOTA3BgNVBAsTMChjKSAyMDA5IEVudHJ1c3Qs\n" +
                "IEluYy4gLSBmb3IgYXV0aG9yaXplZCB1c2Ugb25seTEyMDAGA1UEAxMpRW50cnVz\n" +
                "dCBSb290IENlcnRpZmljYXRpb24gQXV0aG9yaXR5IC0gRzIwHhcNMTQxMDIyMTcw\n" +
                "NTE0WhcNMjQxMDIzMDczMzIyWjCBujELMAkGA1UEBhMCVVMxFjAUBgNVBAoTDUVu\n" +
                "dHJ1c3QsIEluYy4xKDAmBgNVBAsTH1NlZSB3d3cuZW50cnVzdC5uZXQvbGVnYWwt\n" +
                "dGVybXMxOTA3BgNVBAsTMChjKSAyMDEyIEVudHJ1c3QsIEluYy4gLSBmb3IgYXV0\n" +
                "aG9yaXplZCB1c2Ugb25seTEuMCwGA1UEAxMlRW50cnVzdCBDZXJ0aWZpY2F0aW9u\n" +
                "IEF1dGhvcml0eSAtIEwxSzCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEB\n" +
                "ANo/ltBNuS9E59s5XptQ7lylYdpBZ1MJqgCajld/KWvbx+EhJKo60I1HI9Ltchbw\n" +
                "kSHSXbe4S6iDj7eRMmjPziWTLLJ9l8j+wbQXugmeA5CTe3xJgyJoipveR8MxmHou\n" +
                "fUAL0u8+07KMqo9Iqf8A6ClYBve2k1qUcyYmrVgO5UK41epzeWRoUyW4hM+Ueq4G\n" +
                "RQyja03Qxr7qGKQ28JKyuhyIjzpSf/debYMcnfAf5cPW3aV4kj2wbSzqyc+UQRlx\n" +
                "RGi6RzwE6V26PvA19xW2nvIuFR4/R8jIOKdzRV1NsDuxjhcpN+rdBQEiu5Q2Ko1b\n" +
                "Nf5TGS8IRsEqsxpiHU4r2RsCAwEAAaOCAQkwggEFMA4GA1UdDwEB/wQEAwIBBjAP\n" +
                "BgNVHRMECDAGAQH/AgEAMDMGCCsGAQUFBwEBBCcwJTAjBggrBgEFBQcwAYYXaHR0\n" +
                "cDovL29jc3AuZW50cnVzdC5uZXQwMAYDVR0fBCkwJzAloCOgIYYfaHR0cDovL2Ny\n" +
                "bC5lbnRydXN0Lm5ldC9nMmNhLmNybDA7BgNVHSAENDAyMDAGBFUdIAAwKDAmBggr\n" +
                "BgEFBQcCARYaaHR0cDovL3d3dy5lbnRydXN0Lm5ldC9ycGEwHQYDVR0OBBYEFIKi\n" +
                "cHTdvFM/z3vU981/p2DGCky/MB8GA1UdIwQYMBaAFGpyJnrQHu995ztpUdRsjZ+Q\n" +
                "EmarMA0GCSqGSIb3DQEBCwUAA4IBAQA/HBpb/0AiHY81DC2qmSerwBEycNc2KGml\n" +
                "jbEnmUK+xJPrSFdDcSPE5U6trkNvknbFGe/KvG9CTBaahqkEOMdl8PUM4ErfovrO\n" +
                "GhGonGkvG9/q4jLzzky8RgzAiYDRh2uiz2vUf/31YFJnV6Bt0WRBFG00Yu0GbCTy\n" +
                "BrwoAq8DLcIzBfvLqhboZRBD9Wlc44FYmc1r07jHexlVyUDOeVW4c4npXEBmQxJ/\n" +
                "B7hlVtWNw6f1sbZlnsCDNn8WRTx0S5OKPPEr9TVwc3vnggSxGJgO1JxvGvz8pzOl\n" +
                "u7sY82t6XTKH920l5OJ2hiEeEUbNdg5vT6QhcQqEpy02qUgiUX6C\n" +
                "-----END CERTIFICATE-----\n"
    }
}
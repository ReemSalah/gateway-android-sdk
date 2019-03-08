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

package com.mastercard.gateway.android.sampleapp

import android.os.Handler
import android.util.Base64
import android.util.Log
import android.util.Pair

import com.google.gson.GsonBuilder
import com.mastercard.gateway.android.sdk.GatewayMap

import java.io.IOException
import java.io.PrintWriter
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.URL
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException

import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext

import android.text.TextUtils.isEmpty

/**
 * ApiController object used to send create and update session requests. Conforms to the singleton
 * pattern.
 */
class ApiController private constructor() {

    internal lateinit var merchantServerUrl: String


    interface CreateSessionCallback {
        fun onSuccess(sessionId: String, apiVersion: String)

        fun onError(throwable: Throwable)
    }

    interface Check3DSecureEnrollmentCallback {
        fun onSuccess(response: GatewayMap)

        fun onError(throwable: Throwable)
    }

    interface CompleteSessionCallback {
        fun onSuccess(result: String)

        fun onError(throwable: Throwable)
    }

    fun setMerchantServerUrl(url: String) {
        merchantServerUrl = url
    }

    fun createSession(callback: CreateSessionCallback?) {
        val handler = Handler { message ->
            if (callback != null) {
                if (message.obj is Throwable) {
                    callback.onError(message.obj as Throwable)
                } else {
                    val pair = message.obj as Pair<String, String>
                    callback.onSuccess(pair.first, pair.second)
                }
            }
            true
        }

        Thread {
            val m = handler.obtainMessage()
            try {
                m.obj = executeCreateSession()
            } catch (e: Exception) {
                m.obj = e
            }

            handler.sendMessage(m)
        }.start()
    }

    fun check3DSecureEnrollment(sessionId: String, amount: String, currency: String, threeDSecureId: String, callback: Check3DSecureEnrollmentCallback?) {
        val handler = Handler { message ->
            if (callback != null) {
                if (message.obj is Throwable) {
                    callback.onError(message.obj as Throwable)
                } else {
                    callback.onSuccess(message.obj as GatewayMap)
                }
            }
            true
        }

        Thread {
            val m = handler.obtainMessage()
            try {
                m.obj = executeCheck3DSEnrollment(sessionId, amount, currency, threeDSecureId)
            } catch (e: Exception) {
                m.obj = e
            }

            handler.sendMessage(m)
        }.start()
    }

    fun completeSession(sessionId: String, orderId: String, transactionId: String, amount: String, currency: String, threeDSecureTwoId: String?, threeDSecureTwoStatus: String?, isGooglePay: Boolean?, callback: CompleteSessionCallback?) {
        val handler = Handler { message ->
            if (callback != null) {
                if (message.obj is Throwable) {
                    callback.onError(message.obj as Throwable)
                } else {
                    callback.onSuccess(message.obj as String)
                }
            }
            true
        }

        Thread {
            val m = handler.obtainMessage()
            try {
                m.obj = executeCompleteSession(sessionId, orderId, transactionId, amount, currency, threeDSecureTwoId, threeDSecureTwoStatus, isGooglePay)
            } catch (e: Exception) {
                m.obj = e
            }

            handler.sendMessage(m)
        }.start()
    }

    @Throws(Exception::class)
    internal fun executeCreateSession(): Pair<String, String> {
        val jsonResponse = doJsonRequest(URL("$merchantServerUrl/session.php"), "", "POST", null, null, HttpsURLConnection.HTTP_OK)

        val response = GatewayMap(jsonResponse)

        if (!response.containsKey("gatewayResponse")) {
            throw RuntimeException("Could not read gateway response")
        }

        if (!response.containsKey("gatewayResponse.result") || !"SUCCESS".equals((response["gatewayResponse.result"] as String?)!!, ignoreCase = true)) {
            throw RuntimeException("Create session result: " + response["gatewayResponse.result"]!!)
        }

        val apiVersion = response["apiVersion"] as String?
        val sessionId = response["gatewayResponse.session.id"] as String?
        Log.i("createSession", "Created session with ID $sessionId with API version $apiVersion")

        return Pair<String, String>(sessionId, apiVersion)
    }

    @Throws(Exception::class)
    internal fun executeCheck3DSEnrollment(sessionId: String, amount: String, currency: String, threeDSecureId: String): GatewayMap {
        val request = GatewayMap()
                .set("apiOperation", "CHECK_3DS_ENROLLMENT")
                .set("session.id", sessionId)
                .set("order.amount", amount)
                .set("order.currency", currency)
                .set("3DSecure.authenticationRedirect.responseUrl", "$merchantServerUrl/3DSecureResult.php?3DSecureId=$threeDSecureId")

        val jsonRequest = GSON.toJson(request)

        val jsonResponse = doJsonRequest(URL("$merchantServerUrl/3DSecure.php?3DSecureId=$threeDSecureId"), jsonRequest, "PUT", null, null, HttpsURLConnection.HTTP_OK)

        val response = GatewayMap(jsonResponse)

        if (!response.containsKey("gatewayResponse")) {
            throw RuntimeException("Could not read gateway response")
        }

        // if there is an error result, throw it
        if (response.containsKey("gatewayResponse.result") && "ERROR".equals((response["gatewayResponse.result"] as String?)!!, ignoreCase = true)) {
            throw RuntimeException("Check 3DS Enrollment Error: " + response["gatewayResponse.error.explanation"]!!)
        }

        return response
    }

    @Throws(Exception::class)
    internal fun executeCompleteSession(sessionId: String, orderId: String, transactionId: String, amount: String, currency: String, threeDSecureTwoId: String?, threeDSecureTwoStatus: String?, isGooglePay: Boolean?): String {
        val request = GatewayMap()
                .set("apiOperation", "PAY")
                .set("session.id", sessionId)
                .set("order.amount", amount)
                .set("order.currency", currency)
                .set("sourceOfFunds.type", "CARD")
                .set("transaction.source", "INTERNET")
                .set("transaction.frequency", "SINGLE")

        if  (threeDSecureTwoId != null) {
            request["authentication.3ds2.dsTransactionId"] = threeDSecureTwoId
            request["authentication.3ds2.transactionStatus"] = threeDSecureTwoStatus
        }

        if (isGooglePay!!) {
            request["order.walletProvider"] = "GOOGLE_PAY"
        }

        val jsonRequest = GSON.toJson(request)

        val jsonResponse = doJsonRequest(URL("$merchantServerUrl/transaction.php?order=$orderId&transaction=$transactionId"), jsonRequest, "PUT", null, null, HttpsURLConnection.HTTP_OK)

        val response = GatewayMap(jsonResponse)

        if (!response.containsKey("gatewayResponse")) {
            throw RuntimeException("Could not read gateway response")
        }

        if (!response.containsKey("gatewayResponse.result") || !"SUCCESS".equals((response["gatewayResponse.result"] as String?)!!, ignoreCase = true)) {
            throw RuntimeException("Error processing payment")
        }

        return response["gatewayResponse.result"] as String
    }


    /**
     * Initialise a new SSL context using the algorithm, key manager(s), trust manager(s) and
     * source of randomness.
     *
     * @throws NoSuchAlgorithmException if the algorithm is not supported by the android platform
     * @throws KeyManagementException   if initialization of the context fails
     */
    @Throws(NoSuchAlgorithmException::class, KeyManagementException::class)
    internal fun initialiseSslContext() {
        val context = SSLContext.getInstance("TLS")
        context.init(null, null, null)
        HttpsURLConnection.setDefaultSSLSocketFactory(context.socketFactory)
    }

    /**
     * Open an HTTP or HTTPS connection to a particular URL
     *
     * @param address a valid HTTP[S] URL to connect to
     * @return an HTTP or HTTPS connection as appropriate
     * @throws KeyManagementException   if initialization of the SSL context fails
     * @throws NoSuchAlgorithmException if the SSL algorithm is not supported by the android platform
     * @throws MalformedURLException    if the address was not in the HTTP or HTTPS scheme
     * @throws IOException              if the connection could not be opened
     */
    @Throws(KeyManagementException::class, NoSuchAlgorithmException::class, IOException::class)
    internal fun openConnection(address: URL): HttpURLConnection {

        when (address.protocol.toUpperCase()) {
            "HTTPS" -> initialiseSslContext()
            "HTTP" -> {
            }
            else -> throw MalformedURLException("Not an HTTP[S] address")
        }

        val connection = address.openConnection() as HttpURLConnection
        connection.connectTimeout = 30000
        connection.readTimeout = 60000
        return connection
    }

    /**
     * Send a JSON object to an open HTTP[S] connection
     *
     * @param connection an open HTTP[S] connection, as returned by [.openConnection]
     * @param method     an HTTP method, e.g. PUT, POST or GET
     * @param json       a valid JSON-formatted object
     * @param username   user name for basic authorization (can be null for no auth)
     * @param password   password for basic authorization (can be null for no auth)
     * @return an HTTP response code
     * @throws IOException if the connection could not be written to
     */
    @Throws(IOException::class)
    internal fun makeJsonRequest(connection: HttpURLConnection, method: String, json: String,
                                 username: String?, password: String?): Int {

        connection.doOutput = true
        connection.requestMethod = method
        connection.setFixedLengthStreamingMode(json.toByteArray().size)
        connection.setRequestProperty("Content-Type", "application/json")

        if (!isEmpty(username) && !isEmpty(password)) {
            var basicAuth = "$username:$password"
            basicAuth = Base64.encodeToString(basicAuth.toByteArray(), Base64.DEFAULT)
            connection.setRequestProperty("Authorization", "Basic $basicAuth")
        }

        val out = PrintWriter(connection.outputStream)
        out.print(json)
        out.close()

        return connection.responseCode
    }

    /**
     * Retrieve a JSON response from an open HTTP[S] connection. This would typically be called
     * after [.makeJsonRequest]
     *
     * @param connection an open HTTP[S] connection
     * @return a json object in string form
     * @throws IOException if the connection could not be read from
     */
    @Throws(IOException::class)
    internal fun getJsonResponse(connection: HttpURLConnection): String {
        // If the HTTP response code is 4xx or 5xx, we need error rather than input stream
        val stream = if (connection.responseCode < 400)
            connection.inputStream
        else
            connection.errorStream

        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    /**
     * End-to-end method to send some json to an url and retrieve a response
     *
     * @param address             url to send the request to
     * @param jsonRequest         a valid JSON-formatted object
     * @param httpMethod          an HTTP method, e.g. PUT, POST or GET
     * @param username            user name for basic authorization (can be null for no auth)
     * @param password            password for basic authorization (can be null for no auth)
     * @param expectResponseCodes permitted HTTP response codes, e.g. HTTP_OK (200)
     * @return a json response object in string form
     */
    internal fun doJsonRequest(address: URL, jsonRequest: String, httpMethod: String, username: String?, password: String?, vararg expectResponseCodes: Int): String {

        val connection: HttpURLConnection
        val responseCode: Int

        try {
            connection = openConnection(address)
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException("Couldn't initialise SSL context", e)
        } catch (e: KeyManagementException) {
            throw RuntimeException("Couldn't initialise SSL context", e)
        } catch (e: IOException) {
            throw RuntimeException("Couldn't open an HTTP[S] connection", e)
        }

        try {
            responseCode = makeJsonRequest(connection, httpMethod, jsonRequest, username, password)

            if (!contains(expectResponseCodes, responseCode)) {
                throw RuntimeException("Unexpected response code $responseCode")
            }
        } catch (e: SocketTimeoutException) {
            throw RuntimeException("Timeout whilst sending JSON data")
        } catch (e: IOException) {
            throw RuntimeException("Error sending JSON data", e)
        }

        try {

            return getJsonResponse(connection) ?: throw RuntimeException("No data in response")
        } catch (e: SocketTimeoutException) {
            throw RuntimeException("Timeout whilst retrieving JSON response")
        } catch (e: IOException) {
            throw RuntimeException("Error retrieving JSON response", e)
        }

    }

    companion object {

        val instance = ApiController()

        internal val GSON = GsonBuilder().create()


        internal fun contains(haystack: IntArray, needle: Int): Boolean {
            for (candidate in haystack) {
                if (candidate == needle) {
                    return true
                }
            }

            return false
        }
    }
}


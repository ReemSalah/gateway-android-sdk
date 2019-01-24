package com.mastercard.gateway.android.sdk


import android.app.Activity
import android.content.Intent
import android.os.Parcelable

import com.google.android.gms.common.api.Status
import com.google.android.gms.wallet.AutoResolveHelper
import com.google.android.gms.wallet.PaymentData
import com.nhaarman.mockito_kotlin.*

import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import java.security.cert.X509Certificate

import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class GatewayTest {

    private lateinit var gateway: Gateway


    @Before
    fun setUp() {
        gateway = spy(Gateway())
    }

    @Test
    fun testSetMerchantIdWorksAsExpected() {
        gateway.setMerchantId("MERCHANT_ID")

        assertEquals("MERCHANT_ID", gateway.merchantId)
    }

    @Test
    fun testSetRegionWorksAsIntended() {
        gateway.setRegion(Gateway.Region.ASIA_PACIFIC)

        assertEquals(Gateway.Region.ASIA_PACIFIC, gateway.region)
    }

    @Test
    fun testBuildUpdateSessionRequestWorksAsExpected() {
        gateway.merchantId = "MERCHANT_ID"
        gateway.region = Gateway.Region.MTF

        val sessionId = "session_id"
        val apiVersion = Gateway.MIN_API_VERSION.toString()
        val payload = GatewayMap()

        val expectedUrl = "some url"

        whenever(gateway.getUpdateSessionUrl(sessionId, apiVersion)).thenReturn(expectedUrl)

        val request = gateway.buildUpdateSessionRequest(sessionId, apiVersion, payload)

        assertTrue(request.payload.containsKey("device.browser"))
        assertTrue(request.payload.containsKey("apiOperation"))
        assertEquals(Gateway.API_OPERATION, request.payload["apiOperation"])
        assertEquals(Gateway.USER_AGENT, request.payload["device.browser"])
    }

    @Test
    fun testBuildUpdateSessionRequestHandlesApiVersion50() {
        gateway.merchantId = "MERCHANT_ID"
        gateway.region = Gateway.Region.MTF

        val sessionId = "somesession"
        val apiVersion = "50"
        val payload = GatewayMap()

        val expectedUrl = "some url"
        val expectedAuthHeader = "Basic bWVyY2hhbnQuTUVSQ0hBTlRfSUQ6c29tZXNlc3Npb24="

        whenever(gateway.getUpdateSessionUrl(sessionId, apiVersion)).thenReturn(expectedUrl)

        val request = gateway.buildUpdateSessionRequest(sessionId, apiVersion, payload)

        assertTrue(request.payload.containsKey("device.browser"))
        assertFalse(request.payload.containsKey("apiOperation"))
        assertEquals(Gateway.USER_AGENT, request.payload["device.browser"])

        assertTrue(request.headers.containsKey("Authorization"))
        assertEquals(expectedAuthHeader, request.headers["Authorization"])
    }

    @Test
    fun testStart3DSecureActivitySkipsTitleIfNull() {
        val activity: Activity = mock()
        val intent = Intent()
        val testHtml = "html"

        Gateway.start3DSecureActivity(activity, testHtml, null, intent)

        verify(activity).startActivityForResult(intent, Gateway.REQUEST_3D_SECURE)
        assertTrue(intent.hasExtra(Gateway3DSecureActivity.EXTRA_HTML))
        assertFalse(intent.hasExtra(Gateway3DSecureActivity.EXTRA_TITLE))
        assertEquals(testHtml, intent.getStringExtra(Gateway3DSecureActivity.EXTRA_HTML))
    }

    @Test
    fun testStart3DSecureActivityWorksAsExpected() {
        val activity: Activity = mock()
        val intent = Intent()
        val testHtml = "html"
        val testTitle = "title"

        Gateway.start3DSecureActivity(activity, testHtml, testTitle, intent)

        verify(activity).startActivityForResult(intent, Gateway.REQUEST_3D_SECURE)
        assertTrue(intent.hasExtra(Gateway3DSecureActivity.EXTRA_HTML))
        assertTrue(intent.hasExtra(Gateway3DSecureActivity.EXTRA_TITLE))
        assertEquals(testHtml, intent.getStringExtra(Gateway3DSecureActivity.EXTRA_HTML))
        assertEquals(testTitle, intent.getStringExtra(Gateway3DSecureActivity.EXTRA_TITLE))
    }

    @Test
    fun testHandle3DSecureResultReturnsFalseWithNullCallback() {
        assertFalse(Gateway.handle3DSecureResult(0, 0, Intent(), null))
    }

    @Test
    fun testHandle3DSSecureResultReturnsFalseIfInvalidRequestCode() {
        val invalidRequestCode = 10
        val callback: Gateway3DSecureCallback = mock()

        assertFalse(Gateway.handle3DSecureResult(invalidRequestCode, 0, Intent(), callback))
    }

    @Test
    fun testHandle3DSecureResultCallsCancelIfResultNotOk() {
        val validRequestCode = Gateway.REQUEST_3D_SECURE
        val resultCode = Activity.RESULT_CANCELED
        val callback: Gateway3DSecureCallback = mock()

        val result = Gateway.handle3DSecureResult(validRequestCode, resultCode, Intent(), callback)

        assertTrue(result)
        verify(callback).on3DSecureCancel()
    }

    @Test
    fun testHandle3DSecureResultCallsCompleteIfResultOK() {
        val validRequestCode = Gateway.REQUEST_3D_SECURE
        val resultCode = Activity.RESULT_OK
        val data: Intent = mock()
        val acsResultJson = "{\"foo\":\"bar\"}"

        val callback = spy(object : Gateway3DSecureCallback {
            override fun on3DSecureComplete(response: GatewayMap) {
                assertNotNull(response)
                assertTrue(response.containsKey("foo"))
                assertEquals("bar", response["foo"])
            }

            override fun on3DSecureCancel() {
                fail("Should never have called cancel")
            }
        })

        whenever(data.getStringExtra(Gateway3DSecureActivity.EXTRA_ACS_RESULT)).thenReturn(acsResultJson)

        val result = Gateway.handle3DSecureResult(validRequestCode, resultCode, data, callback)

        assertTrue(result)
        verify<Gateway3DSecureCallback>(callback).on3DSecureComplete(any())
    }

    @Test
    fun testHandleGooglePayResultReturnsFalseWithNullCallback() {
        assertFalse(Gateway.handleGooglePayResult(0, 0, Intent(), null))
    }

    @Test
    fun testHandleGooglePayResultReturnsFalseIfInvalidRequestCode() {
        val invalidRequestCode = 10
        val callback: GatewayGooglePayCallback = mock()

        assertFalse(Gateway.handleGooglePayResult(invalidRequestCode, 0, Intent(), callback))
    }

    @Test
    fun testHandleGooglePayResultCallsError() {
        val requestCode = Gateway.REQUEST_GOOGLE_PAY_LOAD_PAYMENT_DATA
        val resultCode = AutoResolveHelper.RESULT_ERROR

        // mock autoresolvehelper method
        val mockStatus: Status = mock()
        val mockData: Intent = mock()
        whenever(mockData.getParcelableExtra<Parcelable>("com.google.android.gms.common.api.AutoResolveHelper.status")).thenReturn(mockStatus)

        val callback = spy(object : GatewayGooglePayCallback {
            override fun onReceivedPaymentData(paymentData: JSONObject) {
                fail("Should not have received payment data")
            }

            override fun onGooglePayCancelled() {
                fail("Should not have called cancelled")
            }

            override fun onGooglePayError(status: Status) {
                assertEquals(mockStatus, status)
            }
        })

        val result = Gateway.handleGooglePayResult(requestCode, resultCode, mockData, callback)

        assertTrue(result)
        verify<GatewayGooglePayCallback>(callback).onGooglePayError(any())
    }

    @Test
    fun testHandleGooglePayResultCallsCancelled() {
        val requestCode = Gateway.REQUEST_GOOGLE_PAY_LOAD_PAYMENT_DATA
        val resultCode = Activity.RESULT_CANCELED

        val callback: GatewayGooglePayCallback = mock()

        val result = Gateway.handleGooglePayResult(requestCode, resultCode, Intent(), callback)

        assertTrue(result)
        verify(callback).onGooglePayCancelled()
    }

    @Test
    fun testHandleGooglePayResultCallsPaymentDataOnSuccess() {
        val requestCode = Gateway.REQUEST_GOOGLE_PAY_LOAD_PAYMENT_DATA
        val resultCode = Activity.RESULT_OK
        val pData = PaymentData.fromJson("{}")
        val data = Intent()
        pData.putIntoIntent(data)

        val callback: GatewayGooglePayCallback = mock()

        val result = Gateway.handleGooglePayResult(requestCode, resultCode, data, callback)

        assertTrue(result)
        verify(callback).onReceivedPaymentData(any())
    }

    @Test
    fun testGetApiUrlThrowsExceptionIfRegionIsNull() {
        val apiVersion = "44"
        gateway.region = null

        try {
            val apiUrl = gateway.getApiUrl(apiVersion)

            fail("Null region should have caused illegal state exception")
        } catch (e: Exception) {
            assertTrue(e is IllegalStateException)
        }

    }

    @Test
    fun testGetApiUrlThrowsExceptionIfApiVersionIsLessThanMin() {
        val apiVersion = (Gateway.MIN_API_VERSION - 1).toString()

        try {
            val apiUrl = gateway.getApiUrl(apiVersion)

            fail("Api version less than minimum value should have caused illegal argument exception")
        } catch (e: Exception) {
            assertTrue(e is IllegalArgumentException)
        }

    }

    @Test
    fun testGetApiUrlWorksAsIntended() {
        gateway.setRegion(Gateway.Region.NORTH_AMERICA)
        val expectedUrl = "https://na-gateway.mastercard.com/api/rest/version/" + Gateway.MIN_API_VERSION

        assertEquals(expectedUrl, gateway.getApiUrl(Gateway.MIN_API_VERSION.toString()))
    }

    @Test
    fun testGetUpdateSessionUrlThrowsExceptionIfSessionIdIsNull() {
        try {
            gateway.getUpdateSessionUrl(null, Gateway.MIN_API_VERSION.toString())

            fail("Null session id should throw illegal argument exception")
        } catch (e: Exception) {
            assertTrue(e is IllegalArgumentException)
        }

    }


    @Test
    fun testGetUpdateSessionUrlThrowsExceptionIfMerchantIdIsNull() {
        gateway.merchantId = null

        try {
            val url = gateway.getUpdateSessionUrl("sess1234", Gateway.MIN_API_VERSION.toString())

            fail("Null merchant id should have caused illegal state exception")
        } catch (e: Exception) {
            assertTrue(e is IllegalStateException)
        }

    }

    @Test
    fun testGetUpdateSessionUrlWorksAsIntended() {
        gateway.merchantId = "somemerchant"
        gateway.region = Gateway.Region.NORTH_AMERICA

        val expectedUrl = "https://na-gateway.mastercard.com/api/rest/version/" + Gateway.MIN_API_VERSION + "/merchant/somemerchant/session/sess1234"

        val actualUrl = gateway.getUpdateSessionUrl("sess1234", Gateway.MIN_API_VERSION.toString())

        assertEquals(expectedUrl, actualUrl)
    }

    @Test
    fun testHandleCallbackMessageCallsOnErrorWithThrowableArg() {
        val callback: GatewayCallback = mock()
        val arg = Exception("Some exception")

        gateway.handleCallbackMessage(callback, arg)

        verify(callback).onError(arg)
    }

    @Test
    fun testHandleCallbackMessageCallsSuccessWithNonThrowableArg() {
        val callback: GatewayCallback = mock()
        val arg: GatewayMap = mock()

        gateway.handleCallbackMessage(callback, arg)

        verify(callback).onSuccess(arg)
    }

    @Test
    fun testCreateSslKeystoreContainsInternalCertificate() {
        val mockCert: X509Certificate = mock()
        whenever(gateway.readCertificate(Gateway.INTERMEDIATE_CA)).thenReturn(mockCert)

        val keyStore = gateway.createSslKeyStore()

        assertTrue(keyStore.containsAlias("gateway.mastercard.com"))
    }

    @Test
    fun testReadingInternalCertificateWorksAsExpected() {
        val certificate = gateway.readCertificate(Gateway.INTERMEDIATE_CA)
        val expectedSerialNo = "1372807406"

        assertNotNull(certificate)
        assertEquals(expectedSerialNo, certificate.serialNumber.toString())
    }

    @Test
    fun testCreateConnectionWorksAsIntended() {
        val request = GatewayRequest(
                url = "https://www.mastercard.com",
                method = GatewayRequest.Method.PUT,
                payload = GatewayMap()
        )

        val context: SSLContext = mock()
        val socketFactory: SSLSocketFactory = mock()
        whenever(context.socketFactory).thenReturn(socketFactory)
        whenever(gateway.createSslContext()).thenReturn(context)

        val c = gateway.createHttpsUrlConnection(request)

        assertEquals(request.url, c.url.toString())
        assertEquals(socketFactory, c.sslSocketFactory)
        assertEquals(Gateway.CONNECTION_TIMEOUT.toLong(), c.connectTimeout.toLong())
        assertEquals(Gateway.READ_TIMEOUT.toLong(), c.readTimeout.toLong())
        assertEquals("PUT", c.requestMethod)
        assertEquals(Gateway.USER_AGENT, c.getRequestProperty("User-Agent"))
        assertEquals("application/json", c.getRequestProperty("Content-Type"))
        assertTrue(c.doOutput)
    }

    @Test
    fun testIsStatusOkWorksAsIntended() {
        val tooLow = 199
        val tooHigh = 300
        val justRight = 200

        assertFalse(gateway.isStatusCodeOk(tooLow))
        assertFalse(gateway.isStatusCodeOk(tooHigh))
        assertTrue(gateway.isStatusCodeOk(justRight))
    }

    @Test
    fun testCreateAuthHeaderWorksAsExpected() {
        val sessionId = "somesession"
        gateway.merchantId = "MERCHANT_ID"

        val expectedAuthHeader = "Basic bWVyY2hhbnQuTUVSQ0hBTlRfSUQ6c29tZXNlc3Npb24="

        val authHeader = gateway.createAuthHeader(sessionId)

        assertEquals(expectedAuthHeader, authHeader)
    }
}

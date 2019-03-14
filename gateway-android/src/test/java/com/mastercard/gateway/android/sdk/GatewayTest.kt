package com.mastercard.gateway.android.sdk


import android.app.Activity
import android.content.Intent
import android.os.Parcelable

import com.google.android.gms.common.api.Status
import com.google.android.gms.wallet.AutoResolveHelper
import com.google.android.gms.wallet.PaymentData
import com.nhaarman.mockitokotlin2.*

import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.mockito.*

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class GatewayTest {

    @Spy
    private lateinit var gateway: Gateway

    @Mock
    private lateinit var mockComms: GatewayComms

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)

        doNothing().whenever(mockComms).runGatewayRequest(any(), any())
        gateway.comms = mockComms
    }

    @Test
    fun testBuildUpdateSessionRequestWorksAsExpected() {
        gateway.merchantId = "MERCHANT_ID"
        gateway.region = Gateway.Region.MTF

        val sessionId = "session_id"
        val apiVersion = GatewayComms.MIN_API_VERSION.toString()
        val payload = GatewayMap()

        gateway.updateSession(sessionId, apiVersion, payload, mock())

        // capture request
        val captor : KArgumentCaptor<GatewayRequest> = argumentCaptor()
        verify(mockComms).runGatewayRequest(captor.capture(), any())
        val request = captor.firstValue

        assertTrue(request.payload.containsKey("device.browser"))
        assertTrue(request.payload.containsKey("apiOperation"))
        assertEquals("UPDATE_PAYER_DATA", request.payload["apiOperation"])
        assertEquals(GatewayComms.USER_AGENT, request.payload["device.browser"])
    }

    @Test
    fun testBuildUpdateSessionRequestHandlesApiVersion50() {
        gateway.merchantId = "MERCHANT_ID"
        gateway.region = Gateway.Region.MTF

        val sessionId = "somesession"
        val apiVersion = "50"
        val payload = GatewayMap()

        val expectedAuthHeader = "Basic bWVyY2hhbnQuTUVSQ0hBTlRfSUQ6c29tZXNlc3Npb24="

        gateway.updateSession(sessionId, apiVersion, payload, mock())

        // capture request
        val captor : KArgumentCaptor<GatewayRequest> = argumentCaptor()
        verify(mockComms).runGatewayRequest(captor.capture(), any())
        val request = captor.firstValue

        assertTrue(request.payload.containsKey("device.browser"))
        assertFalse(request.payload.containsKey("apiOperation"))
        assertEquals(GatewayComms.USER_AGENT, request.payload["device.browser"])

        assertTrue(request.headers.containsKey("Authorization"))
        assertEquals(expectedAuthHeader, request.headers["Authorization"])
    }

    @Test
    fun testBuildAuthenticationRequestWorksAsExpected() {
        gateway.merchantId = "MERCHANT_ID"
        gateway.region = Gateway.Region.MTF

        val sessionId = "somesession"
        val apiVersion = "51"
        val payload = GatewayMap()
        val orderId = "someorder"
        val txnId = "sometxn"

        val expectedUrl = "https://test-gateway.mastercard.com/api/rest/version/$apiVersion/merchant/${gateway.merchantId}/order/$orderId/transaction/$txnId"
        val expectedAuthHeader = "Basic bWVyY2hhbnQuTUVSQ0hBTlRfSUQ6c29tZXNlc3Npb24="

        gateway.initiateAuthentication(sessionId, orderId, txnId, apiVersion, payload, mock())

        // capture request
        val acRequest : KArgumentCaptor<GatewayRequest> = argumentCaptor()
        verify(mockComms).runGatewayRequest(acRequest.capture(), any())
        val request = acRequest.firstValue

        assertEquals(expectedUrl, request.url)
        assertEquals(GatewayRequest.Method.PUT, request.method)
        assertTrue(request.headers.containsKey("Authorization"))
        assertEquals(expectedAuthHeader, request.headers["Authorization"])
    }

    @Test
    fun testBuildInitiateAuthenticationRequestAddsApiOperation() {
        gateway.merchantId = "MERCHANT_ID"
        gateway.region = Gateway.Region.MTF

        val sessionId = "somesession"
        val apiVersion = "51"
        val payload = GatewayMap()
        val orderId = "someorder"
        val txnId = "sometxn"

        val expectedApiOperation = "INITIATE_AUTHENTICATION"

        gateway.initiateAuthentication(sessionId, orderId, txnId, apiVersion, payload, mock())

        // capture request
        val acRequest : KArgumentCaptor<GatewayRequest> = argumentCaptor()
        verify(mockComms).runGatewayRequest(acRequest.capture(), any())
        val request = acRequest.firstValue

        assertTrue(request.payload.containsKey("apiOperation"))
        assertEquals(expectedApiOperation, request.payload["apiOperation"])
    }

    @Test
    fun testBuildAuthenticatePayerRequestAddsApiOperation() {
        gateway.merchantId = "MERCHANT_ID"
        gateway.region = Gateway.Region.MTF

        val sessionId = "somesession"
        val apiVersion = "51"
        val payload = GatewayMap()
        val orderId = "someorder"
        val txnId = "sometxn"

        val expectedApiOperation = "AUTHENTICATE_PAYER"

        gateway.authenticatePayer(sessionId, orderId, txnId, apiVersion, payload, mock())

        // capture request
        val acRequest : KArgumentCaptor<GatewayRequest> = argumentCaptor()
        verify(mockComms).runGatewayRequest(acRequest.capture(), any())
        val request = acRequest.firstValue

        assertTrue(request.payload.containsKey("apiOperation"))
        assertEquals(expectedApiOperation, request.payload["apiOperation"])
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
            override fun on3DSecureComplete(acsResult: GatewayMap) {
                assertNotNull(acsResult)
                assertTrue(acsResult.containsKey("foo"))
                assertEquals("bar", acsResult["foo"])
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
        val sessionId = "someSession"
        val apiVersion = "44"
        gateway.merchantId = "MERCHANT_ID"
        gateway.region = null

        try {
            gateway.updateSession(sessionId, apiVersion, GatewayMap(), mock())

            fail("Null region should have caused illegal state exception")
        } catch (e: Exception) {
            assertTrue(e is IllegalStateException)
        }

    }

    @Test
    fun testGetApiUrlThrowsExceptionIfApiVersionIsLessThanMin() {
        val sessionId = "someSession"
        val apiVersion = (GatewayComms.MIN_API_VERSION - 1).toString()
        gateway.merchantId = "MERCHANT_ID"

        try {
            gateway.updateSession(sessionId, apiVersion, GatewayMap(), mock())

            fail("Api version less than minimum value should have caused illegal argument exception")
        } catch (e: Exception) {
            assertTrue(e is IllegalArgumentException)
        }

    }

    @Test
    fun testGetUpdateSessionUrlThrowsExceptionIfMerchantIdIsNull() {
        gateway.merchantId = null
        val sessionId = "someSession"
        val apiVersion = GatewayComms.MIN_API_VERSION.toString()

        try {
            gateway.updateSession(sessionId, apiVersion, GatewayMap(), mock())

            fail("Null merchant id should have caused illegal state exception")
        } catch (e: Exception) {
            assertTrue(e is IllegalStateException)
        }

    }

    @Test
    fun testGetUpdateSessionUrlWorksAsIntended() {
        gateway.merchantId = "somemerchant"
        gateway.region = Gateway.Region.NORTH_AMERICA

        val sessionId = "someSession"
        val apiVersion = GatewayComms.MIN_API_VERSION.toString()

        val expectedUrl = "https://na-gateway.mastercard.com/api/rest/version/$apiVersion/merchant/somemerchant/session/$sessionId"

        gateway.updateSession(sessionId, apiVersion, GatewayMap(), mock())

        // capture request
        val acRequest : KArgumentCaptor<GatewayRequest> = argumentCaptor()
        verify(mockComms).runGatewayRequest(acRequest.capture(), any())
        val request = acRequest.firstValue

        assertEquals(expectedUrl, request.url)
    }

    @Test
    fun testCreateAuthHeaderWorksAsExpected() {
        gateway.merchantId = "MERCHANT_ID"
        gateway.region = Gateway.Region.NORTH_AMERICA

        val sessionId = "somesession"
        val orderId = "order"
        val txnId = "txn"
        val apiVersion = GatewayComms.MIN_API_VERSION.toString()
        val payload = GatewayMap()

        val expectedAuthHeader = "Basic bWVyY2hhbnQuTUVSQ0hBTlRfSUQ6c29tZXNlc3Npb24="

        gateway.initiateAuthentication(sessionId, orderId, txnId, apiVersion, payload, mock())

        // capture request
        val acRequest : KArgumentCaptor<GatewayRequest> = argumentCaptor()
        verify(mockComms).runGatewayRequest(acRequest.capture(), any())
        val request = acRequest.firstValue

        assertTrue(request.headers.containsKey("Authorization"))
        assertEquals(expectedAuthHeader, request.headers["Authorization"])
    }
}

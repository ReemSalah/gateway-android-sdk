package com.mastercard.gateway.android.sdk

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.Spy
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class GatewayCommsTest {

    @Spy
    private lateinit var comms: GatewayComms

    @Mock
    private lateinit var mockCallback: GatewayCallback

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
    }

    @Test
    fun testHandleCallbackMessageCallsOnErrorWithThrowableArg() {
        val arg = Exception("Some exception")

        comms.handleCallbackMessage(mockCallback, arg)

        verify(mockCallback).onError(arg)
    }

    @Test
    fun testHandleCallbackMessageCallsSuccessWithNonThrowableArg() {
        val arg: GatewayMap = mock()

        comms.handleCallbackMessage(mockCallback, arg)

        verify(mockCallback).onSuccess(arg)
    }

    @Test
    fun testCreateSslKeystoreContainsInternalCertificate() {
        whenever(comms.readCertificate(GatewayComms.INTERMEDIATE_CA)).thenReturn(mock())

        val keyStore = comms.createSslKeyStore()

        Assert.assertTrue(keyStore.containsAlias("gateway.mastercard.com"))
    }

    @Test
    fun testReadingInternalCertificateWorksAsExpected() {
        val certificate = comms.readCertificate(GatewayComms.INTERMEDIATE_CA)
        val expectedSerialNo = "1372807406"

        Assert.assertNotNull(certificate)
        Assert.assertEquals(expectedSerialNo, certificate.serialNumber.toString())
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
        whenever(comms.createSslContext()).thenReturn(context)

        val c = comms.createHttpsUrlConnection(request)

        Assert.assertEquals(request.url, c.url.toString())
        Assert.assertEquals(socketFactory, c.sslSocketFactory)
        assertEquals(GatewayComms.CONNECTION_TIMEOUT.toLong(), c.connectTimeout.toLong())
        assertEquals(GatewayComms.READ_TIMEOUT.toLong(), c.readTimeout.toLong())
        Assert.assertEquals("PUT", c.requestMethod)
        assertEquals(GatewayComms.USER_AGENT, c.getRequestProperty("User-Agent"))
        Assert.assertEquals("application/json", c.getRequestProperty("Content-Type"))
        Assert.assertTrue(c.doOutput)
    }

    @Test
    fun testIsStatusOkWorksAsIntended() {
        val tooLow = 199
        val tooHigh = 300
        val justRight = 200

        Assert.assertFalse(comms.isStatusCodeOk(tooLow))
        Assert.assertFalse(comms.isStatusCodeOk(tooHigh))
        Assert.assertTrue(comms.isStatusCodeOk(justRight))
    }
}
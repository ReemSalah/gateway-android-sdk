package com.mastercard.gateway.android.sdk

import android.app.Activity
import android.content.Intent
import android.net.Uri
import com.nhaarman.mockitokotlin2.*

import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue


@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class)
class Gateway3DSecureActivityTest {

    lateinit var activity: Gateway3DSecureActivity

    @Before
    @Throws(Exception::class)
    fun setUp() {
        val activityController = spy(Robolectric.buildActivity(Gateway3DSecureActivity::class.java))

        activity = spy(activityController.get())
        doNothing().whenever(activity).onBackPressed()
    }

    @Test
    @Throws(Exception::class)
    fun testInitCallsBackPressIfHtmlMissing() {
        whenever(activity.extraHtml).thenReturn(null)

        activity.init()

        verify(activity).onBackPressed()
    }

    @Test
    @Throws(Exception::class)
    fun testInitSetsDefaultTitleIfExtraTitleMissing() {
        val html = "<html></html>"
        val defaultTitle = "default title"
        val extraTitle: String? = null

        whenever(activity.extraHtml).thenReturn(html)
        whenever(activity.defaultTitle).thenReturn(defaultTitle)
        whenever(activity.extraTitle).thenReturn(extraTitle)
        doNothing().whenever(activity).setToolbarTitle(any())
        doNothing().whenever(activity).setWebViewHtml(any())

        activity.init()

        verify(activity).setToolbarTitle(defaultTitle)
    }

    @Test
    @Throws(Exception::class)
    fun testInitWorksAsExpected() {
        val html = "<html></html>"
        val defaultTitle = "default title"
        val extraTitle = "extra title"

        whenever(activity.extraHtml).thenReturn(html)
        whenever(activity.defaultTitle).thenReturn(defaultTitle)
        whenever(activity.extraTitle).thenReturn(extraTitle)
        doNothing().whenever(activity).setToolbarTitle(any())
        doNothing().whenever(activity).setWebViewHtml(any())

        activity.init()

        verify(activity).setWebViewHtml(html)
        verify(activity).setToolbarTitle(extraTitle)
    }

    @Test
    fun testGetExtraTitleReturnsNullIfMissing() {
        val testIntent = Intent()
        whenever(activity.intent).thenReturn(testIntent)

        val title = activity.extraTitle

        assertNull(title)
    }

    @Test
    fun testGetExtraTitleReturnsValueIfExists() {
        val expectedTitle = "My Title"
        val testIntent = Intent()
        testIntent.putExtra(Gateway3DSecureActivity.EXTRA_TITLE, expectedTitle)
        whenever(activity.intent).thenReturn(testIntent)

        val title = activity.extraTitle

        assertEquals(expectedTitle, title)
    }

    @Test
    fun testGetExtraHtmlReturnsNullIfMissing() {
        val testIntent = Intent()
        whenever(activity.intent).thenReturn(testIntent)

        val html = activity.extraHtml

        assertNull(html)
    }

    @Test
    fun testGetExtraHtmlReturnsValueIfExists() {
        val expectedHtml = "<html></html>"
        val testIntent = Intent()
        testIntent.putExtra(Gateway3DSecureActivity.EXTRA_HTML, expectedHtml)
        whenever(activity.intent).thenReturn(testIntent)

        val html = activity.extraHtml

        assertEquals(expectedHtml, html)
    }

    @Test
    fun testIntentToEmailWorksAsExpected() {
        val testUri: Uri = mock()
        val testIntent = Intent()

        activity.intentToEmail(testUri, testIntent)

        val flags = testIntent.flags

        assertNotEquals(0, (flags and Intent.FLAG_ACTIVITY_NEW_TASK).toLong())
        assertEquals(testUri, testIntent.data)
        verify(activity).startActivity(testIntent)
    }

    @Test
    fun testCompleteWorksAsExpected() {
        val testAcsResult = "test result"
        val testIntent = Intent()

        activity.complete(testAcsResult, testIntent)

        assertTrue(testIntent.hasExtra(Gateway3DSecureActivity.EXTRA_ACS_RESULT))
        assertEquals(testAcsResult, testIntent.getStringExtra(Gateway3DSecureActivity.EXTRA_ACS_RESULT))
        verify(activity).setResult(Activity.RESULT_OK, testIntent)
        verify(activity).finish()
    }

    @Test
    fun testWebViewUrlChangesCallCompleteOnCorrectScheme() {
        val testUri = Uri.parse("gatewaysdk://3dsecure?irrelevant1=something&acsResult={}&irrelevant2=something")
        val testResult = "acs result"

        whenever(activity.getACSResultFromUri(testUri)).thenReturn(testResult)

        activity.webViewUrlChanges(testUri)

        verify(activity).complete(testResult)
    }

    @Test
    fun testWebViewUrlChangesCallIntentToEmailOnMailtoScheme() {
        val testUri = Uri.parse("mailto://something")

        doNothing().whenever(activity).intentToEmail(testUri)

        activity.webViewUrlChanges(testUri)

        verify(activity).intentToEmail(testUri)
    }

    @Test
    fun testWebViewUrlChangesPassesThruUriIfNoSchemeMatch() {
        val testUri = Uri.parse("https://www.google.com")

        doNothing().whenever(activity).loadWebViewUrl(testUri)

        activity.webViewUrlChanges(testUri)

        verify(activity).loadWebViewUrl(testUri)
    }

    @Test
    fun testGetAcsResultFromUriWorksAsExpected() {
        val testUri = Uri.parse("gatewaysdk://3dsecure?irrelevant1=something&acsResult={}&irrelevant2=something")

        val result = activity.getACSResultFromUri(testUri)

        assertEquals("{}", result)
    }
}

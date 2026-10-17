package com.tim.futureai.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression tests for the chat bubble width rule introduced in Revise2. */
class ChatLayoutTest {
    @Test fun bubbleWidthNeverExceeds330dp() {
        assertEquals(330f, chatBubbleMaxWidthDp(1080f))
        assertEquals(330f, chatBubbleMaxWidthDp(720f))
    }

    @Test fun narrowScreensGetFractionalWidth() {
        assertEquals(258f, chatBubbleMaxWidthDp(300f), 0.01f)
        assertEquals(172f, chatBubbleMaxWidthDp(200f), 0.01f)
    }

    @Test fun widthStaysPositiveOnVerySmallScreens() {
        assertTrue(chatBubbleMaxWidthDp(50f) > 0f)
    }
}

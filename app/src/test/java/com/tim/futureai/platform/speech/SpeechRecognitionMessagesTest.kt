package com.tim.futureai.platform.speech

import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechRecognitionMessagesTest {
    @Test fun permissionFailureExplainsHowToRecover() {
        assertTrue(SpeechRecognitionMessages.failure(SpeechRecognitionMessages.ERROR_INSUFFICIENT_PERMISSIONS).contains("权限被拒绝"))
    }

    @Test fun unsupportedOrEmptyResultOffersTextInputFallback() {
        assertTrue(SpeechRecognitionMessages.failure(null).contains("直接输入文字"))
    }
}

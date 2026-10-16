package com.tim.futureai.feature.chat

/** Non-secret display metadata passed from settings to the chat model selector. */
data class ChatModelChoice(val id: String, val displayName: String, val supportsImages: Boolean = false)

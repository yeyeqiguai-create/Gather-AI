package com.tim.futureai.domain.chat

data class SystemInstructionTemplate(
    val id: String,
    val title: String,
    val instruction: String,
    val category: String = "通用",
    val isFavorite: Boolean = false,
)

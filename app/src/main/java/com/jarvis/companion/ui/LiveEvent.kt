package com.jarvis.companion.ui

enum class EventType {
    USER,
    JARVIS,
    ACTION,
    SYSTEM
}

data class LiveEvent(
    val type: EventType,
    val title: String,
    val content: String,
    val time: String
)

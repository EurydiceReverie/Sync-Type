package com.lanremotetype.model

enum class TypingMode(val value: String, val label: String, val description: String) {
    INSTANT("instant", "Instant", "Paste all at once"),
    FAST("fast", "Fast", "80-120 WPM"),
    NORMAL("normal", "Normal", "30-70 WPM"),
    HUMAN("human", "Human", "20-55 WPM realistic");

    companion object {
        fun fromValue(value: String): TypingMode {
            return entries.find { it.value == value } ?: NORMAL
        }
    }
}

enum class Priority(val value: String, val label: String) {
    LOW("low", "Low"),
    NORMAL("normal", "Normal"),
    HIGH("high", "High"),
    URGENT("urgent", "Urgent");

    companion object {
        fun fromValue(value: String): Priority {
            return entries.find { it.value == value } ?: NORMAL
        }
    }
}

enum class TypingAction(val value: String) {
    PAUSE("pause"),
    RESUME("resume"),
    ABORT("abort");

    companion object {
        fun fromValue(value: String): TypingAction? {
            return entries.find { it.value == value }
        }
    }
}

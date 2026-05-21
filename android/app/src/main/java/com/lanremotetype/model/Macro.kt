package com.lanremotetype.model

data class Macro(
    val id: String,
    val name: String,
    val text: String,
    val mode: String = "instant",
    val icon: String = "keyboard"
)

object DefaultMacros {
    val defaults = listOf(
        Macro("macro_1", "Enter", "\n", "instant", "keyboard_return"),
        Macro("macro_2", "Tab", "\t", "instant", "keyboard_tab"),
        Macro("macro_3", "Ctrl+C", "", "instant", "content_copy"),
        Macro("macro_4", "Ctrl+V", "", "instant", "content_paste"),
        Macro("macro_5", "Ctrl+Z", "", "instant", "undo"),
        Macro("macro_6", "Escape", "", "instant", "close"),
    )
}

package com.pocketproof.engine

/** The language of text produced outside the interface: rendered statements, hints and tool names. */
object Wording {
    @Volatile var german = false

    fun tr(en: String, de: String) = if (german && de.isNotEmpty()) de else en
}

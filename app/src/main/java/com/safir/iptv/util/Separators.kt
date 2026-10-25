package com.safir.iptv.util

/**
 * Viele Anbieter setzen Trennzeilen als „Kanal“ in ihre Listen, etwa
 * „##### DE ALLGEMEINES #####“. Dahinter steckt kein Stream: in der Liste sind
 * sie Überschriften, beim Umschalten werden sie übersprungen.
 */
fun isSeparatorName(name: String): Boolean = SeparatorPattern.matches(name.trim())

private val SeparatorPattern = Regex("""^[#=*~\-_|]{3,}.*[#=*~\-_|]{3,}$|^[#=*~]{3,}.*""")

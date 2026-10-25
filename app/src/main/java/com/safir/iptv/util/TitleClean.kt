package com.safir.iptv.util

/**
 * Anbieter stellen ihren Titeln Kürzel voran: „NF - The Irishman (2019)“,
 * „[DE] Tatort“, „|TR| Kurtlar Vadisi“. Auf einem Cover ist das nur Lärm — der
 * Titel selbst ist, was man sucht.
 */
fun cleanVodTitle(raw: String): String =
    raw.replace(TitlePrefix, "").trim().ifBlank { raw.trim() }

private val TitlePrefix =
    Regex("""^(?:\s*(?:\[[^\]]{1,12}]|\|[^|]{1,12}\||[A-Z0-9]{2,4}\s*[-|:]\s))+""")

/** „VOD - NETFLIX [MULTISUB]“ → „NETFLIX [MULTISUB]“, „SRS - DISNEY+“ → „DISNEY+“. */
fun cleanCategoryName(raw: String): String =
    raw.replace(CategoryPrefix, "").trim().ifBlank { raw.trim() }

private val CategoryPrefix =
    Regex("""^\s*(?:VOD|SRS|SERIES|SERIEN|MOVIES?|FILME?|FILMS?)\s*[-|:]\s*""", RegexOption.IGNORE_CASE)

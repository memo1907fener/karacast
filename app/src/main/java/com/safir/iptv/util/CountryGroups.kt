package com.safir.iptv.util

import com.safir.iptv.domain.model.Category
import java.util.Locale

/** Kennung einer Länder-Gruppe in der Gruppenliste: „cty:DE“. */
const val COUNTRY_PREFIX = "cty:"

/**
 * Fasst die Kategorien eines Anbieters nach Land zusammen.
 *
 * Anbieter schreiben das Land vor den Namen — „[DE] SPORT“, „DE | FILME“,
 * „|TR| ULUSAL“, „TR - HABER“, „GER: KINDER“. Aus zehn Kategorien „[DE] …“
 * wird in der Gruppenliste ein Eintrag „Deutschland“; darin stehen alle
 * Kanäle hintereinander, mit dem Rest des Kategorienamens als Überschrift
 * („SPORT“, „FILME“ …). Was kein erkennbares Land vorne trägt, bleibt, wie es ist.
 */
object CountryGroups {

    private val Prefix = Regex(
        """^\s*(?:\[\s*([A-Za-z]{2,3})\s*]|\|\s*([A-Za-z]{2,3})\s*\||\(\s*([A-Za-z]{2,3})\s*\)|([A-Z]{2,3})\s*[|:\-–]|([A-Z]{2})\s+(?=\S))\s*[|:\-–]?\s*"""
    )

    private val iso2: Set<String> = Locale.getISOCountries().toSet()

    private val iso3: Map<String, String> = Locale.getISOCountries().mapNotNull { code ->
        runCatching { Locale("", code).isO3Country.uppercase() to code }.getOrNull()
    }.toMap()

    /**
     * Kürzel, die bei IPTV-Anbietern etwas anderes heißen als in ISO 3166 —
     * oder gar nicht dort stehen. „AR“ ist bei ihnen fast immer Arabisch, nicht
     * Argentinien; „UK“ ist Großbritannien.
     */
    private val aliases = mapOf(
        "UK" to "GB",
        "EN" to "GB",
        "GER" to "DE",
        "TUR" to "TR",
        "SUI" to "CH",
        "NED" to "NL",
        "POR" to "PT",
        "KU" to "KU",
        "KUR" to "KU",
        "AR" to "AR",
        "ARA" to "AR",
        "EXYU" to "EXYU",
        "EX-YU" to "EXYU"
    )

    /** Diese Kürzel sehen aus wie ein Land, meinen bei Anbietern aber etwas anderes. */
    private val notCountries = setOf("VIP", "VOD", "PPV", "TV", "HD", "FHD", "UHD", "SD", "NEW", "ALL", "XXX", "MUSIC")

    /** Das Land einer Kategorie („DE“) oder null, wenn ihr Name keins nennt. */
    fun codeOf(name: String): String? {
        val match = Prefix.find(name) ?: return null
        val raw = match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }?.uppercase() ?: return null
        if (raw in notCountries) return null
        // Nichts als das Kürzel: dann ist es kein Präfix, sondern der ganze Name.
        if (name.length <= match.value.length) return null
        aliases[raw]?.let { return it }
        return when {
            raw.length == 2 && raw in iso2 -> raw
            raw.length == 3 -> iso3[raw]
            else -> null
        }
    }

    /** „[DE] SPORT“ → „SPORT“: der Teil, der in der Länder-Gruppe als Überschrift steht. */
    fun subName(name: String): String {
        val match = Prefix.find(name) ?: return name
        return name.substring(match.value.length).trim().ifBlank { name.trim() }
    }

    /** „DE“ → „Deutschland“ in der Sprache der App. */
    fun displayName(code: String, languageTag: String): String {
        val language = Locale.forLanguageTag(languageTag)
        return when (code) {
            "AR" -> Locale("ar").getDisplayLanguage(language)
            "KU" -> Locale("ku").getDisplayLanguage(language)
            "EXYU" -> "Ex-YU"
            else -> Locale("", code).getDisplayCountry(language)
        }.replaceFirstChar { it.titlecase(language) }.ifBlank { code }
    }

    /**
     * Die Gruppenliste mit Ländern statt Einzelkategorien. Jedes Land steht dort,
     * wo seine erste Kategorie stand, damit die Sortierung des Nutzers gilt.
     * Eigene Gruppen ([skip]) bleiben unangetastet.
     */
    fun merge(categories: List<Category>, languageTag: String, skip: (Category) -> Boolean): List<Category> {
        val result = mutableListOf<Category>()
        val indexByCode = HashMap<String, Int>()
        for (category in categories) {
            val code = if (skip(category)) null else codeOf(category.name)
            if (code == null) {
                result += category
                continue
            }
            val index = indexByCode[code]
            if (index == null) {
                indexByCode[code] = result.size
                result += Category(COUNTRY_PREFIX + code, displayName(code, languageTag), category.channelCount)
            } else {
                val existing = result[index]
                result[index] = existing.copy(channelCount = existing.channelCount + category.channelCount)
            }
        }
        return result
    }
}

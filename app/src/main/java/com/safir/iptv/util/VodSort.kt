package com.safir.iptv.util

import com.safir.iptv.domain.model.Movie
import com.safir.iptv.domain.model.Series

/**
 * Wie die Cover einer Kategorie geordnet sind.
 *
 * [DEFAULT] ist die Reihenfolge des Anbieters — manche pflegen sie von Hand, und
 * wer nichts umstellt, soll sie behalten. Die übrigen sind das, was man bei
 * tausend Filmen in einer Kategorie wirklich braucht: das Neue zuerst, alphabetisch,
 * die besten, die jüngsten.
 */
enum class VodSort { DEFAULT, NEWEST, AZ, RATING, YEAR;

    /** Der nächste Modus — ein Knopf schaltet reihum, das geht mit jeder Fernbedienung. */
    fun next(): VodSort = values()[(ordinal + 1) % values().size]

    companion object {
        fun of(name: String?): VodSort = values().firstOrNull { it.name == name } ?: DEFAULT
    }
}

private val YearInName = Regex("""(?:^|[\s(\[\-|])((?:19|20)\d{2})(?:[\s)\]\-|]|$)""")

/** „Dune (2021)“, „Dune 2021“, „[2021] Dune“ → 2021. */
fun yearOf(name: String, released: String? = null): Int? {
    released?.trim()?.take(4)?.toIntOrNull()?.takeIf { it in 1900..2100 }?.let { return it }
    return YearInName.findAll(name).lastOrNull()?.groupValues?.get(1)?.toIntOrNull()
}

/** „7.5“, „7,5“, „8/10“ → 7.5. Ohne Wert: null, damit er ans Ende rutscht. */
fun ratingOf(rating: String?): Double? =
    rating?.trim()?.substringBefore('/')?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it > 0.0 }

/**
 * Sortiert nach [mode]. Was keinen Wert hat (kein Datum, keine Bewertung), steht
 * hinten — in der Reihenfolge des Anbieters, nicht durcheinander.
 */
fun <T> List<T>.sortedFor(
    mode: VodSort,
    title: (T) -> String,
    added: (T) -> Long?,
    rating: (T) -> String?,
    year: (T) -> Int?
): List<T> = when (mode) {
    VodSort.DEFAULT -> this
    VodSort.NEWEST -> sortedWith(compareBy<T> { added(it) == null }.thenByDescending { added(it) ?: 0L })
    VodSort.AZ -> sortedBy { cleanVodTitle(title(it)).lowercase() }
    VodSort.RATING -> sortedWith(
        compareBy<T> { ratingOf(rating(it)) == null }.thenByDescending { ratingOf(rating(it)) ?: 0.0 }
    )
    VodSort.YEAR -> sortedWith(compareBy<T> { year(it) == null }.thenByDescending { year(it) ?: 0 })
}

fun List<Movie>.sortedFor(mode: VodSort): List<Movie> =
    sortedFor(mode, { it.name }, { it.addedMs }, { it.rating }, { yearOf(it.name) })

fun List<Series>.sortedSeriesFor(mode: VodSort): List<Series> =
    sortedFor(mode, { it.name }, { it.addedMs }, { it.rating }, { yearOf(it.name, it.released) })

package com.craftworks.music.data.providers.lyrics

import java.text.Normalizer
import kotlin.math.abs

/**
 * Comparisons shared by the external lyrics sources.
 *
 * Everything here treats missing data as "cannot be judged" rather than "no match": a candidate
 * must never be rejected because it omitted a field, only because it reported something that
 * disagrees with the playing track.
 */

/**
 * Lower case, accents folded and everything that is not a letter or a digit removed, so
 * `Beyoncé` matches `Beyonce` and `Next Ex-Girlfriend` matches `Next Ex-girlfriend`.
 */
internal fun String?.normalizedForComparison(): String {
    val folded = Normalizer.normalize(this ?: "", Normalizer.Form.NFD)
    return folded
        .lowercase()
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), "")
}

/**
 * Containment in either direction rather than equality, because one side is often the shortened
 * form of the other (`Ohio` against `Ohio (Come Back To Texas)`).
 */
internal fun String?.isSameAs(other: String?): Boolean {
    val wanted = normalizedForComparison()
    val found = other.normalizedForComparison()
    return wanted.isNotEmpty() && found.isNotEmpty() && (found.contains(wanted) || wanted.contains(found))
}

/**
 * A value worth putting in a query string: trimmed, or null when it is blank or the literal
 * `"null"` that `CharSequence?.toString()` produces for a null receiver. Null values are dropped
 * by Ktor rather than sent as empty parameters.
 */
internal fun String?.asQueryValue(): String? =
    this?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

/**
 * True when a candidate recording is the same one as the requested track.
 *
 * Unknown on either side passes: the filter discards known mismatches, it is not an admission
 * test. Comparison is in milliseconds so that a tolerance of 0 or 1 second is usable - the
 * requested duration is only known to the millisecond and rounding it to seconds first would
 * turn a half-second difference into a whole one.
 */
internal fun durationWithinTolerance(
    requestedMs: Long?,
    candidateSeconds: Double?,
    toleranceSeconds: Int
): Boolean {
    if (requestedMs == null || requestedMs <= 0) return true
    if (candidateSeconds == null) return true
    return abs(candidateSeconds * 1000.0 - requestedMs) <= toleranceSeconds * 1000.0
}

/** How far a candidate is from the playing track, for ranking the ones that passed the tolerance. */
internal fun durationDistanceMs(requestedMs: Long?, candidateSeconds: Double?): Double {
    if (requestedMs == null || requestedMs <= 0) return 0.0
    if (candidateSeconds == null) return Double.MAX_VALUE
    return abs(candidateSeconds * 1000.0 - requestedMs)
}

/** The playing track's length, or null when the provider does not report one (`0` is not a length). */
internal fun Long?.asKnownDurationMs(): Long? = this?.takeIf { it > 0 }

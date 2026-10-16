package com.sledje.agentpro.core.data

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/*
 * Parsing of the text values Room stores for PostgREST columns. Rows are cached as they arrive
 * (`timestamptz` as ISO text with an offset, `date` as YYYY-MM-DD, `numeric` as a JSON number), so a
 * malformed value degrades to null instead of crashing a screen.
 */

/** A `timestamptz` value (e.g. `2026-10-16T04:30:00.123456+00:00`), or null. */
fun parseInstantOrNull(text: String?): Instant? = text?.let { runCatching { Instant.parse(it) }.getOrNull() }

/** A `date` value (`2026-10-16`), or null. */
fun parseDateOrNull(text: String?): LocalDate? = text?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }

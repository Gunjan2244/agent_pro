package com.sledje.agentpro.core.format

import androidx.compose.runtime.Composable
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.month_short_1
import com.sledje.agentpro.resources.month_short_10
import com.sledje.agentpro.resources.month_short_11
import com.sledje.agentpro.resources.month_short_12
import com.sledje.agentpro.resources.month_short_2
import com.sledje.agentpro.resources.month_short_3
import com.sledje.agentpro.resources.month_short_4
import com.sledje.agentpro.resources.month_short_5
import com.sledje.agentpro.resources.month_short_6
import com.sledje.agentpro.resources.month_short_7
import com.sledje.agentpro.resources.month_short_8
import com.sledje.agentpro.resources.month_short_9
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/** Translated short month names (Jan…Dec / जन॰…दिस॰) for [Dates.format]. */
@Composable
fun rememberMonthNames(): List<String> = listOf(
    stringResource(Res.string.month_short_1),
    stringResource(Res.string.month_short_2),
    stringResource(Res.string.month_short_3),
    stringResource(Res.string.month_short_4),
    stringResource(Res.string.month_short_5),
    stringResource(Res.string.month_short_6),
    stringResource(Res.string.month_short_7),
    stringResource(Res.string.month_short_8),
    stringResource(Res.string.month_short_9),
    stringResource(Res.string.month_short_10),
    stringResource(Res.string.month_short_11),
    stringResource(Res.string.month_short_12),
)

/** "15 Oct 2026" in the app language. */
@Composable
fun formatDate(date: LocalDate): String = Dates.format(date, rememberMonthNames())

package com.sledje.agentpro.core.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.sledje.agentpro.core.designsystem.AgentProTheme
import com.sledje.agentpro.core.designsystem.AppShapes
import com.sledje.agentpro.core.designsystem.Spacing

/**
 * A modal bottom sheet (elevated; radius 16 on the top corners; docs/DESIGN_SYSTEM.md, Surfaces) with a
 * title and the sheet's content, e.g. the Home setup checklist.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = AppShapes.sheet,
        containerColor = AgentProTheme.colors.elevatedSurface,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = Spacing.l)) {
            Text(
                title,
                style = AgentProTheme.text.title,
                modifier = Modifier.padding(horizontal = Spacing.m).padding(bottom = Spacing.s).semantics { heading() },
            )
            content()
        }
    }
}

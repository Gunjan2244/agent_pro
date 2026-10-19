package com.sledje.agentpro.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.sledje.agentpro.core.designsystem.Motion

/**
 * A NavHost for a linear flow (sign-in, password recovery, onboarding): shared-axis X forward and
 * back (docs/DESIGN_SYSTEM.md, Motion). The pop transitions also drive the predictive back gesture.
 * The main shell sets its own, because it adds fade-through between tabs.
 */
@Composable
fun SharedAxisNavHost(
    navController: NavHostController,
    startDestination: Any,
    modifier: Modifier = Modifier,
    builder: NavGraphBuilder.() -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = { Motion.sharedAxisXEnter(forward = true) },
        exitTransition = { Motion.sharedAxisXExit(forward = true) },
        popEnterTransition = { Motion.sharedAxisXEnter(forward = false) },
        popExitTransition = { Motion.sharedAxisXExit(forward = false) },
        builder = builder,
    )
}

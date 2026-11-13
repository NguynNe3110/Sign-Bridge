package com.uzuu.signbridge.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.uzuu.signbridge.core.di.AppContainer
import com.uzuu.signbridge.domain.model.ContextType
import com.uzuu.signbridge.feature.chip.ChipManageScreen
import com.uzuu.signbridge.feature.chip.ChipManageViewModel
import com.uzuu.signbridge.feature.context.ContextSelectScreen
import com.uzuu.signbridge.feature.conversation.ConversationScreen
import com.uzuu.signbridge.feature.conversation.ConversationViewModel

@Composable
fun AppNavHost(
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val contextArgument = listOf(navArgument(Routes.ARG_CONTEXT) { type = NavType.StringType })

    NavHost(
        navController = navController,
        startDestination = Routes.CONTEXT_SELECT,
        modifier = modifier,
    ) {
        composable(Routes.CONTEXT_SELECT) {
            ContextSelectScreen(
                onContextSelected = { navController.navigate(Routes.conversation(it)) },
            )
        }

        composable(Routes.CONVERSATION, arguments = contextArgument) { entry ->
            val context = entry.contextArg()
            val viewModel: ConversationViewModel =
                viewModel(factory = container.conversationViewModelFactory(context))
            ConversationScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onManageChips = { navController.navigate(Routes.chipManage(it)) },
                cameraPreviewController = container.cameraPreview,
            )
        }

        composable(Routes.CHIP_MANAGE, arguments = contextArgument) { entry ->
            val context = entry.contextArg()
            val viewModel: ChipManageViewModel =
                viewModel(factory = container.chipManageViewModelFactory(context))
            ChipManageScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

private fun NavBackStackEntry.contextArg(): ContextType {
    val raw = arguments?.getString(Routes.ARG_CONTEXT)
    return ContextType.entries.firstOrNull { it.name == raw } ?: ContextType.CUSTOM
}

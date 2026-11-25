package org.maqraatalnoor

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.maqraatalnoor.data.Role
import org.maqraatalnoor.ui.components.MaqraatalNoorPattern
import org.maqraatalnoor.ui.navigation.Routes
import org.maqraatalnoor.ui.screens.BootScreen
import org.maqraatalnoor.ui.screens.AdminScreen
import org.maqraatalnoor.ui.screens.CallScreen
import org.maqraatalnoor.ui.screens.ChatHubScreen
import org.maqraatalnoor.ui.screens.HomeScreen
import org.maqraatalnoor.ui.screens.JoinHalqaScreen
import org.maqraatalnoor.ui.screens.PendingApprovalScreen
import org.maqraatalnoor.ui.screens.GroupsScreen
import org.maqraatalnoor.ui.screens.LiveHalqaScreen
import org.maqraatalnoor.ui.screens.LoginScreen
import org.maqraatalnoor.ui.screens.MushafScreen
import org.maqraatalnoor.ui.screens.SettingsScreen
import org.maqraatalnoor.ui.screens.TeacherControlScreen
import org.maqraatalnoor.ui.theme.DeepGreen
import org.maqraatalnoor.ui.theme.DeepGreenDark
import org.maqraatalnoor.ui.theme.MaqraatalNoorBackground
import org.maqraatalnoor.ui.theme.MaqraatalNoorTheme
import org.maqraatalnoor.ui.theme.Paper
import org.maqraatalnoor.ui.viewmodels.AuthState
import org.maqraatalnoor.ui.viewmodels.SessionViewModel
import org.maqraatalnoor.ui.viewmodels.SettingsViewModel

/**
 * Maqraatal-Noor — مقرأة النور.
 *
 * الدخول حقيقي عبر Supabase (بريد + كلمة مرور، بدعوة فقط)؛ وبعد الدخول
 * تُبنى الواجهة على بيانات الحلقة الفعلية.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(
                AndroidColor.TRANSPARENT,
                AndroidColor.TRANSPARENT,
            ),
        )
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val uiFontFamily by settingsViewModel.uiFontFamily.collectAsState()
            val sessionViewModel: SessionViewModel = viewModel()
            val auth by sessionViewModel.state.collectAsState()
            val busy by sessionViewModel.busy.collectAsState()
            val error by sessionViewModel.error.collectAsState()
            val joinStatus by sessionViewModel.joinStatus.collectAsState()
            val joinBusy by sessionViewModel.joinBusy.collectAsState()

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaqraatalNoorTheme(uiFamily = uiFontFamily) {
                    when (val state = auth) {
                        AuthState.Loading -> BootScreen()

                        AuthState.SignedOut -> LoginScreen(
                            busy = busy,
                            error = error,
                            joinStatus = joinStatus,
                            joinBusy = joinBusy,
                            onSignIn = sessionViewModel::signIn,
                            onSubmitJoin = sessionViewModel::submitJoin,
                        )

                        is AuthState.SignedIn -> {
                            if (state.membership == null) {
                                JoinHalqaScreen(
                                    name = state.profile.name,
                                    busy = busy,
                                    error = error,
                                    onRedeem = sessionViewModel::redeem,
                                    onSignOut = sessionViewModel::signOut,
                                )
                            } else if (state.membership.status != "active") {
                                PendingApprovalScreen(
                                    name = state.profile.name,
                                    banned = state.membership.status == "banned",
                                    busy = busy,
                                    onRefresh = sessionViewModel::reload,
                                    onSignOut = sessionViewModel::signOut,
                                )
                            } else {
                                MainApp(
                                    state = state,
                                    onSignOut = sessionViewModel::signOut,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainApp(
    state: AuthState.SignedIn,
    onSignOut: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val role = if (state.isStaff) Role.TEACHER else Role.STUDENT
    BackHandler(enabled = currentRoute != Routes.HOME) {
        if (!navController.popBackStack()) navController.navigate(Routes.HOME)
    }

    val screenBackground = when (currentRoute) {
        Routes.LIVE -> DeepGreenDark
        Routes.MUSHAF -> Paper
        Routes.SPLASH -> DeepGreen
        else -> MaqraatalNoorBackground
    }

    Scaffold(
        containerColor = screenBackground,
        contentWindowInsets = WindowInsets.navigationBars,
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().background(screenBackground)) {
            if (currentRoute != Routes.LIVE && currentRoute != Routes.MUSHAF) {
                MaqraatalNoorPattern(Modifier.fillMaxSize())
            }
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier.padding(innerPadding),
            ) {
            composable(Routes.HOME) {
                HomeScreen(
                    role = role,
                    onNavigateChats = { navController.navigate(Routes.CHATS) },
                    onNavigateSettings = { navController.navigate(Routes.SETTINGS) },
                    onNavigateGroups = { navController.navigate(Routes.GROUPS) },
                    onNavigateAdmin = { navController.navigate(Routes.ADMIN) },
                    displayName = state.profile.name,
                    roleLabel = when (state.profile.role) { "supervisor" -> "المشرفة العامة"; "teacher" -> "معلمة"; else -> "طالبة" },
                    isStaff = state.isStaff,
                    isSupervisor = state.profile.role == "supervisor",
                    onNavigateMushaf = { navController.navigate(Routes.MUSHAF) },
                )
            }
            composable(
                Routes.LIVE,
                arguments = listOf(navArgument("classId") { type = NavType.StringType }),
            ) { entry ->
                LiveHalqaScreen(
                    classId = entry.arguments?.getString("classId") ?: "",
                    onBack = { navController.popBackStack() },
                    onOpenMushaf = { navController.navigate(Routes.MUSHAF) },
                    onJoinCall = { navController.navigate(Routes.call(false)) },
                )
            }
            composable(Routes.MUSHAF) {
                MushafScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.CHATS) {
                ChatHubScreen(
                    onBack = { navController.popBackStack() },
                    onStartCall = { conversation, video ->
                        navController.navigate(
                            Routes.call(
                                withVideo = video,
                                thread = if (conversation.isGroup) "halqa" else "private",
                                studentId = conversation.memberId,
                            ),
                        )
                    },
                    onOpenMushaf = { navController.navigate(Routes.MUSHAF) },
                )
            }
            composable(Routes.ADMIN) {
                AdminScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.GROUPS) {
                GroupsScreen(onBack = { navController.popBackStack() })
            }
            composable(
                Routes.CALL,
                arguments = listOf(
                    navArgument("withVideo") { type = NavType.BoolType; defaultValue = false },
                    navArgument("thread") { type = NavType.StringType; defaultValue = "halqa" },
                    navArgument("studentId") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                CallScreen(
                    startWithVideo = entry.arguments?.getBoolean("withVideo") == true,
                    thread = entry.arguments?.getString("thread") ?: "halqa",
                    studentId = entry.arguments?.getString("studentId").orEmpty(),
                    onLeave = { navController.popBackStack() },
                )
            }
            composable(
                Routes.TEACHER,
                arguments = listOf(navArgument("classId") { type = NavType.StringType }),
            ) { entry ->
                TeacherControlScreen(
                    classId = entry.arguments?.getString("classId") ?: "",
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    userName = state.profile.name,
                    roleLabel = when (state.profile.role) {
                        "supervisor" -> "المشرفة العامة"
                        "teacher" -> "معلمة"
                        else -> "طالبة"
                    },
                    onSignOut = onSignOut,
                    onBack = { navController.popBackStack() },
                )
            }
        }
            }
    }
}

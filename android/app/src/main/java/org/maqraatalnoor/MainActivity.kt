package org.maqraatalnoor

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.maqraatalnoor.data.Role
import org.maqraatalnoor.ui.components.BottomNavItem
import org.maqraatalnoor.ui.components.MaqraatalNoorBottomNav
import org.maqraatalnoor.ui.navigation.Routes
import org.maqraatalnoor.ui.screens.BootScreen
import org.maqraatalnoor.ui.screens.ClassesScreen
import org.maqraatalnoor.ui.screens.HomeScreen
import org.maqraatalnoor.ui.screens.JoinHalqaScreen
import org.maqraatalnoor.ui.screens.LiveHalqaScreen
import org.maqraatalnoor.ui.screens.LoginScreen
import org.maqraatalnoor.ui.screens.MushafScreen
import org.maqraatalnoor.ui.screens.PrivateSessionScreen
import org.maqraatalnoor.ui.screens.SettingsScreen
import org.maqraatalnoor.ui.screens.TeacherControlScreen
import org.maqraatalnoor.ui.theme.DeepGreen
import org.maqraatalnoor.ui.theme.DeepGreenDark
import org.maqraatalnoor.ui.theme.MaqraatalNoorBackground
import org.maqraatalnoor.ui.theme.MaqraatalNoorTheme
import org.maqraatalnoor.ui.theme.Paper
import org.maqraatalnoor.ui.theme.PlexFamily
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

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaqraatalNoorTheme(uiFamily = uiFontFamily ?: PlexFamily) {
                    when (val state = auth) {
                        AuthState.Loading -> BootScreen()

                        AuthState.SignedOut -> LoginScreen(
                            busy = busy,
                            error = error,
                            onSignIn = sessionViewModel::signIn,
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
    val showBottomNav = currentRoute == Routes.HOME ||
        currentRoute == Routes.CLASSES ||
        currentRoute == Routes.SETTINGS

    val screenBackground = when (currentRoute) {
        Routes.LIVE -> DeepGreenDark
        Routes.MUSHAF -> Paper
        Routes.SPLASH -> DeepGreen
        else -> MaqraatalNoorBackground
    }

    Scaffold(
        containerColor = screenBackground,
        contentWindowInsets = WindowInsets.navigationBars,
        bottomBar = {
            if (showBottomNav) {
                MaqraatalNoorBottomNav(
                    items = listOf(
                        BottomNavItem("الرئيسية", Routes.HOME),
                        BottomNavItem("حلقتي", Routes.CLASSES),
                        BottomNavItem("المصحف", Routes.MUSHAF),
                        BottomNavItem("حسابي", Routes.SETTINGS),
                    ),
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(Routes.HOME) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    role = role,
                    displayName = state.profile.name,
                    halqaName = state.halqa?.name ?: "الحلقة العامة",
                    teachersLabel = state.teachersLabel,
                    membersCount = state.students.size,
                    isStaff = state.isStaff,
                    onNavigateLive = { classId -> navController.navigate(Routes.live(classId)) },
                    onNavigateClasses = { navController.navigate(Routes.CLASSES) },
                    onNavigateMushaf = { navController.navigate(Routes.MUSHAF) },
                    onNavigatePrivate = { studentId -> navController.navigate(Routes.privateSession(studentId)) },
                    onNavigateTeacher = { classId -> navController.navigate(Routes.teacher(classId)) },
                )
            }
            composable(Routes.CLASSES) {
                ClassesScreen(
                    halqaName = state.halqa?.name ?: "الحلقة العامة",
                    teachersLabel = state.teachersLabel,
                    membersCount = state.students.size,
                    isStaff = state.isStaff,
                    onOpenLive = { classId -> navController.navigate(Routes.live(classId)) },
                    onOpenPrivate = { studentId -> navController.navigate(Routes.privateSession(studentId)) },
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
                )
            }
            composable(Routes.MUSHAF) {
                MushafScreen()
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
            composable(
                Routes.PRIVATE,
                arguments = listOf(navArgument("studentId") { type = NavType.StringType }),
            ) { entry ->
                PrivateSessionScreen(
                    studentId = entry.arguments?.getString("studentId") ?: "",
                    onOpenMushaf = { navController.navigate(Routes.MUSHAF) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    userName = state.profile.name,
                    roleLabel = when (state.profile.role) {
                        "supervisor" -> "مشرفة عامة"
                        "teacher" -> "معلمة"
                        else -> "طالبة"
                    },
                    onSignOut = onSignOut,
                )
            }
        }
    }
}

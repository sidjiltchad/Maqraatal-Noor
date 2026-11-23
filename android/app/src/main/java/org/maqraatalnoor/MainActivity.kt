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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import org.maqraatalnoor.ui.components.BottomNavItem
import org.maqraatalnoor.ui.components.MaqraatalNoorBottomNav
import org.maqraatalnoor.ui.navigation.Routes
import org.maqraatalnoor.ui.screens.ClassesScreen
import org.maqraatalnoor.ui.screens.HomeScreen
import org.maqraatalnoor.ui.screens.LiveHalqaScreen
import org.maqraatalnoor.ui.screens.MushafScreen
import org.maqraatalnoor.ui.screens.PrivateSessionScreen
import org.maqraatalnoor.ui.screens.SettingsScreen
import org.maqraatalnoor.ui.screens.SplashScreen
import org.maqraatalnoor.ui.screens.TeacherControlScreen
import org.maqraatalnoor.ui.theme.DeepGreen
import org.maqraatalnoor.ui.theme.DeepGreenDark
import org.maqraatalnoor.ui.theme.MaqraatalNoorBackground
import org.maqraatalnoor.ui.theme.MaqraatalNoorTheme
import org.maqraatalnoor.ui.theme.Paper
import org.maqraatalnoor.ui.theme.PlexFamily
import org.maqraatalnoor.ui.viewmodels.SettingsViewModel

/**
 * Maqraatal-Noor — مقرأة النور. Arabic only, RTL.
 *
 * Entry point: the splash screen chooses a role (student / teacher,
 * invitation only), then the eight design screens run over Compose
 * Navigation with local demo data. No server, no real call in this build.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge: the app paints under the status and navigation bars.
        // Every header is deep green, so the status bar keeps light icons and
        // a transparent scrim; the bottom nav is paper, so it keeps dark icons.
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
            var role by remember { mutableStateOf(Role.STUDENT) }

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaqraatalNoorTheme(uiFamily = uiFontFamily ?: PlexFamily) {
                    val navController = rememberNavController()
                    val backStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = backStackEntry?.destination?.route
                    val showBottomNav = currentRoute == Routes.HOME ||
                        currentRoute == Routes.CLASSES ||
                        currentRoute == Routes.SETTINGS

                    // Full-bleed background per screen, painted behind the
                    // system-bar insets so no empty band shows top or bottom.
                    val screenBackground = when (currentRoute) {
                        Routes.SPLASH -> DeepGreen
                        Routes.LIVE -> DeepGreenDark
                        Routes.MUSHAF -> Paper
                        Routes.TEACHER, Routes.PRIVATE -> MaqraatalNoorBackground
                        else -> MaqraatalNoorBackground
                    }

                    Scaffold(
                        containerColor = screenBackground,
                        // Top insets are handled by each screen header (drawn
                        // edge-to-edge); only the bottom system bar is padded
                        // here, and the bottom bar already covers it when shown.
                        contentWindowInsets = WindowInsets.navigationBars,
                        bottomBar = {
                            if (showBottomNav) {
                                MaqraatalNoorBottomNav(
                                    items = listOf(
                                        BottomNavItem("الرئيسية", Routes.HOME),
                                        BottomNavItem("فصولي", Routes.CLASSES),
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
                            startDestination = Routes.SPLASH,
                            modifier = Modifier.padding(innerPadding),
                        ) {
                            composable(Routes.SPLASH) {
                                SplashScreen(
                                    onEnterAs = { chosen ->
                                        role = chosen
                                        navController.navigate(Routes.HOME) {
                                            popUpTo(Routes.SPLASH) { inclusive = true }
                                        }
                                    },
                                )
                            }
                            composable(Routes.HOME) {
                                HomeScreen(
                                    role = role,
                                    onNavigateLive = { classId -> navController.navigate(Routes.live(classId)) },
                                    onNavigateClasses = { navController.navigate(Routes.CLASSES) },
                                    onNavigateMushaf = { navController.navigate(Routes.MUSHAF) },
                                    onNavigatePrivate = { studentId -> navController.navigate(Routes.privateSession(studentId)) },
                                    onNavigateTeacher = { classId -> navController.navigate(Routes.teacher(classId)) },
                                )
                            }
                            composable(Routes.CLASSES) {
                                ClassesScreen(
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
                                SettingsScreen()
                            }
                        }
                    }
                }
            }
        }
    }
}

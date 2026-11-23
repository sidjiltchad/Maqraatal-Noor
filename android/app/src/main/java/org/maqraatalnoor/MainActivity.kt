package org.maqraatalnoor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import org.maqraatalnoor.ui.theme.MaqraatalNoorTheme
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
                        currentRoute == Routes.MUSHAF ||
                        currentRoute == Routes.SETTINGS

                    Scaffold(
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

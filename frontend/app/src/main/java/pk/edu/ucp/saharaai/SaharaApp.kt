package pk.edu.ucp.saharaai

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import pk.edu.ucp.saharaai.ui.screens.*

@Composable
fun SaharaApp() {
    val navController = rememberNavController()
    var isEnglish by rememberSaveable { mutableStateOf(false) }

    var userFullName by rememberSaveable { mutableStateOf("Sahara User") }
    var userEmail by rememberSaveable { mutableStateOf("") }
    var userCallingName by rememberSaveable { mutableStateOf("User") }
    var isFromRegistration by rememberSaveable { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        NavHost(navController = navController, startDestination = "welcome") {

            composable("welcome") {
                WelcomeScreen(
                    onNavigateToLogin = { navController.navigate("login") },
                    onNavigateToRegister = { navController.navigate("register") },
                    onNavigateToSettings = { navController.navigate("welcome-settings") },
                    isEnglish = isEnglish
                )
            }
            composable("welcome-settings") {
                WelcomeSettingsScreen(
                    isEnglish = isEnglish,
                    onLanguageChange = { isEnglish = it },
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToNgo = { navController.navigate("ngo-dashboard") },
                    onNavigateToCounselor = { navController.navigate("counselor-dashboard") }
                )
            }
            composable("login") {
                LoginScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDashboard = { email ->
                        userEmail = email
                        userFullName = "Sahara User"
                        userCallingName = "User"
                        isFromRegistration = false
                        navController.navigate("dashboard") {
                            popUpTo("welcome") { inclusive = true }
                        }
                    },
                    onNavigateToRegister = { navController.navigate("register") },
                    onNavigateToForgotPassword = { navController.navigate("forgot-password") },
                    onNavigateToFaceRecognition = { navController.navigate("face-recognition") },
                    isEnglish = isEnglish
                )
            }
            composable("register") {
                RegisterScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToLogin = { navController.navigate("login") },
                    onNavigateToOnboarding = { fullName, email, callingName ->
                        userFullName = fullName
                        userEmail = email
                        userCallingName = callingName
                        isFromRegistration = true
                        navController.navigate("onboarding") {
                            popUpTo("welcome") { inclusive = true }
                        }
                    },
                    isEnglish = isEnglish
                )
            }
            composable("forgot-password") {
                ForgotPasswordScreen(
                    onNavigateBack = { navController.popBackStack() },
                    isEnglish = isEnglish
                )
            }
            composable("onboarding") {
                OnboardingScreen(
                    onNavigateToDashboard = {
                        navController.navigate("dashboard") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    },
                    isEnglish = isEnglish
                )
            }

            composable("dashboard") {
                DashboardScreen(
                    navController = navController,
                    isEnglish = isEnglish,
                    userName = userCallingName
                )
            }
            composable("assessment") {
                AssessmentScreen(
                    navController = navController,
                    onNavigateBack = { navController.popBackStack() },
                    isEnglish = isEnglish,
                    onComplete = { score ->
                        navController.navigate("dashboard") {
                            popUpTo("dashboard") { inclusive = true }
                        }
                    }
                )
            }
            composable("chat") {
                ChatScreen(
                    navController = navController,
                    onNavigateBack = { navController.popBackStack() },
                    isEnglish = isEnglish,
                    userName = userCallingName
                )
            }
            composable("recovery") {
                GameRecoveryScreen(
                    navController = navController,
                    onNavigateBack = { navController.popBackStack() },
                    isEnglish = isEnglish
                )
            }
            composable("profile") {
                ProfileScreen(
                    navController = navController,
                    onNavigateToSettings = { navController.navigate("settings") },
                    onNavigateToEmergency = { navController.navigate("emergency") },
                    onSignOut = {
                        navController.navigate("welcome") {
                            popUpTo(0)
                        }
                    },
                    isEnglish = isEnglish,
                    fullName = userFullName,
                    email = userEmail,
                    isFromRegistration = isFromRegistration
                )
            }

            composable("notifications") {
                NotificationsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    isEnglish = isEnglish) }

            composable("voice-analysis") {
                VoiceAnalysisScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToChat = { navController.navigate("chat") },
                    isEnglish = isEnglish
                )
            }
            composable("face-recognition") {
                FaceRecognitionScreen(
                    navController = navController,
                    isEnglish = isEnglish
                )
            }
            composable("meditation") {
                MeditationScreen(
                    navController = navController,
                    isEnglish = isEnglish
                )
            }
            composable("sleep-tracker") {
                SleepTrackerScreen(
                    navController = navController,
                    isEnglish = isEnglish
                )
            }
            composable("progress") {
                ProgressScreen(
                    navController = navController,
                    isEnglish = isEnglish
                )
            }
            composable("emergency") {
                EmergencyScreen(
                    navController = navController,
                    onNavigateBack = { navController.popBackStack() },
                    isEnglish = isEnglish
                )
            }
            composable("journal") {
                JournalScreen(
                    navController = navController,
                    isEnglish = isEnglish
                )
            }

            composable("counselors") {
                CounselorsScreen(
                    navController = navController,
                    isEnglish = isEnglish
                )
            }
            composable("connections") {
                ConnectionsScreen(
                    navController = navController,
                    onNavigateBack = { navController.popBackStack() },
                    isEnglish = isEnglish
                )
            }
            composable("community") {
                CommunityScreen(
                    navController = navController,
                    isEnglish = isEnglish
                )
            }


            composable("settings") {
                SettingsScreen(
                    navController = navController,
                    onNavigateBack = { navController.popBackStack() },
                    isEnglish = isEnglish,
                    userName = userCallingName,
                    onLanguageChange = { isEnglish = it }
                )
            }
            composable("ngo-dashboard") {
                NgoDashboardScreen(
                    navController = navController,
                    isEnglish = isEnglish
                )
            }
            composable("counselor-dashboard") {
                CounselorDashboardScreen(
                    navController = navController,
                    isEnglish = isEnglish
                )
            }
        }
    }
}
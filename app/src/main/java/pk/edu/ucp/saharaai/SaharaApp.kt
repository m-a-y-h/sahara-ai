package pk.edu.ucp.saharaai

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.messaging.messaging
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import pk.edu.ucp.saharaai.data.remote.RealtimeDBService
import pk.edu.ucp.saharaai.data.repository.GoogleCredentialAuth
import pk.edu.ucp.saharaai.data.repository.GoogleSignInOutcome
import pk.edu.ucp.saharaai.ui.screens.*
import pk.edu.ucp.saharaai.utils.AssessmentCache
import pk.edu.ucp.saharaai.utils.NotificationRouteStore
import pk.edu.ucp.saharaai.utils.callingName
import pk.edu.ucp.saharaai.utils.showAssessmentRequiredToast as showAssessmentToast
import pk.edu.ucp.saharaai.utils.showLocalizedToast
import pk.edu.ucp.saharaai.viewmodels.ChatViewModel

private const val PREFS_NAME              = "sahara_prefs"
private const val KEY_COUNSELOR_KEY       = "counselor_key"
private const val KEY_USER_NAME           = "user_calling_name"
private const val KEY_USER_EMAIL          = "user_email"
private const val KEY_USER_FULL_NAME      = "user_full_name"
const val KEY_ASSESSMENT_SCORE            = "assessment_score"
const val KEY_ASSESSMENT_TIMESTAMP        = "assessment_timestamp_ms"
const val KEY_ASSESSMENT_EVER_COMPLETED   = "assessment_ever_completed"
const val ASSESSMENT_VALIDITY_MS          = 6L * 30 * 24 * 60 * 60 * 1000
private const val KEY_LANGUAGE            = "is_english"

@Composable
fun SaharaApp() {
    val context = LocalContext.current
    val prefs   = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    val savedCounselorKey = remember { prefs.getString(KEY_COUNSELOR_KEY, "") ?: "" }
    val savedEmail        = remember { prefs.getString(KEY_USER_EMAIL, "") ?: "" }
    val rawFirebaseUser = remember { Firebase.auth.currentUser }
    val firebaseUser = remember(rawFirebaseUser?.uid, savedEmail, savedCounselorKey) {
        rawFirebaseUser?.takeUnless {
            it.isAnonymous && savedEmail.isBlank() && savedCounselorKey.isBlank()
        }
    }

    remember(firebaseUser?.uid) {
        AssessmentCache.restoreToGlobal(context, firebaseUser?.uid)
        true
    }

    val startDestination = remember {
        when {
            savedCounselorKey.isNotBlank()                    -> "counselor-dashboard"
            firebaseUser != null                              -> "auth-gate"
            else                                              -> "welcome"
        }
    }

    val navController = rememberNavController()

    var isEnglish by rememberSaveable {
        mutableStateOf(prefs.getBoolean(KEY_LANGUAGE, false))
    }

    val onLanguageChange: (Boolean) -> Unit = { newVal ->
        isEnglish = newVal
        prefs.edit().putBoolean(KEY_LANGUAGE, newVal).apply()
    }

    fun showAssessmentRequiredToast() {
        showAssessmentToast(context, isEnglish)
    }

    @Composable
    fun RequireCurrentAssessment(content: @Composable () -> Unit) {
        if (GlobalAppState.hasCompletedInitialAssessment) {
            content()
        } else {
            LaunchedEffect(Unit) {
                showAssessmentRequiredToast()
                navController.navigate("assessment") { launchSingleTop = true }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = pk.edu.ucp.saharaai.ui.theme.SaharaStrongGreen)
            }
        }
    }

    var userFullName by rememberSaveable {
        mutableStateOf(
            prefs.getString(KEY_USER_FULL_NAME, "")
                ?.ifBlank { firebaseUser?.displayName }
                ?.ifBlank { "Sahara User" }
                ?: "Sahara User"
        )
    }
    var userEmail by rememberSaveable {
        mutableStateOf(
            prefs.getString(KEY_USER_EMAIL, "")
                ?.ifBlank { firebaseUser?.email }
                ?.ifBlank { "" }
                ?: ""
        )
    }
    var userCallingName by rememberSaveable {
        mutableStateOf(
            prefs.getString(KEY_USER_NAME, "")
                ?.ifBlank { firebaseUser?.displayName?.let { callingName(it) } }
                ?.ifBlank { "User" }
                ?: "User"
        )
    }
    var isFromRegistration by rememberSaveable { mutableStateOf(false) }
    var counselorKey by rememberSaveable { mutableStateOf(savedCounselorKey) }
    var ngoKey by rememberSaveable { mutableStateOf("") }
    var hasAdminAccess by rememberSaveable { mutableStateOf(false) }

    var pendingVerificationEmail       by rememberSaveable { mutableStateOf("") }
    var pendingVerificationFullName    by rememberSaveable { mutableStateOf("") }
    var pendingVerificationCallingName by rememberSaveable { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        Firebase.auth.addAuthStateListener { auth ->
            if (auth.currentUser == null) {
                val hasPrefsSession = prefs.getString(KEY_USER_EMAIL, "").isNullOrBlank().not()
                val hasCounselorKey = prefs.getString(KEY_COUNSELOR_KEY, "").isNullOrBlank().not()
                if (!hasPrefsSession && !hasCounselorKey) {
                    navController.navigate("welcome") { popUpTo(0) { inclusive = true } }
                }
            }
        }
    }

    val signOut: () -> Unit = {
        Firebase.auth.signOut()
        scope.launch {
            runCatching {
                CredentialManager.create(context)
                    .clearCredentialState(ClearCredentialStateRequest())
            }
        }
        val emailForBiometric = prefs.getString(KEY_USER_EMAIL, "") ?: ""
        val nameForBiometric  = prefs.getString(KEY_USER_FULL_NAME, "") ?: ""
        prefs.edit()
            .remove(KEY_COUNSELOR_KEY)
            .remove(KEY_USER_NAME)
            .remove(KEY_USER_EMAIL)
            .remove(KEY_USER_FULL_NAME)
            .putString("biometric_last_email", emailForBiometric)
            .putString("biometric_last_name",  nameForBiometric)
            .apply()
        AssessmentCache.clearActiveSession(context)
        counselorKey = ""
        // NB: do NOT flip hasAdminAccess here. The admin-dashboard composable is
        // gated on it; flipping it while that screen is still current swaps in
        // the empty `else` branch (white) and races its popBackStack with the
        // navigate below. It's reset on the welcome screen instead.
        navController.navigate("welcome") { popUpTo(0) { inclusive = true } }
    }

    fun applyUserState(email: String, fullName: String) {
        val cleanEmail = email.ifBlank { Firebase.auth.currentUser?.email.orEmpty() }
        val cleanName = fullName.ifBlank { Firebase.auth.currentUser?.displayName.orEmpty() }
            .ifBlank { "Sahara User" }
        val calling = callingName(cleanName).ifBlank { cleanName }.ifBlank { "User" }
        userEmail = cleanEmail
        userFullName = cleanName
        userCallingName = calling
        GlobalAppState.userEmail = cleanEmail
        GlobalAppState.userName = calling
        prefs.edit()
            .putString(KEY_USER_EMAIL, cleanEmail)
            .putString(KEY_USER_FULL_NAME, cleanName)
            .putString(KEY_USER_NAME, calling)
            .apply()
    }

    fun clearBlockedSession(message: String) {
        Firebase.auth.signOut()
        prefs.edit()
            .remove(KEY_USER_NAME)
            .remove(KEY_USER_EMAIL)
            .remove(KEY_USER_FULL_NAME)
            .apply()
        userEmail = ""
        userFullName = "Sahara User"
        userCallingName = "User"
        context.showLocalizedToast(
            isEnglish,
            message.ifBlank { "This account is blocked." },
            message.ifBlank { "Yeh account block hai." },
            android.widget.Toast.LENGTH_LONG,
        )
        navController.navigate("welcome") { popUpTo(0) { inclusive = true } }
    }

    fun routeAfterAuth(
        emailHint: String = "",
        nameHint: String = "",
    ) {
        val currentUser = Firebase.auth.currentUser
        if (currentUser == null) {
            navController.navigate("welcome") { popUpTo(0) { inclusive = true } }
            return
        }
        scope.launch {
            val email = currentUser.email.orEmpty().ifBlank { emailHint }
            val name = currentUser.displayName.orEmpty().ifBlank { nameHint }.ifBlank { userFullName }
            RealtimeDBService.postAuthUserState(currentUser.uid, name, email)
                .onSuccess { state ->
                    if (state.isBlocked) {
                        clearBlockedSession(state.blockReason)
                        return@onSuccess
                    }
                    val dbName = state.userData["name"]?.toString().orEmpty().ifBlank { name }
                    val dbEmail = state.userData["email"]?.toString().orEmpty().ifBlank { email }
                    applyUserState(dbEmail, dbName)
                    runCatching { Firebase.messaging.token.await() }
                        .onSuccess { token -> RealtimeDBService.saveDeviceToken(currentUser.uid, token) }

                    val localAssessment = AssessmentCache.restoreToGlobal(context, currentUser.uid)
                    RealtimeDBService.loadLatestAssessmentResult(currentUser.uid)
                        .onSuccess { latest ->
                            val cloudAssessment = AssessmentCache.fromCloudMap(latest)
                            when {
                                cloudAssessment != null -> {
                                    AssessmentCache.save(
                                        context = context,
                                        uid = currentUser.uid,
                                        score = cloudAssessment.score,
                                        timestampMs = cloudAssessment.timestampMs,
                                    )
                                    AssessmentCache.applyToGlobal(cloudAssessment)
                                }
                                localAssessment?.isCurrent == true -> Unit
                                else -> AssessmentCache.clearActiveSession(context)
                            }
                        }
                        .onFailure {
                            if (localAssessment?.isCurrent != true) {
                                AssessmentCache.clearActiveSession(context)
                            }
                        }

                    // Google-only users have no password yet, but we DON'T nag
                    // them here — no link email, no toast. They set a password
                    // (and we email it) when they enable the fingerprint scanner
                    // in Settings, which is the natural place for it.
                    val targetRoute = if (state.onboardingCompleted) "dashboard" else "onboarding"
                    if (navController.currentDestination?.route != targetRoute) {
                        navController.navigate(targetRoute) {
                            launchSingleTop = true
                            popUpTo("welcome") { inclusive = true }
                        }
                    }
                }
                .onFailure {
                    context.showLocalizedToast(
                        isEnglish,
                        it.message ?: "Could not load account status.",
                        "Account status load nahi ho saka.",
                        android.widget.Toast.LENGTH_LONG,
                    )
                    navController.navigate("welcome") { popUpTo(0) { inclusive = true } }
                }
        }
    }

    val pendingNotificationRoute by NotificationRouteStore.pending.collectAsState()
    LaunchedEffect(pendingNotificationRoute) {
        val route = pendingNotificationRoute ?: return@LaunchedEffect
        if (Firebase.auth.currentUser != null) {
            navController.navigate(route) { launchSingleTop = true }
            NotificationRouteStore.consume(route)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        NavHost(navController = navController, startDestination = startDestination) {

            composable("auth-gate") {
                LaunchedEffect(Unit) { routeAfterAuth() }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = pk.edu.ucp.saharaai.ui.theme.SaharaStrongGreen)
                }
            }

            composable("welcome") {
                // Safe place to clear admin access — admin-dashboard is already
                // off the stack, so there's no gate to flip mid-screen.
                LaunchedEffect(Unit) { hasAdminAccess = false }
                WelcomeScreen(
                    onNavigateToLogin    = { navController.navigate("login") },
                    onNavigateToRegister = { navController.navigate("register") },
                    onNavigateToSettings = { navController.navigate("welcome-settings") },
                    isEnglish = isEnglish
                )
            }

            composable("welcome-settings") {
                WelcomeSettingsScreen(
                    isEnglish            = isEnglish,
                    onLanguageChange     = onLanguageChange,
                    onNavigateBack       = { navController.popBackStack() },
                    onNavigateToNgo      = { validatedKey ->
                        ngoKey = validatedKey
                        navController.navigate("ngo-dashboard")
                    },
                    onNavigateToAdmin    = {
                        // The admin key was already validated upstream. Sign in
                        // with a real Google account (not anonymous) so RTDB
                        // security rules can grant THIS uid admin writes — add it
                        // once under /admins/<uid> in the Firebase console.
                        scope.launch {
                            val adminUid = GoogleCredentialAuth.signIn(context).getOrNull()?.let { outcome ->
                                when (outcome) {
                                    is GoogleSignInOutcome.Signed -> Firebase.auth.currentUser?.uid
                                    is GoogleSignInOutcome.NeedsPasswordLink ->
                                        runCatching {
                                            Firebase.auth.signInWithCredential(outcome.pendingGoogleCredential).await()
                                        }.getOrNull()?.user?.uid
                                }
                            }
                            if (adminUid.isNullOrBlank()) {
                                context.showLocalizedToast(
                                    isEnglish,
                                    "Admin Google sign-in failed or was cancelled.",
                                    "Admin Google sign-in nakaam ya cancel ho gaya.",
                                    android.widget.Toast.LENGTH_LONG,
                                )
                                return@launch
                            }
                            android.util.Log.i("SaharaAdmin", "Admin signed in. uid=$adminUid (add /admins/$adminUid: true in RTDB)")
                            hasAdminAccess = true
                            navController.navigate("admin-dashboard")
                        }
                    },
                    onNavigateToCounselor = { key ->
                        counselorKey = key
                        prefs.edit().putString(KEY_COUNSELOR_KEY, key).apply()
                        navController.navigate("counselor-dashboard")
                    },
                    onNavigateToRegistration = { type ->
                        navController.navigate("registration-request/$type")
                    }
                )
            }

            composable("login") {
                LoginScreen(
                    onNavigateBack        = { navController.popBackStack() },
                    onNavigateToDashboard = { email ->
                        isFromRegistration = false
                        routeAfterAuth(emailHint = email)
                    },
                    onBiometricSuccess = {
                        // A successful fingerprint/face unlock only proves the
                        // device owner is present — it cannot, on its own, revive
                        // a Firebase session that has already been signed out (we
                        // never persist the password). If there is no live session
                        // to restore, keep the user on Login and ask for their
                        // password instead of dumping them back to Welcome.
                        if (Firebase.auth.currentUser == null) {
                            context.showLocalizedToast(
                                isEnglish,
                                "Please sign in with your password to continue.",
                                "Jari rakhne ke liye apne password se sign in karein.",
                                android.widget.Toast.LENGTH_LONG,
                            )
                        } else {
                            val restoredEmail =
                                Firebase.auth.currentUser?.email?.ifBlank { null }
                                    ?: prefs.getString(KEY_USER_EMAIL, "")?.ifBlank { null }
                                    ?: prefs.getString("biometric_last_email", "") ?: ""

                            val storedFull =
                                prefs.getString(KEY_USER_FULL_NAME, "")?.ifBlank { null }
                                    ?: prefs.getString("biometric_last_name", "") ?: ""

                            val restoredCalling =
                                prefs.getString(KEY_USER_NAME, "")?.ifBlank { null }
                                    ?: callingName(storedFull).ifBlank { storedFull }
                                        .ifBlank { "User" }

                            applyUserState(restoredEmail, storedFull.ifBlank { restoredCalling })
                            routeAfterAuth(emailHint = restoredEmail, nameHint = storedFull)
                        }
                    },
                    onNavigateToRegister       = { navController.navigate("register") },
                    onNavigateToForgotPassword = { navController.navigate("forgot-password") },
                    onNavigateToFaceLogin      = { navController.navigate("face-recognition") },
                    isEnglish = isEnglish
                )
            }

            composable("register") {
                RegisterScreen(
                    onNavigateBack    = { navController.popBackStack() },
                    onNavigateToLogin = {
                        navController.navigate("login") {
                            popUpTo("register") { inclusive = true }
                        }
                    },
                    onNavigateToOnboarding = { fullName, email, callingName ->
                        pendingVerificationFullName    = fullName
                        pendingVerificationEmail       = email
                        pendingVerificationCallingName = callingName
                        navController.navigate("email-verification") {
                            popUpTo("welcome") { inclusive = false }
                        }
                    },
                    onNavigateToDashboard = { email ->
                        isFromRegistration = false
                        routeAfterAuth(emailHint = email)
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

            composable("email-verification") {
                val uid = remember { Firebase.auth.currentUser?.uid ?: "" }
                EmailVerificationScreen(
                    email          = pendingVerificationEmail,
                    name           = pendingVerificationCallingName,
                    uid            = uid,
                    isEnglish      = isEnglish,
                    onVerified     = {
                        isFromRegistration = true
                        applyUserState(pendingVerificationEmail, pendingVerificationFullName)
                        routeAfterAuth(
                            emailHint = pendingVerificationEmail,
                            nameHint = pendingVerificationFullName,
                        )
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable("onboarding") {
                OnboardingScreen(
                    onNavigateToDashboard = {
                        navController.navigate("dashboard") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    },
                    onNavigateBack = { navController.popBackStack() },
                    isEnglish = isEnglish
                )
            }


            composable("dashboard") {
                // Wrap DashboardScreen in a Box so we can layer the weekly
                // listening-report popup on top whenever a fresh, unread
                // report exists. The popup is owned by WeeklyReportViewModel
                // so DashboardScreen.kt stays unchanged.
                val weeklyReportVm: pk.edu.ucp.saharaai.viewmodels.WeeklyReportViewModel =
                    androidx.lifecycle.viewmodel.compose.viewModel()
                val riskMonitoringVm: pk.edu.ucp.saharaai.viewmodels.RiskMonitoringViewModel =
                    androidx.lifecycle.viewmodel.compose.viewModel()
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    weeklyReportVm.checkForPopup()
                    riskMonitoringVm.refresh()
                }
                val pendingReport by weeklyReportVm.pendingPopup.collectAsState()
                val monitoringNotice by riskMonitoringVm.pendingStartNotice.collectAsState()
                val cumulativeReport by riskMonitoringVm.pendingCumulativeReport.collectAsState()
                // A dedicated haze source captures the whole dashboard render so the
                // report popups below can blur it as real glass.
                val dashHaze = remember { HazeState() }
                androidx.compose.foundation.layout.Box {
                    Box(Modifier.fillMaxSize().hazeSource(state = dashHaze)) {
                        DashboardScreen(
                            navController = navController,
                            isEnglish = isEnglish,
                            userName  = userCallingName
                        )
                    }
                    // Monitoring start popup wins priority over the weekly
                    // listening one because the user only sees the
                    // monitoring popup once in their lifetime.
                    cumulativeReport?.let { report ->
                        pk.edu.ucp.saharaai.ui.components.CumulativeReportDialog(
                            hazeState = dashHaze,
                            report = report,
                            isEnglish = isEnglish,
                            onViewProgress = {
                                riskMonitoringVm.acknowledgeCumulativeReport(report.cycleId)
                                navController.navigate("progress") { launchSingleTop = true }
                            },
                            onAcknowledge = {
                                riskMonitoringVm.acknowledgeCumulativeReport(report.cycleId)
                            },
                        )
                    } ?: monitoringNotice?.let { notice ->
                        pk.edu.ucp.saharaai.ui.components.MonitoringStartDialog(
                            hazeState = dashHaze,
                            notice = notice,
                            isEnglish = isEnglish,
                            onAcknowledge = riskMonitoringVm::acknowledgeStartNotice,
                        )
                    } ?: pendingReport?.takeIf { GlobalAppState.hasCompletedInitialAssessment }?.let { report ->
                        pk.edu.ucp.saharaai.ui.components.WeeklyReportPopupDialog(
                            hazeState = dashHaze,
                            report = report,
                            isEnglish = isEnglish,
                            onOpen = {
                                weeklyReportVm.dismissPopup(report.weekStartIso)
                                navController.navigate("weekly-reports") { launchSingleTop = true }
                            },
                            onDismiss = { weeklyReportVm.dismissPopup(report.weekStartIso) },
                            onDelete  = { weeklyReportVm.deleteReport(report.weekStartIso) },
                        )
                    }
                }
            }

            composable("assessment") {
                AssessmentScreen(
                    navController  = navController,
                    onNavigateBack = { navController.popBackStack() },
                    isEnglish = isEnglish,
                    onComplete = {
                        navController.navigate("dashboard") {
                            popUpTo("dashboard") { inclusive = true }
                        }
                    }
                )
            }

            composable("chat") {
                RequireCurrentAssessment {
                    ChatScreen(
                        navController  = navController,
                        onNavigateBack = { navController.popBackStack() },
                        isEnglish = isEnglish,
                        userName  = userCallingName
                    )
                }
            }

            composable("recovery") {
                RequireCurrentAssessment {
                    GameRecoveryScreen(
                        navController  = navController,
                        onNavigateBack = { navController.popBackStack() },
                        isEnglish = isEnglish
                    )
                }
            }

            composable("profile") {
                ProfileScreen(
                    navController         = navController,
                    onNavigateBack        = { navController.popBackStack() },
                    onNavigateToSettings  = {
                        navController.navigate("settings") { launchSingleTop = true }
                    },
                    onNavigateToEmergency = {
                        navController.navigate("emergency") { launchSingleTop = true }
                    },
                    onSignOut             = signOut,
                    isEnglish             = isEnglish,
                    fullName              = userFullName,
                    email                 = userEmail,
                    isFromRegistration    = isFromRegistration
                )
            }

            composable("activity-log") {
                RequireCurrentAssessment {
                    ActivityLogScreen(
                        navController   = navController,
                        onNavigateBack = { navController.popBackStack() },
                        isEnglish      = isEnglish
                    )
                }
            }

            composable("weekly-reports") {
                RequireCurrentAssessment {
                    WeeklyReportScreen(
                        navController = navController,
                        isEnglish     = isEnglish
                    )
                }
            }

            composable("help-center") {
                HelpCenterScreen(
                    navController        = navController,
                    onNavigateBack        = { navController.popBackStack() },
                    onNavigateToEmergency = {
                        navController.navigate("emergency") { launchSingleTop = true }
                    },
                    isEnglish             = isEnglish
                )
            }

            composable("notifications") {
                NotificationsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    navController  = navController,
                    isEnglish      = isEnglish
                )
            }

            composable("voice-analysis") {
                RequireCurrentAssessment {
                    VoiceAnalysisScreen(
                        navController     = navController,
                        onNavigateBack   = { navController.popBackStack() },
                        onNavigateToChat = { navController.navigate("chat") { launchSingleTop = true } },
                        isEnglish        = isEnglish
                    )
                }
            }

            composable("face-recognition") {
                FaceRecognitionScreen(
                    navController = navController,
                    isEnglish     = isEnglish
                )
            }

            composable("sahara-lens") {
                RequireCurrentAssessment {
                    SaharaLensScreen(
                        navController = navController,
                        isEnglish     = isEnglish
                    )
                }
            }

            composable("meditation") {
                RequireCurrentAssessment {
                    MeditationScreen(
                        navController = navController,
                        isEnglish     = isEnglish
                    )
                }
            }

            composable("sleep-tracker") {
                RequireCurrentAssessment {
                    SleepTrackerScreen(
                        navController = navController,
                        isEnglish     = isEnglish
                    )
                }
            }

            composable("screen-time") {
                RequireCurrentAssessment {
                    ScreenTimeScreen(
                        navController = navController,
                        isEnglish     = isEnglish
                    )
                }
            }

            // Legacy `mood-patterns` route: the standalone screen was retired
            // and its mood-tracking surface moved into JournalScreen. We keep
            // the route as a redirect so anything that still calls
            // navigate("mood-patterns") (deep links, bookmarks) lands on the
            // unified journal experience. Remove this block once nothing in
            // the codebase references the old name.
            composable("mood-patterns") {
                RequireCurrentAssessment {
                    JournalScreen(
                        navController = navController,
                        isEnglish     = isEnglish
                    )
                }
            }

            composable("progress") {
                ProgressScreen(
                    navController = navController,
                    isEnglish     = isEnglish
                )
            }

            composable("emergency") {
                EmergencyScreen(
                    navController  = navController,
                    onNavigateBack = { navController.popBackStack() },
                    isEnglish      = isEnglish
                )
            }

            composable("journal") {
                RequireCurrentAssessment {
                    JournalScreen(
                        navController = navController,
                        isEnglish     = isEnglish
                    )
                }
            }

            composable("counselors") {
                RequireCurrentAssessment {
                    CounselorsScreen(
                        navController = navController,
                        isEnglish     = isEnglish
                    )
                }
            }

            composable("settings") {
                SettingsScreen(
                    navController    = navController,
                    onNavigateBack   = { navController.popBackStack() },
                    isEnglish        = isEnglish,
                    userName         = userCallingName,
                    onLanguageChange = onLanguageChange
                )
            }

            composable("connections") {
                RequireCurrentAssessment {
                    ConnectionsScreen(
                        navController  = navController,
                        onNavigateBack = { navController.popBackStack() },
                        isEnglish      = isEnglish
                    )
                }
            }

            composable("community") {
                RequireCurrentAssessment {
                    CommunityScreen(
                        navController = navController,
                        isEnglish     = isEnglish
                    )
                }
            }

            composable("counselor-chat/{counselorId}/{counselorName}") { back ->
                val counselorId   = Uri.decode(back.arguments?.getString("counselorId") ?: "")
                val counselorName = Uri.decode(back.arguments?.getString("counselorName") ?: "Counselor")
                    .replace("_", " ")
                val vmKey = "counselor_$counselorId"
                RequireCurrentAssessment {
                    ChatScreen(
                        navController  = navController,
                        onNavigateBack = { navController.popBackStack() },
                        isEnglish      = isEnglish,
                        userName       = userCallingName,
                        counselorId    = counselorId,
                        counselorName  = counselorName,
                        chatViewModel  = viewModel(key = vmKey)
                    )
                }
            }

            composable("counselor-opens-chat/{userUid}/{counselorKey}") { back ->
                val targetUserUid = Uri.decode(back.arguments?.getString("userUid") ?: "")
                val ck            = Uri.decode(back.arguments?.getString("counselorKey") ?: "")
                val vmKey         = "counselor_opens_${targetUserUid}_$ck"
                ChatScreen(
                    navController  = navController,
                    onNavigateBack = { navController.popBackStack() },
                    isEnglish      = isEnglish,
                    userName       = "User",
                    counselorId    = ck,
                    counselorName  = "User (${targetUserUid.take(6)})",
                    forUserId      = targetUserUid,
                    chatViewModel  = viewModel(key = vmKey)
                )
            }

            composable("counselor-call/{counselorKey}/{counselorName}/{mode}/{forUserId}") { back ->
                InAppCallScreen(
                    navController = navController,
                    isEnglish = isEnglish,
                    counselorKey = Uri.decode(back.arguments?.getString("counselorKey") ?: ""),
                    counselorName = Uri.decode(back.arguments?.getString("counselorName") ?: "Counselor").replace("_", " "),
                    mode = Uri.decode(back.arguments?.getString("mode") ?: "voice"),
                    forUserId = Uri.decode(back.arguments?.getString("forUserId") ?: "self"),
                )
            }

            composable("ngo-dashboard") {
                NgoDashboardScreen(
                    navController = navController,
                    isEnglish     = isEnglish,
                    ngoKey        = ngoKey,
                    onSignOut     = signOut
                )
            }

            composable("admin-dashboard") {
                if (hasAdminAccess) {
                    AdminDashboardScreen(
                        navController = navController,
                        isEnglish = isEnglish,
                        onSignOut = signOut
                    )
                } else {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                }
            }

            composable("registration-request/{applicantType}") { back ->
                RegistrationRequestScreen(
                    navController = navController,
                    applicantType = back.arguments?.getString("applicantType") ?: "COUNSELOR",
                    isEnglish = isEnglish
                )
            }

            composable("counselor-dashboard") {
                var setupChecked by rememberSaveable { mutableStateOf(false) }
                val scope = rememberCoroutineScope()
                LaunchedEffect(counselorKey) {
                    if (!setupChecked && counselorKey.isNotBlank()) {
                        setupChecked = true
                        scope.launch {
                            val result = RealtimeDBService.getCounselorKey(counselorKey)
                            val data = result.getOrNull()
                            val profileComplete = data?.get("profileComplete") as? Boolean ?: false
                            if (!profileComplete) {
                                navController.navigate("counselor-setup") {
                                    popUpTo("counselor-dashboard") { inclusive = true }
                                }
                            }
                        }
                    }
                }
                CounselorDashboardScreen(
                    navController = navController,
                    isEnglish     = isEnglish,
                    counselorKey  = counselorKey,
                    onSignOut     = signOut
                )
            }

            composable("counselor-setup") {
                CounselorSetupScreen(
                    navController = navController,
                    isEnglish     = isEnglish,
                    counselorKey  = counselorKey
                )
            }
        }
    }
}

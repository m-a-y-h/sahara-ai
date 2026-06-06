package pk.edu.ucp.saharaai.ui.screens

import android.graphics.BitmapFactory
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.messaging.messaging
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.tasks.await
import pk.edu.ucp.saharaai.data.model.AvatarRequest
import pk.edu.ucp.saharaai.data.model.PaymentRequest
import pk.edu.ucp.saharaai.data.model.RegistrationRequest
import pk.edu.ucp.saharaai.data.model.BugReport
import pk.edu.ucp.saharaai.data.model.CounselorAttributeCatalog
import pk.edu.ucp.saharaai.ui.components.CardVariant
import pk.edu.ucp.saharaai.ui.components.GlassAlertDialog
import pk.edu.ucp.saharaai.ui.components.SaharaCard
import pk.edu.ucp.saharaai.ui.theme.*
import pk.edu.ucp.saharaai.viewmodels.AdminDashboardViewModel

private data class AdminMetric(
    val label: String,
    val value: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tint: Color,
)

private data class AdminImagePreview(
    val title: String,
    val imageUrl: String,
)

private enum class AdminQueueKey {
    APPLICATIONS,
    AVATARS,
    BUGS,
    PAYMENTS,
}

@Composable
fun AdminDashboardScreen(
    navController: NavController,
    isEnglish: Boolean,
    onSignOut: () -> Unit = {},
    dashboardViewModel: AdminDashboardViewModel = viewModel()
) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val applications = dashboardViewModel.applications
    val payments = dashboardViewModel.payments
    val bugReports = dashboardViewModel.bugReports
    val avatarRequests = dashboardViewModel.avatarRequests
    val error = dashboardViewModel.error
    val hazeState = remember { HazeState() }
    var imagePreview by remember { mutableStateOf<AdminImagePreview?>(null) }
    var expandedQueues by remember { mutableStateOf(setOf(AdminQueueKey.APPLICATIONS)) }

    // Subscribe this device to the "admins" FCM topic so the sahara_push cron
    // can push a notification whenever a new registration / payment proof /
    // bug report lands. Idempotent — the platform de-dupes the subscription.
    LaunchedEffect(Unit) {
        runCatching {
            com.google.firebase.Firebase.messaging
                .subscribeToTopic("admins")
                .await()
        }
    }

    val counselorApplications = applications.filter { it.applicantType.equals("COUNSELOR", ignoreCase = true) }
    val ngoApplications = applications.filter { it.applicantType.equals("NGO", ignoreCase = true) }
    val otherApplications = applications.filterNot {
        it.applicantType.equals("COUNSELOR", ignoreCase = true) ||
            it.applicantType.equals("NGO", ignoreCase = true)
    }
    val pendingApplications = applications.filter { it.status.equals("PENDING_REVIEW", ignoreCase = true) }
    val pendingCounselorApplications = counselorApplications.count { it.status.equals("PENDING_REVIEW", ignoreCase = true) }
    val pendingNgoApplications = ngoApplications.count { it.status.equals("PENDING_REVIEW", ignoreCase = true) }
    val pendingPayments = payments.filter { it.status == "PENDING_REVIEW" }
    val pendingAvatars = avatarRequests.filter { it.status == "PENDING_REVIEW" }
    val unresolvedBugs = bugReports.filter { it.status != "RESOLVED" }
    val bgGradient = if (isDark) {
        listOf(
            Color(0xFF0D241D),
            Color(0xFF132D30),
            MaterialTheme.colorScheme.background,
        )
    } else {
        listOf(
            SaharaStrongGreen.copy(.24f),
            SaharaSky.copy(.14f),
            SaharaPeach.copy(.10f),
            MaterialTheme.colorScheme.background.copy(.72f),
        )
    }
    val emailApplication: (RegistrationRequest, String) -> Unit = { request, issuedKey ->
        val subject = Uri.encode("Sahara AI ${request.applicantType} application")
        val body = if (issuedKey.isBlank()) "" else Uri.encode(
            "Your documents have been approved. Your Sahara AI ${request.applicantType} access key is: $issuedKey"
        )
        val intent = Intent(
            Intent.ACTION_SENDTO,
            Uri.parse("mailto:${request.email}?subject=$subject&body=$body")
        )
        context.startActivity(intent)
    }
    fun toggleQueue(key: AdminQueueKey) {
        expandedQueues = if (key in expandedQueues) expandedQueues - key else expandedQueues + key
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
                .background(Brush.verticalGradient(bgGradient))
        )
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(18.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (isEnglish) "Admin Dashboard" else "Admin Dashboard",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = SaharaStrongGreen
                    )
                    Text(
                        if (isEnglish) "Manual review and assignments" else "Manual review aur assignments",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onSignOut) {
                    Icon(
                        Icons.AutoMirrored.Filled.Logout,
                        contentDescription = if (isEnglish) "Sign Out" else "Log Out",
                        tint = SaharaCoral,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            SaharaCard(variant = CardVariant.GLASS, hazeState = hazeState, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = SaharaWarning)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (isEnglish)
                            "Payment screenshots must be verified manually against your receiving account before approval."
                        else
                            "Approve karne se pehle screenshot ko receiving account se manually verify karein.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AdminMetricGrid(
                hazeState = hazeState,
                metrics = listOf(
                    AdminMetric(
                        if (isEnglish) "All forms" else "Forms",
                        pendingApplications.size,
                        Icons.Default.Badge,
                        SaharaSky
                    ),
                    AdminMetric(
                        if (isEnglish) "Counselors" else "Counselors",
                        pendingCounselorApplications,
                        Icons.Default.Psychology,
                        SaharaStrongGreen
                    ),
                    AdminMetric(
                        if (isEnglish) "NGOs" else "NGOs",
                        pendingNgoApplications,
                        Icons.Default.Groups,
                        SaharaPeach
                    ),
                    AdminMetric(
                        if (isEnglish) "Bugs" else "Bugs",
                        unresolvedBugs.size,
                        Icons.Default.BugReport,
                        if (unresolvedBugs.isEmpty()) SaharaStrongGreen else SaharaCoral
                    ),
                    AdminMetric(
                        if (isEnglish) "Payments" else "Payments",
                        pendingPayments.size,
                        Icons.Default.Payments,
                        SaharaStrongGreen
                    ),
                    AdminMetric(
                        if (isEnglish) "Avatars" else "Avatars",
                        pendingAvatars.size,
                        Icons.Default.AccountCircle,
                        if (pendingAvatars.isEmpty()) SaharaStrongGreen else SaharaWarning
                    )
                )
            )

            if (error.isNotBlank()) {
                Text(error, color = SaharaCoral, style = MaterialTheme.typography.bodySmall)
            }

            AdminQueuePanel(
                title = if (isEnglish) "Registration Forms" else "Registration Forms",
                subtitle = if (isEnglish) "Counselor and NGO applications" else "Counselor aur NGO applications",
                count = pendingApplications.size,
                icon = Icons.Default.Badge,
                tint = SaharaSky,
                expanded = AdminQueueKey.APPLICATIONS in expandedQueues,
                hazeState = hazeState,
                onToggle = { toggleQueue(AdminQueueKey.APPLICATIONS) },
            ) {
                if (applications.isEmpty()) {
                    AdminEmptyCard(
                        if (isEnglish) "No application forms submitted." else "Koi application form submit nahi hua.",
                        hazeState = hazeState,
                    )
                } else {
                    RegistrationReviewSection(
                        title = if (isEnglish) "Counselor Applications" else "Counselor Applications",
                        emptyMessage = if (isEnglish) "No counselor applications yet." else "Abhi koi counselor application nahi.",
                        requests = counselorApplications,
                        isEnglish = isEnglish,
                        hazeState = hazeState,
                        onEmail = emailApplication,
                        onOpenDocument = { label, url ->
                            imagePreview = AdminImagePreview(label.replace("_", " "), url)
                        },
                        onApprove = { request, key, notes, attributeIds ->
                            dashboardViewModel.approveRegistration(request, key, notes, attributeIds)
                        },
                        onReject = { request, notes ->
                            dashboardViewModel.rejectRegistration(request.requestId, notes)
                        }
                    )
                    RegistrationReviewSection(
                        title = if (isEnglish) "NGO Applications" else "NGO Applications",
                        emptyMessage = if (isEnglish) "No NGO applications yet." else "Abhi koi NGO application nahi.",
                        requests = ngoApplications,
                        isEnglish = isEnglish,
                        hazeState = hazeState,
                        onEmail = emailApplication,
                        onOpenDocument = { label, url ->
                            imagePreview = AdminImagePreview(label.replace("_", " "), url)
                        },
                        onApprove = { request, key, notes, attributeIds ->
                            dashboardViewModel.approveRegistration(request, key, notes, attributeIds)
                        },
                        onReject = { request, notes ->
                            dashboardViewModel.rejectRegistration(request.requestId, notes)
                        }
                    )
                    if (otherApplications.isNotEmpty()) {
                        RegistrationReviewSection(
                            title = if (isEnglish) "Other Applications" else "Other Applications",
                            emptyMessage = "",
                            requests = otherApplications,
                            isEnglish = isEnglish,
                            hazeState = hazeState,
                            onEmail = emailApplication,
                            onOpenDocument = { label, url ->
                                imagePreview = AdminImagePreview(label.replace("_", " "), url)
                            },
                            onApprove = { request, key, notes, attributeIds ->
                                dashboardViewModel.approveRegistration(request, key, notes, attributeIds)
                            },
                            onReject = { request, notes ->
                                dashboardViewModel.rejectRegistration(request.requestId, notes)
                            }
                        )
                    }
                }
            }

            AdminQueuePanel(
                title = if (isEnglish) "Avatar Review" else "Avatar Review",
                subtitle = if (isEnglish) "Profile images awaiting approval" else "Profile images review ke liye",
                count = pendingAvatars.size,
                icon = Icons.Default.AccountCircle,
                tint = SaharaWarning,
                expanded = AdminQueueKey.AVATARS in expandedQueues,
                hazeState = hazeState,
                onToggle = { toggleQueue(AdminQueueKey.AVATARS) },
            ) {
                if (pendingAvatars.isEmpty()) {
                    AdminEmptyCard(
                        if (isEnglish) "No avatar requests awaiting review." else "Koi avatar request pending nahi.",
                        hazeState = hazeState,
                    )
                } else {
                    pendingAvatars.forEach { request ->
                        AvatarReviewCard(
                            request = request,
                            isEnglish = isEnglish,
                            hazeState = hazeState,
                            onOpenImage = {
                                imagePreview = AdminImagePreview(
                                    if (isEnglish) "Avatar request" else "Avatar request",
                                    request.fileUrl,
                                )
                            },
                            onApprove = { dashboardViewModel.approveAvatar(request, it) },
                            onReject = { dashboardViewModel.rejectAvatar(request, it) },
                            onBlock = { dashboardViewModel.blockAvatarUser(request, it) },
                        )
                    }
                }
            }

            AdminQueuePanel(
                title = if (isEnglish) "User Bug Reports" else "Users ki Bug Reports",
                subtitle = if (isEnglish) "Screenshots and device reports" else "Screenshots aur device reports",
                count = unresolvedBugs.size,
                icon = Icons.Default.BugReport,
                tint = if (unresolvedBugs.isEmpty()) SaharaStrongGreen else SaharaCoral,
                expanded = AdminQueueKey.BUGS in expandedQueues,
                hazeState = hazeState,
                onToggle = { toggleQueue(AdminQueueKey.BUGS) },
            ) {
                if (bugReports.isEmpty()) {
                    AdminEmptyCard(
                        if (isEnglish) "No bug reports submitted." else "Koi bug report submit nahi ki gayi hai.",
                        hazeState = hazeState,
                    )
                } else {
                    bugReports.forEach { report ->
                        BugReviewCard(
                            report = report,
                            isEnglish = isEnglish,
                            hazeState = hazeState,
                            onOpenScreenshot = {
                                imagePreview = AdminImagePreview(
                                    if (isEnglish) "Bug screenshot" else "Bug screenshot",
                                    report.screenshotUrl,
                                )
                            },
                            onResolve = { dashboardViewModel.resolveBugReport(report.reportId) },
                        )
                    }
                }
            }

            AdminQueuePanel(
                title = if (isEnglish) "Payment Proof Review" else "Payment Proof Review",
                subtitle = if (isEnglish) "Manual screenshot verification" else "Manual screenshot verification",
                count = pendingPayments.size,
                icon = Icons.Default.Payments,
                tint = SaharaStrongGreen,
                expanded = AdminQueueKey.PAYMENTS in expandedQueues,
                hazeState = hazeState,
                onToggle = { toggleQueue(AdminQueueKey.PAYMENTS) },
            ) {
                if (pendingPayments.isEmpty()) {
                    AdminEmptyCard(
                        if (isEnglish) "No payment screenshots awaiting review." else "Koi payment screenshot pending nahi.",
                        hazeState = hazeState,
                    )
                } else {
                    pendingPayments.forEach { payment ->
                        PaymentReviewCard(
                            payment = payment,
                            isEnglish = isEnglish,
                            hazeState = hazeState,
                            onOpenProof = {
                                imagePreview = AdminImagePreview(
                                    if (isEnglish) "Payment proof" else "Payment proof",
                                    payment.proofUrl,
                                )
                            },
                            onApprove = { notes ->
                                dashboardViewModel.approvePayment(payment, notes)
                            },
                            onReject = { notes, attachment ->
                                dashboardViewModel.rejectPayment(payment, notes, attachment)
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.navigationBarsPadding().height(20.dp))
        }

        imagePreview?.let { preview ->
            AdminImagePreviewDialog(
                preview = preview,
                isEnglish = isEnglish,
                hazeState = hazeState,
                onDismiss = { imagePreview = null },
            )
        }
    }
}

@Composable
private fun AdminQueuePanel(
    title: String,
    subtitle: String,
    count: Int,
    icon: ImageVector,
    tint: Color,
    expanded: Boolean,
    hazeState: HazeState,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    SaharaCard(
        variant = CardVariant.GLASS,
        hazeState = hazeState,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(if (expanded) 14.dp else 0.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = tint.copy(alpha = 0.16f),
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.padding(10.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = tint.copy(alpha = 0.14f),
                ) {
                    Text(
                        count.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = tint,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    content()
                }
            }
        }
    }
}

@Composable
private fun AvatarReviewCard(
    request: AvatarRequest,
    isEnglish: Boolean,
    hazeState: HazeState,
    onOpenImage: () -> Unit,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
    onBlock: (String) -> Unit,
) {
    var notes by remember(request.requestId) { mutableStateOf("") }
    var confirmBlock by remember(request.requestId) { mutableStateOf(false) }
    if (confirmBlock) {
        GlassAlertDialog(
            hazeState = hazeState,
            onDismissRequest = { confirmBlock = false },
            title = { Text(if (isEnglish) "Block this user?" else "Is user ko block karein?") },
            text = { Text(if (isEnglish) "This prevents this UID/email from continuing after sign-in." else "Ye UID/email ko sign-in ke baad app access se rok dega.") },
            confirmButton = {
                TextButton(onClick = { confirmBlock = false; onBlock(notes) }) {
                    Text(if (isEnglish) "Block" else "Block", color = SaharaCoral)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmBlock = false }) {
                    Text(if (isEnglish) "Cancel" else "Cancel")
                }
            }
        )
    }
    SaharaCard(variant = CardVariant.GLASS, hazeState = hazeState, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(request.userName.ifBlank { request.userId.take(10) }, fontWeight = FontWeight.Bold)
                    Text(request.userEmail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${request.sizeBytes / 1024} KB", style = MaterialTheme.typography.labelMedium, color = SaharaStrongGreen)
            }
            AdminEvidenceImage(
                model = request.fileUrl,
                contentDescription = "Avatar request",
                modifier = Modifier.fillMaxWidth().height(160.dp).background(Color.LightGray.copy(.12f), RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Fit,
            )
            OutlinedButton(onClick = onOpenImage) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
                Spacer(Modifier.width(6.dp))
                Text(if (isEnglish) "Open image" else "Image kholein")
            }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(if (isEnglish) "Admin comment" else "Admin comment") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2
            )
            AdminActionRow {
                Button(
                    onClick = { onApprove(notes) },
                    colors = ButtonDefaults.buttonColors(containerColor = SaharaStrongGreen)
                ) { Text(if (isEnglish) "Approve" else "Approve") }
                OutlinedButton(onClick = { onReject(notes) }) {
                    Text(if (isEnglish) "Reject" else "Reject")
                }
                OutlinedButton(onClick = { confirmBlock = true }) {
                    Text(if (isEnglish) "Block" else "Block", color = SaharaCoral)
                }
            }
        }
    }
}

@Composable
private fun BugReviewCard(
    report: BugReport,
    isEnglish: Boolean,
    hazeState: HazeState,
    onOpenScreenshot: () -> Unit,
    onResolve: () -> Unit,
) {
    var confirmResolve by remember(report.reportId) { mutableStateOf(false) }
    val isResolved = report.status == "RESOLVED"
    if (confirmResolve) {
        GlassAlertDialog(
            hazeState = hazeState,
            onDismissRequest = { confirmResolve = false },
            title = { Text(if (isEnglish) "Resolve this bug?" else "Kya ye bug hal ho gaya hai?") },
            text = {
                Text(
                    if (isEnglish) "Confirm only after checking the screenshot and issue. This prevents an accidental resolution."
                    else "Screenshot aur issue check karne ke baad hi confirm karein. Is se ghalti se resolve nahi hoga."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmResolve = false; onResolve() }) {
                    Text(if (isEnglish) "Confirm Resolved" else "Hal Ho Gaya Hai")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmResolve = false }) {
                    Text(if (isEnglish) "Cancel" else "Cancel Karein")
                }
            },
        )
    }

    SaharaCard(variant = CardVariant.GLASS, hazeState = hazeState, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(report.maskedEmail, fontWeight = FontWeight.Bold)
                Text(
                    if (isResolved) {
                        if (isEnglish) "RESOLVED" else "HAL HO GAYA HAI"
                    } else {
                        if (isEnglish) "UNRESOLVED" else "HAL HONA BAQI HAI"
                    },
                    color = if (isResolved) SaharaStrongGreen else SaharaCoral,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(report.deviceModel, style = MaterialTheme.typography.bodySmall)
            if (report.description.isNotBlank()) {
                Text(report.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AdminEvidenceImage(
                model = report.screenshotUrl,
                contentDescription = "Bug screenshot",
                modifier = Modifier.fillMaxWidth().height(150.dp).background(Color.LightGray.copy(alpha = .12f), RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Fit,
            )
            AdminActionRow {
                OutlinedButton(onClick = onOpenScreenshot) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
                    Spacer(Modifier.width(4.dp))
                    Text(if (isEnglish) "Open Screenshot" else "Screenshot Kholein")
                }
                if (!isResolved) {
                    Button(
                        onClick = { confirmResolve = true },
                        colors = ButtonDefaults.buttonColors(containerColor = SaharaStrongGreen),
                    ) {
                        Text(if (isEnglish) "Resolve" else "Hal Karein")
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminMetricGrid(
    metrics: List<AdminMetric>,
    hazeState: HazeState,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth < 520.dp) 2 else 3
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            metrics.chunked(columns).forEach { rowMetrics ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowMetrics.forEach { metric ->
                        AdminCountCard(
                            label = metric.label,
                            value = metric.value,
                            icon = metric.icon,
                            tint = metric.tint,
                            hazeState = hazeState,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(columns - rowMetrics.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminCountCard(
    label: String,
    value: Int,
    icon: ImageVector,
    tint: Color,
    hazeState: HazeState,
    modifier: Modifier
) {
    SaharaCard(variant = CardVariant.GLASS, hazeState = hazeState, modifier = modifier) {
        Column(Modifier.padding(8.dp)) {
            Icon(icon, null, tint = tint)
            Spacer(Modifier.height(8.dp))
            Text("$value", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AdminEmptyCard(
    message: String,
    hazeState: HazeState,
) {
    SaharaCard(variant = CardVariant.GLASS, hazeState = hazeState, modifier = Modifier.fillMaxWidth()) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AdminInlineMenu(
    title: String,
    subtitle: String? = null,
    count: Int? = null,
    tint: Color = SaharaStrongGreen,
    initiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember(title, count) { mutableStateOf(initiallyExpanded) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = if (isSystemInDarkTheme()) 0.18f else 0.38f),
                RoundedCornerShape(18.dp),
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(if (expanded) 10.dp else 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = tint,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            count?.let {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = tint.copy(alpha = 0.12f),
                ) {
                    Text(
                        it.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = tint,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                content()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminActionRow(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
private fun AdminEvidenceImage(
    model: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val inlineBitmap = remember(model) { decodeInlineDataImage(model) }
    if (inlineBitmap != null) {
        Image(
            bitmap = inlineBitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    } else {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    }
}

private fun decodeInlineDataImage(model: String): android.graphics.Bitmap? {
    val marker = "base64,"
    if (!model.startsWith("data:image", ignoreCase = true) || marker !in model) return null
    return runCatching {
        val encoded = model.substringAfter(marker).trim()
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}

@Composable
private fun AdminImagePreviewDialog(
    preview: AdminImagePreview,
    isEnglish: Boolean,
    hazeState: HazeState,
    onDismiss: () -> Unit,
) {
    GlassAlertDialog(
        hazeState = hazeState,
        onDismissRequest = onDismiss,
        title = {
            Text(
                preview.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            if (preview.imageUrl.isBlank()) {
                Text(
                    if (isEnglish) "No image was supplied." else "Image maujood nahi.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                AdminEvidenceImage(
                    model = preview.imageUrl,
                    contentDescription = preview.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp, max = 520.dp)
                        .background(Color.LightGray.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isEnglish) "Close" else "Band karein")
            }
        },
    )
}

@Composable
private fun RegistrationReviewSection(
    title: String,
    emptyMessage: String,
    requests: List<RegistrationRequest>,
    isEnglish: Boolean,
    hazeState: HazeState,
    onEmail: (RegistrationRequest, String) -> Unit,
    onOpenDocument: (String, String) -> Unit,
    onApprove: (RegistrationRequest, String, String, List<String>) -> Unit,
    onReject: (RegistrationRequest, String) -> Unit,
) {
    AdminInlineMenu(
        title = title,
        subtitle = if (isEnglish) "Tap to review this group" else "Review ke liye tap karein",
        count = requests.size,
        tint = SaharaStrongGreen,
        initiallyExpanded = requests.isNotEmpty(),
    ) {
        if (requests.isEmpty()) {
            AdminEmptyCard(emptyMessage, hazeState = hazeState)
        } else {
            requests.forEach { request ->
                RegistrationReviewCard(
                    request = request,
                    isEnglish = isEnglish,
                    hazeState = hazeState,
                    onEmail = { issuedKey -> onEmail(request, issuedKey) },
                    onOpenDocument = onOpenDocument,
                    onApprove = { key, notes, attributeIds -> onApprove(request, key, notes, attributeIds) },
                    onReject = { notes -> onReject(request, notes) }
                )
            }
        }
    }
}

@Composable
private fun RegistrationReviewCard(
    request: RegistrationRequest,
    isEnglish: Boolean,
    hazeState: HazeState,
    onEmail: (String) -> Unit,
    onOpenDocument: (String, String) -> Unit,
    onApprove: (String, String, List<String>) -> Unit,
    onReject: (String) -> Unit
) {
    var issuedKey by remember(request.requestId) { mutableStateOf("") }
    var notes by remember(request.requestId) { mutableStateOf("") }
    var selectedAttributeIds by remember(request.requestId) { mutableStateOf(request.approvedAttributeIds.toSet()) }
    val isCounselorRequest = request.applicantType.equals("COUNSELOR", ignoreCase = true)
    val documentEntries = request.documentUrls.ifEmpty {
        if (request.documentUrl.isNotBlank()) mapOf("document" to request.documentUrl) else emptyMap()
    }
    val locationText = listOf(request.district, request.city)
        .filter { it.isNotBlank() }
        .joinToString(", ")
    val accuracyText = if (request.locationAccuracyMeters > 0f) {
        "${request.locationAccuracyMeters.toInt()}m"
    } else {
        ""
    }
    SaharaCard(variant = CardVariant.GLASS, hazeState = hazeState, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        request.applicantName.ifBlank { request.applicantType },
                        fontWeight = FontWeight.Bold,
                        color = SaharaSky,
                    )
                    Text(
                        request.email.ifBlank { request.applicantType },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = SaharaSky.copy(alpha = 0.12f),
                ) {
                    Text(
                        request.status.replace("_", " "),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = SaharaSky,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    )
                }
            }

            RegistrationReviewInfoSection(
                title = if (isEnglish) "Applicant details" else "Applicant details",
                isEnglish = isEnglish,
                initiallyExpanded = true,
                fields = listOf(
                    "Applicant name" to request.applicantName,
                    "Organization / NGO" to request.organizationName,
                    "Email" to request.email,
                    "Phone" to request.phone,
                    "FCM token" to request.applicantFcmToken,
                ),
            )
            RegistrationReviewInfoSection(
                title = if (isEnglish) "Location" else "Location",
                isEnglish = isEnglish,
                initiallyExpanded = false,
                fields = listOf(
                    "Region" to request.region,
                    "City" to request.city,
                    "District" to request.district,
                    "Detected location" to locationText,
                    "Accuracy" to accuracyText,
                ),
            )
            RegistrationReviewInfoSection(
                title = if (isEnglish) "Verification" else "Verification",
                isEnglish = isEnglish,
                initiallyExpanded = false,
                fields = listOf(
                    "Verification body" to request.verificationBody,
                    "Registration / license number" to request.registrationNumber,
                    "Qualification / services summary" to request.qualificationSummary,
                    "Additional notes" to request.details,
                    "Required evidence keys" to request.requiredDocumentKeys.joinToString(", "),
                    "Provided evidence keys" to documentEntries.keys.joinToString(", "),
                ),
            )
            AdminActionRow {
                OutlinedButton(onClick = { onEmail(request.issuedKey) }) {
                    Icon(Icons.Default.Email, null)
                    Spacer(Modifier.width(4.dp))
                    Text(if (request.issuedKey.isBlank()) "Email" else "Email Key")
                }
                documentEntries.entries.forEach { (label, url) ->
                    OutlinedButton(onClick = { onOpenDocument(label, url) }) {
                        Icon(Icons.Default.Description, null)
                        Spacer(Modifier.width(4.dp))
                        Text(label.replace("_", " ").take(18))
                    }
                }
            }
            if (request.status == "PENDING_REVIEW") {
                OutlinedTextField(
                    value = issuedKey,
                    onValueChange = { issuedKey = it },
                    label = { Text(if (isEnglish) "Key to issue after approval" else "Approval ke baad key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (isCounselorRequest) {
                    AdminInlineMenu(
                        title = if (isEnglish) "Counselor search attributes" else "Counselor search attributes",
                        subtitle = if (isEnglish) "Select visible specialties before approval" else "Approval se pehle specialties chunein",
                        count = selectedAttributeIds.size,
                        tint = SaharaStrongGreen,
                        initiallyExpanded = false,
                    ) {
                        CounselorAttributeCatalog.all.forEach { attribute ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = selectedAttributeIds.contains(attribute.id),
                                    onCheckedChange = { checked ->
                                        selectedAttributeIds = if (checked) {
                                            selectedAttributeIds + attribute.id
                                        } else {
                                            selectedAttributeIds - attribute.id
                                        }
                                    },
                                )
                                Column {
                                    Text(attribute.labelEn, style = MaterialTheme.typography.bodySmall)
                                    Text(attribute.labelUr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(if (isEnglish) "Review notes" else "Review notes") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )
                AdminActionRow {
                    Button(
                        onClick = { onApprove(issuedKey.trim(), notes, selectedAttributeIds.toList()) },
                        enabled = issuedKey.isNotBlank() && (!isCounselorRequest || selectedAttributeIds.isNotEmpty()),
                        colors = ButtonDefaults.buttonColors(containerColor = SaharaStrongGreen)
                    ) { Text(if (isEnglish) "Approve & Issue Key" else "Approve") }
                    OutlinedButton(onClick = { onReject(notes) }) { Text(if (isEnglish) "Reject" else "Reject") }
                }
            } else if (request.issuedKey.isNotBlank()) {
                Text(
                    (if (isEnglish) "Issued key: " else "Issue ki hui key: ") + request.issuedKey,
                    color = SaharaStrongGreen,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(
                    onClick = { onApprove(request.issuedKey, notes, selectedAttributeIds.toList()) }
                ) {
                    Text(if (isEnglish) "Sync key record" else "Key sync karein")
                }
            }
        }
    }
}

@Composable
private fun RegistrationReviewInfoSection(
    title: String,
    isEnglish: Boolean,
    initiallyExpanded: Boolean,
    fields: List<Pair<String, String>>,
) {
    AdminInlineMenu(
        title = title,
        subtitle = null,
        tint = SaharaStrongGreen,
        initiallyExpanded = initiallyExpanded,
    ) {
        fields.forEach { (label, value) ->
            RegistrationReviewInfoField(
                label = label,
                value = value,
                isEnglish = isEnglish,
            )
        }
    }
}

@Composable
private fun RegistrationReviewInfoField(
    label: String,
    value: String,
    isEnglish: Boolean,
) {
    val displayValue = value.ifBlank {
        if (isEnglish) "Not provided" else "Not provided"
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            displayValue,
            style = MaterialTheme.typography.bodySmall,
            color = if (value.isBlank()) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun PaymentReviewCard(
    payment: PaymentRequest,
    isEnglish: Boolean,
    hazeState: HazeState,
    onOpenProof: () -> Unit,
    onApprove: (String) -> Unit,
    onReject: (String, Uri?) -> Unit
) {
    var notes by remember(payment.requestId) { mutableStateOf("") }
    var rejectError by remember(payment.requestId) { mutableStateOf("") }
    var attachmentUri by remember(payment.requestId) { mutableStateOf<Uri?>(null) }
    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        attachmentUri = it
    }
    SaharaCard(variant = CardVariant.GLASS, hazeState = hazeState, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(payment.counselorName, fontWeight = FontWeight.Bold)
                Text("PKR ${payment.amountPkr}", fontWeight = FontWeight.Bold, color = SaharaStrongGreen)
            }
            Text(
                "User: ${payment.userId.take(10)}...  Account title: ${payment.accountTitle.ifBlank { "missing" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Reference: ${payment.transactionReference.ifBlank { "not supplied" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (payment.proofUrl.isNotBlank()) {
                AdminEvidenceImage(
                    model = payment.proofUrl,
                    contentDescription = "Uploaded payment screenshot",
                    modifier = Modifier.fillMaxWidth().height(160.dp).background(Color.LightGray.copy(.12f), RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit,
                )
                OutlinedButton(onClick = onOpenProof) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (isEnglish) "Open full proof" else "Proof kholein")
                }
            } else {
                Text(
                    if (isEnglish) "No screenshot supplied." else "Screenshot nahi di gayi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(if (isEnglish) "Verification note" else "Tasdeeq note") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2
            )
            OutlinedButton(onClick = { attachmentPicker.launch("image/*") }) {
                Icon(Icons.Default.AttachFile, null)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (attachmentUri == null) {
                        if (isEnglish) "Attach rejection image" else "Reject note image lagayein"
                    } else {
                        if (isEnglish) "Rejection image attached" else "Reject image lag gayi"
                    }
                )
            }
            if (rejectError.isNotBlank()) {
                Text(rejectError, color = SaharaCoral, style = MaterialTheme.typography.labelSmall)
            }
            AdminActionRow {
                Button(
                    onClick = { onApprove(notes) },
                    colors = ButtonDefaults.buttonColors(containerColor = SaharaStrongGreen)
                ) { Text(if (isEnglish) "Approve & Assign" else "Approve & Assign") }
                OutlinedButton(
                    onClick = {
                        if (notes.isBlank()) {
                            rejectError = if (isEnglish) "A rejection note is required." else "Reject note zaroori hai."
                        } else {
                            rejectError = ""
                            onReject(notes, attachmentUri)
                        }
                    }
                ) { Text(if (isEnglish) "Reject" else "Reject") }
            }
        }
    }
}

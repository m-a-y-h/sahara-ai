package pk.edu.ucp.saharaai.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MinorCrash
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import pk.edu.ucp.saharaai.ui.components.BottomNav
import pk.edu.ucp.saharaai.viewmodels.EmergencyViewModel
import pk.edu.ucp.saharaai.ui.components.ButtonVariant
import pk.edu.ucp.saharaai.ui.components.CardVariant
import pk.edu.ucp.saharaai.ui.components.SaharaButton
import pk.edu.ucp.saharaai.ui.components.SaharaCard
import pk.edu.ucp.saharaai.ui.components.HazeBackButton
import pk.edu.ucp.saharaai.ui.theme.*
import pk.edu.ucp.saharaai.utils.ObservePermissionState
import pk.edu.ucp.saharaai.utils.PermissionCopy
import pk.edu.ucp.saharaai.utils.rememberAppPermissionRequester
import pk.edu.ucp.saharaai.utils.showLocalizedToast

data class Helpline(
    val name: String,
    val descEn: String,
    val descUr: String,
    val number: String,
    val icon: ImageVector
)

@Composable
fun EmergencyScreen(
    navController: NavController,
    onNavigateBack: () -> Unit = { navController.popBackStack() },
    isEnglish: Boolean = false,
    emergencyViewModel: EmergencyViewModel = viewModel()
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val hazeState = remember { HazeState() }

    val makeCall = { number: String ->
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$number")
        }
        context.startActivity(intent)
    }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var pendingLocationShare by remember { mutableStateOf(false) }

    val shareLocationInfo = { lat: Double, lon: Double ->
        val uri = "https://maps.google.com/?q=$lat,$lon"
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, if (isEnglish) "Emergency! My current location is: $uri" else "Hanggami soorat-e-haal! Meri location: $uri")
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, null)
        context.startActivity(shareIntent)
    }

    val fetchLocationAndShare = {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            pendingLocationShare = false
            context.showLocalizedToast(isEnglish, "Fetching location...", "Location li ja rahi hai...")
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        shareLocationInfo(location.latitude, location.longitude)
                    } else {
                        context.showLocalizedToast(isEnglish, "Ensure GPS is on.", "GPS on karein.")
                    }
                }
        }
    }

    val locationPermissionRequester = rememberAppPermissionRequester(
        permission = Manifest.permission.ACCESS_FINE_LOCATION,
        isEnglish = isEnglish,
        copy = PermissionCopy(
            deniedEn = "Location permission was denied.",
            deniedUr = "Location ki ijazat nahi di gayi.",
            settingsEn = "Enable location permission in App settings to share live location.",
            settingsUr = "Live location share karne ke liye App settings mein location ki ijazat dein.",
        ),
        onGranted = fetchLocationAndShare,
    )
    ObservePermissionState(locationPermissionRequester) { granted ->
        if (granted && pendingLocationShare) {
            fetchLocationAndShare()
        }
    }

    val helplines = remember {
        listOf(
            Helpline("Rescue 1122", "Medical & Fire Emergency", "Tibi aur Aag ki Emergency", "1122", Icons.Default.LocalHospital),
            Helpline("Edhi Ambulance", "Emergency Ambulance", "Ambulance Service", "115", Icons.Default.MinorCrash),
            Helpline("Police Help", "Police Emergency", "Police Ki Madad", "15", Icons.Default.LocalPolice),
            Helpline("Umang Pakistan", "Mental Health Counseling", "Zehni Sehat ki Helpline", "03117786264", Icons.Default.Favorite)
        )
    }

    val bgGradient = if (isDark) {
        listOf(SaharaCoral.copy(alpha = 0.2f), MaterialTheme.colorScheme.background.copy(alpha = 0.6f), MaterialTheme.colorScheme.background)
    } else {
        listOf(SaharaCoral.copy(alpha = 0.25f), SaharaPeach.copy(alpha = 0.15f), MaterialTheme.colorScheme.background.copy(alpha = 0.2f))
    }

    val blob1Color = SaharaCoral.copy(alpha = if (isDark) 0.25f else 0.15f)
    val blob2Color = SaharaPeach.copy(alpha = if (isDark) 0.2f else 0.18f)

    val pulseScale = rememberFrameOscillation(1f, 1.08f, 800)

    Box(modifier = Modifier.fillMaxSize()) {
        ScreenBackdrop(hazeState, bgGradient, blob1Color, blob2Color)

        Scaffold(
            bottomBar = { BottomNav(navController = navController, hazeState = hazeState) },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    HazeBackButton(onClick = onNavigateBack, hazeState = hazeState, tint = SaharaCoral)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = if (isEnglish) "Emergency Help" else "Hanggami Madad",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = SaharaCoral
                        )
                        Text(
                            text = if (isEnglish) "You are not alone" else "Aap akele nahi hain",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    SaharaCard(
                        variant = CardVariant.DASHBOARD_GLASS,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Button(
                                onClick = {
                                    makeCall("1122")
                                    val hasPermission = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.ACCESS_FINE_LOCATION
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasPermission) {
                                        val client = LocationServices.getFusedLocationProviderClient(context)
                                        client.getCurrentLocation(
                                            Priority.PRIORITY_HIGH_ACCURACY,
                                            CancellationTokenSource().token
                                        ).addOnSuccessListener { location ->
                                            emergencyViewModel.triggerSOSForSignedInUser(
                                                latitude     = location?.latitude ?: 0.0,
                                                longitude    = location?.longitude ?: 0.0,
                                                locationName = "Unknown",
                                                riskScore    = 100f
                                            )
                                        }
                                    } else {
                                        emergencyViewModel.triggerSOSForSignedInUser(
                                            latitude     = 0.0,
                                            longitude    = 0.0,
                                            locationName = "Unknown",
                                            riskScore    = 100f
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .size(110.dp)
                                    .scale(pulseScale),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = SaharaCoral),
                                elevation = ButtonDefaults.buttonElevation(
                                    defaultElevation = 8.dp,
                                    pressedElevation = 2.dp
                                )
                            ) {
                                Icon(
                                    Icons.Default.Phone,
                                    contentDescription = "Call",
                                    modifier = Modifier.size(48.dp),
                                    tint = Color.White
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Text(
                                text = if (isEnglish) "SOS Emergency" else "SOS Hanggami",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isEnglish) "Call Rescue 1122 immediately" else "Rescue 1122 ko turant call karein",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Button(
                                onClick = {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                        fetchLocationAndShare()
                                    } else {
                                        pendingLocationShare = true
                                        locationPermissionRequester.request()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SaharaCoral.copy(alpha = 0.1f),
                                    contentColor = SaharaCoral
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isEnglish) "Share Live Location" else "Live Location Bhejein",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = SaharaCoral.copy(alpha = 0.1f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            SaharaCoral.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = SaharaCoral
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isEnglish) "Important Notice" else "Zaroori Hidayat",
                                    fontWeight = FontWeight.Bold,
                                    color = SaharaCoral
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isEnglish)
                                        "If you or someone else is in immediate danger, please call emergency services right away. Do not wait."
                                    else
                                        "Agar aap ya koi aur khatre mein hai, turant emergency services ko call karein. Intezar mat karein.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = if (isEnglish) "Helplines in Pakistan" else "Pakistan mein Helplines",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    helplines.forEach { helpline ->
                        val description = if (isEnglish) helpline.descEn else helpline.descUr

                        SaharaCard(
                            variant = CardVariant.DASHBOARD_GLASS,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .background(SaharaCoral.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(helpline.icon, contentDescription = null, tint = SaharaCoral, modifier = Modifier.size(26.dp))
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = helpline.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            val clip = android.content.ClipData.newPlainText("Helpline", helpline.number)
                                            clipboard.setPrimaryClip(clip)
                                            context.showLocalizedToast(isEnglish, "Number copied", "Number copy ho gaya")
                                        },
                                        modifier = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f), CircleShape)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = SaharaCoral, modifier = Modifier.size(20.dp))
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                SaharaButton(
                                    text = if (isEnglish) "Call ${helpline.number}" else "${helpline.number} par Call Karein",
                                    onClick = { makeCall(helpline.number) },
                                    variant = ButtonVariant.DESTRUCTIVE,
                                    isEnglish = isEnglish,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

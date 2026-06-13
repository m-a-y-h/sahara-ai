package pk.edu.ucp.saharaai.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import pk.edu.ucp.saharaai.R
import pk.edu.ucp.saharaai.ui.components.*
import pk.edu.ucp.saharaai.ui.theme.*
import pk.edu.ucp.saharaai.viewmodels.CounselorSetupViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CounselorSetupScreen(
    navController: NavController,
    isEnglish: Boolean,
    counselorKey: String = "",
    setupViewModel: CounselorSetupViewModel = viewModel()
) {
    val isDark = isSystemInDarkTheme()
    val bgHazeState = remember { HazeState() }
    val softTextColor = if (isDark) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.85f)

    
    var fullName      by remember { mutableStateOf("") }
    var feeText       by remember { mutableStateOf("") }
    var ngoName       by remember { mutableStateOf("") }
    var region        by remember { mutableStateOf("") }
    var bio           by remember { mutableStateOf("") }
    val isLoading = setupViewModel.isLoading
    val errorMsg = setupViewModel.errorMsg

    fun saveProfile() {
        if (fullName.isBlank()) { setupViewModel.reportError(if (isEnglish) "Please enter your name." else "Apna naam darj karein."); return }
        if (ngoName.isBlank()) { setupViewModel.reportError(if (isEnglish) "NGO name is required." else "NGO ka naam zaroori hai."); return }
        if (counselorKey.isBlank()) { setupViewModel.reportError(if (isEnglish) "Session error. Please re-enter your key." else "Session error. Dobara key darj karein."); return }
        val fee = feeText.toIntOrNull()
        if (fee == null) { setupViewModel.reportError(if (isEnglish) "Please enter your session fee." else "Apni session fee darj karein."); return }
        if (fee > 5000) { setupViewModel.reportError(if (isEnglish) "No counselor can set a fee above PKR 5000." else "Koi counselor PKR 5000 se zyada fee nahi rakh sakta."); return }

        setupViewModel.saveProfile(
            counselorKey = counselorKey,
            fullName = fullName,
            feePkr = fee,
            ngoName = ngoName,
            region = region,
            bio = bio,
            fallbackError = if (isEnglish) "Error saving profile" else "Profile save nahi hua",
            onSuccess = {
                navController.navigate("counselor-dashboard") {
                    popUpTo("counselor-setup") { inclusive = true }
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {

        Box(modifier = Modifier.fillMaxSize().hazeSource(bgHazeState)) {
            Image(
                painter = painterResource(id = R.drawable.sahara_bg3),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (isDark) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.1f)))
            }
            BackdropBlobs(
                primaryBlob = BackdropBlobSpec(
                    size = 350.dp,
                    offsetX = (-80).dp,
                    offsetY = (-50).dp,
                    color = if (isDark) SaharaGreen.copy(0.15f) else SaharaGreen.copy(0.18f),
                    blurRadius = 80.dp,
                ),
                secondaryBlob = BackdropBlobSpec(
                    size = 400.dp,
                    offsetX = 100.dp,
                    offsetY = 50.dp,
                    color = if (isDark) SaharaSky.copy(0.15f) else SaharaSky.copy(0.22f),
                    alignment = Alignment.BottomEnd,
                    blurRadius = 96.dp,
                ),
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {

            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.25f))
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HazeBackButton(onClick = { navController.popBackStack() }, hazeState = bgHazeState)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isEnglish) "Counselor Setup" else "Counselor Profile",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = SaharaStrongGreen
                        )
                        Text(
                            text = if (isEnglish) "Complete your profile to get started"
                                   else "Shuru karne ke liye apna profile mukammal karein",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                SaharaCard(variant = CardVariant.GLASS, hazeState = bgHazeState, modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                        
                        Text(
                            text = if (isEnglish) "Personal Info" else "Zaati Maloomat",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = SaharaStrongGreen
                        )

                        SetupField(
                            value = fullName,
                            onValueChange = { fullName = it },
                            label = if (isEnglish) "Full Name *" else "Poora Naam *",
                            icon = Icons.Default.Person,
                            isDark = isDark,
                            softTextColor = softTextColor
                        )

                        SetupField(
                            value = region,
                            onValueChange = { region = it },
                            label = if (isEnglish) "Region / City" else "Ilaqa / Shehar",
                            icon = Icons.Default.LocationOn,
                            isDark = isDark,
                            softTextColor = softTextColor
                        )

                        SetupField(
                            value = bio,
                            onValueChange = { bio = it },
                            label = if (isEnglish) "Short Bio (optional)" else "Mukhtasar Taaruf (ikhtiari)",
                            icon = Icons.Default.Info,
                            isDark = isDark,
                            softTextColor = softTextColor,
                            singleLine = false
                        )
                    }
                }

                SaharaCard(variant = CardVariant.GLASS, hazeState = bgHazeState, modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                        Text(
                            text = if (isEnglish) "Organization & Fee" else "Tanzeem aur Fee",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = SaharaStrongGreen
                        )

                        Column {
                            SetupField(
                                value = feeText,
                                onValueChange = { feeText = it.filter(Char::isDigit).take(4) },
                                label = if (isEnglish) "Session Fee (PKR) *" else "Session Fee (PKR) *",
                                icon = Icons.Default.Payments,
                                isDark = isDark,
                                softTextColor = softTextColor,
                                keyboardType = KeyboardType.Number
                            )
                            Text(
                                text = if (isEnglish) "We highly recommend selecting a fee below PKR 1000. No counselor can set a fee above PKR 5000."
                                       else "Hum mashwara dete hain ke fee PKR 1000 se kam rakhein. PKR 5000 se zyada fee allowed nahi.",
                                style = MaterialTheme.typography.labelSmall,
                                color = softTextColor.copy(alpha = 0.6f),
                                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                            )
                        }

                        SetupField(
                            value = ngoName,
                            onValueChange = { ngoName = it },
                            label = if (isEnglish) "NGO / Organization Name *" else "NGO / Tanzeem ka Naam *",
                            icon = Icons.Default.Business,
                            isDark = isDark,
                            softTextColor = softTextColor
                        )
                    }
                }

                if (errorMsg.isNotBlank()) {
                    Text(
                        text = errorMsg,
                        color = SaharaCoral,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = SaharaStrongGreen)
                    }
                } else {
                    SaharaButton(
                        text = if (isEnglish) "Save & Continue" else "Mahfooz Karein",
                        onClick = { saveProfile() },
                        variant = ButtonVariant.DEFAULT,
                        isFullWidth = true,
                        hazeState = bgHazeState,
                        isEnglish = isEnglish
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SetupField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isDark: Boolean,
    softTextColor: Color,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    val inputBg = if (isDark) Color.Black.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.5f)

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = SaharaStrongGreen, modifier = Modifier.size(20.dp)) },
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 3,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = SaharaStrongGreen,
            unfocusedBorderColor = softTextColor.copy(alpha = 0.3f),
            focusedLabelColor    = SaharaStrongGreen,
            unfocusedLabelColor  = softTextColor.copy(alpha = 0.6f),
            focusedTextColor     = softTextColor,
            unfocusedTextColor   = softTextColor,
            cursorColor          = SaharaStrongGreen,
            focusedContainerColor   = inputBg,
            unfocusedContainerColor = inputBg
        )
    )
}

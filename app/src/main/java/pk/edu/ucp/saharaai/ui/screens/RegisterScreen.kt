package pk.edu.ucp.saharaai.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.blur.HazeColorEffect
import pk.edu.ucp.saharaai.R
import pk.edu.ucp.saharaai.ui.components.*
import pk.edu.ucp.saharaai.ui.theme.*
import pk.edu.ucp.saharaai.viewmodels.RegisterViewModel

private val nameRegex = Regex("^[a-zA-Z\\s]*$")
private val emailRegex = Regex("""^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[a-zA-Z]{2,}$""")
private val commonTitles = listOf(
    "syed", "sayyed", "sayyid", "muhammad", "mohammad", "mohd", "muhammed", "mohamed", "mohammed",
    "peer", "doctor", "pir", "qazi", "qaazi", "khan", "bin", "shaikh", "sheikh", "mian", "shah",
    "makhdoom", "hafiz", "raja", "malik", "sardar", "wadera", "mir", "haju", "haaji", "haji",
    "mirza", "ustad", "jan", "khawaja", "dewan", "rana", "chaudhari", "choudhury", "chowdhury",
    "chaudhry", "chohadry", "choudhry", "choudhary", "chohadri", "chouudry", "choudari", "chaudree",
    "chowdhrie", "chawdry", "chowdhary", "chowdhry", "chowdhri", "chowdhari", "chawdhury", "chaudery",
    "rai", "rao", "kanwar", "jam", "arbab", "zardar", "nawab", "qari", "mufti", "allama", "maulana",
    "pirzada", "ghulam", "zaildar", "pasha", "syeda", "bibi", "begum", "beghum", "moulvi", "faqir",
    "faqeer", "hashmi", "hazrat", "bano", "baano", "pirzadi", "sahibzadi", "khanum", "khatoon",
    "sultan", "malak"
)

fun isValidName(name: String): Boolean {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return words.size >= 2 && words.all { it.length >= 3 } && nameRegex.matches(name)
}
private fun isValidEmail(email: String): Boolean = emailRegex.matches(email)

fun getCallingName(fullName: String): String {
    val parts = fullName.trim().split(" ")
    if (parts.isEmpty()) return ""
    val callingName = parts.firstOrNull { part ->
        !commonTitles.contains(part.lowercase().replace(".", ""))
    } ?: parts.first()
    return callingName.replaceFirstChar { it.uppercase() }
}

@Composable
fun RegisterScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToOnboarding: (String, String, String) -> Unit,
    onNavigateToDashboard: (String) -> Unit = {},
    isEnglish: Boolean = false,
    registerViewModel: RegisterViewModel = viewModel()
) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val primaryGreen = if (isDark) SaharaStrongGreen else SaharaGreen

    val bgGradient = if (isDark) {
        listOf(SaharaStrongGreen.copy(alpha = 0.2f), MaterialTheme.colorScheme.background.copy(alpha = 0.6f), MaterialTheme.colorScheme.background)
    } else {
        listOf(SaharaStrongGreen.copy(alpha = 0.25f), SaharaPeach.copy(alpha = 0.1f), MaterialTheme.colorScheme.background.copy(alpha = 0.2f))
    }
    val blob1Color = SaharaStrongGreen.copy(alpha = if (isDark) 0.25f else 0.15f)
    val blob2Color = SaharaSky.copy(alpha = if (isDark) 0.2f else 0.18f)

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isAccepted by remember { mutableStateOf(false) }
    var showToS by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    val isLoading = registerViewModel.isLoading
    val errorMsg = registerViewModel.errorMessage

    val bgHazeState = remember { HazeState() }
    val rootHazeState = remember { HazeState() }

    val hasMinLength = password.length >= 8
    val hasUppercase = password.any { it.isUpperCase() }
    val hasNumber = password.any { it.isDigit() }
    val hasSpecial = password.any { !it.isLetterOrDigit() }

    val isPasswordValid = hasMinLength && hasUppercase && hasNumber && hasSpecial
    val passwordsMatch = password == confirmPassword || confirmPassword.isEmpty()

    val isNameValid = isValidName(name) || name.isEmpty()
    val isEmailValid = isValidEmail(email) || email.isEmpty()

    val isFormValid = isValidName(name) && isValidEmail(email) && isPasswordValid && (password == confirmPassword) && isAccepted

    val blobMotion = rememberBackdropBlobMotion()

    Box(modifier = Modifier.fillMaxSize()) {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = rootHazeState)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = bgHazeState)
                    .background(Brush.verticalGradient(bgGradient))
            ) {
                Box(modifier = Modifier.size(350.dp).offset(x = (-80).dp, y = (-50).dp).primaryBlobMotion(blobMotion).background(Brush.radialGradient(listOf(blob1Color, Color.Transparent))))
                Box(modifier = Modifier.size(400.dp).align(Alignment.BottomEnd).offset(x = 100.dp, y = 50.dp).secondaryBlobMotion(blobMotion).background(Brush.radialGradient(listOf(blob2Color, Color.Transparent))))
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                HazeBackButton(onClick = onNavigateBack, hazeState = bgHazeState, tint = primaryGreen)

                Spacer(modifier = Modifier.height(12.dp))

                Text(if (isEnglish) "Create Account" else "Naya Account", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = primaryGreen)
                Text(if (isEnglish) "Take the first step towards healing" else "Apne behtar kal ki taraf pehla qadam", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(modifier = Modifier.height(24.dp))

                SaharaCard(
                    variant = CardVariant.GLASS,
                    hazeState = bgHazeState,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { newValue ->
                                if (newValue.length <= 32 && newValue.matches(nameRegex)) {
                                    name = newValue.split(" ").joinToString(" ") { word ->
                                        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                                    }
                                    registerViewModel.clearAuthError()
                                }
                            },
                            label = { Text(if (isEnglish) "Full Name" else "Poora Naam") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = primaryGreen) },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            modifier = Modifier.fillMaxWidth().onFocusChanged { _ -> },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            maxLines = 1,
                            isError = !isNameValid,
                            supportingText = { if (!isNameValid && name.isNotEmpty()) Text(if (isEnglish) "Enter at least 2 words, each 3+ characters" else "Kam az kam 2 alfaz darj karein, har lafz 3+ huroof ka ho", style = MaterialTheme.typography.labelSmall) }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it.trim().lowercase(); registerViewModel.clearAuthError() },
                            label = { Text(if (isEnglish) "Email" else "Email") },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = primaryGreen, modifier = Modifier.size(20.dp)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.EmailAddress },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            isError = !isEmailValid,
                            supportingText = { if (!isEmailValid) Text(if (isEnglish) "Enter a valid email address" else "Sahih email address darj karein", style = MaterialTheme.typography.labelSmall) }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; registerViewModel.clearAuthError() },
                            label = { Text(if (isEnglish) "Password" else "Password") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = primaryGreen, modifier = Modifier.size(20.dp)) },
                            trailingIcon = {
                                val image = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(image, contentDescription = "Toggle Password", tint = primaryGreen, modifier = Modifier.size(20.dp))
                                }
                            },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Password },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            isError = !isPasswordValid && password.isNotEmpty(),
                            supportingText = {
                                if (password.isNotEmpty() && !isPasswordValid) {
                                    Text(
                                        buildAnnotatedString {
                                            val invalidColor = SaharaCoral

                                            withStyle(SpanStyle(color = if (hasMinLength) primaryGreen else invalidColor)) {
                                                append(if (isEnglish) "At least 8 chars" else "Kam az kam 8 huroof")
                                            }
                                            withStyle(SpanStyle(color = if (hasUppercase) primaryGreen else invalidColor)) {
                                                append(if (isEnglish) ", 1 uppercase" else ", 1 bara harf")
                                            }
                                            withStyle(SpanStyle(color = if (hasNumber) primaryGreen else invalidColor)) {
                                                append(if (isEnglish) ", 1 number" else ", 1 number")
                                            }
                                            withStyle(SpanStyle(color = if (hasSpecial) primaryGreen else invalidColor)) {
                                                append(if (isEnglish) ", 1 special char" else ", 1 special character")
                                            }
                                        },
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it; registerViewModel.clearAuthError() },
                            label = { Text(if (isEnglish) "Confirm Password" else "Password Dobara") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = primaryGreen, modifier = Modifier.size(20.dp)) },
                            trailingIcon = {
                                val image = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(image, contentDescription = "Toggle Password", tint = primaryGreen, modifier = Modifier.size(20.dp))
                                }
                            },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            isError = !passwordsMatch && confirmPassword.isNotEmpty(),
                            supportingText = { if (!passwordsMatch && confirmPassword.isNotEmpty()) Text(if (isEnglish) "Passwords do not match" else "Password aik jaisa nahi hai", color = SaharaCoral, style = MaterialTheme.typography.labelSmall) }
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = isAccepted, onCheckedChange = { isAccepted = it }, colors = CheckboxDefaults.colors(checkedColor = primaryGreen))
                            val tosText = buildAnnotatedString {
                                if (isEnglish) {
                                    append("I agree to the ")
                                    withLink(LinkAnnotation.Clickable(tag = "tos", styles = TextLinkStyles(style = SpanStyle(color = primaryGreen, fontWeight = FontWeight.Bold)), linkInteractionListener = { showToS = true })) {
                                        append("Terms of Service")
                                    }
                                } else {
                                    withLink(LinkAnnotation.Clickable(tag = "tos", styles = TextLinkStyles(style = SpanStyle(color = primaryGreen, fontWeight = FontWeight.Bold)), linkInteractionListener = { showToS = true })) {
                                        append("Terms of Service")
                                    }
                                    append(" ka/ki paband hoon")
                                }
                            }
                            Text(text = tosText, style = MaterialTheme.typography.bodySmall)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (errorMsg.isNotBlank()) {
                            Text(
                                text = errorMsg,
                                color = SaharaCoral,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        val isButtonEnabled = isFormValid && !isLoading

                        if (isLoading) {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = SaharaStrongGreen)
                            }
                        } else {
                            SaharaButton(
                                text = if (isEnglish) "Sign Up" else "Register Karein",
                                onClick = {
                                    registerViewModel.registerWithEmail(
                                        name, email, password, isEnglish, onNavigateToOnboarding
                                    )
                                },
                                enabled = isButtonEnabled,
                                isFullWidth = true,
                                modifier = Modifier.height(48.dp),
                                variant = if (isButtonEnabled) ButtonVariant.SAHARASTRONGGREENGLASS else ButtonVariant.DEFAULT,
                                hazeState = if (isButtonEnabled) bgHazeState else null,
                                isEnglish = isEnglish
                            )
                        }
                    }
                }

                OrDivider(isEnglish = isEnglish, isDark = isDark)

                GlassyIconButton(
                    iconRes = R.drawable.ic_google,
                    text = if (isEnglish) "Sign up with Google" else "Google se Register Karein",
                    onClick = {
                        registerViewModel.registerWithGoogle(
                            context,
                            isEnglish,
                            onNewUser = { _, email, _ -> onNavigateToDashboard(email) },
                            onExistingUser = onNavigateToDashboard
                        )
                    },
                    hazeState = bgHazeState
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = 32.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                        Text(
                            text = if (isEnglish) "Already have an account? " else "Pehle se registered hain? ",
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isEnglish) "Sign In" else "Sign In",
                            fontWeight = FontWeight.Bold,
                            color = primaryGreen,
                            modifier = Modifier.clickable { onNavigateToLogin() }
                        )
                }
            }
        }

        if (showToS) {
            TermsOfServiceOverlay(
                isEnglish = isEnglish,
                hazeState = rootHazeState,
                onDismiss = { showToS = false },
                onAccept = {
                    isAccepted = true
                    showToS = false
                }
            )
        }
    }
}

@Composable
fun OrDivider(isEnglish: Boolean, isDark: Boolean) {
    val color = if (isDark) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.2f)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = color)
        Text(
            text = if (isEnglish) "OR" else "YA",
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f)
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = color)
    }
}

@Composable
fun GlassyIconButton(
    iconRes: Int? = null,
    iconVector: androidx.compose.ui.graphics.vector.ImageVector? = null,
    text: String? = null,
    onClick: () -> Unit,
    hazeState: HazeState
) {
    val isDark = isSystemInDarkTheme()
    val tint = if (isDark) Color.White else Color.Black.copy(alpha = 0.85f)

    val modifier = if (text != null) Modifier.fillMaxWidth().height(56.dp) else Modifier.size(64.dp)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .hazeEffect(state = hazeState) {
                blurEffect { blurRadius = 16.dp }
            }
            .background(if (isDark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.4f))
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        if (isDark) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.6f),
                        if (isDark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.1f)
                    )
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (text != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (iconRes != null) Image(painter = painterResource(id = iconRes), contentDescription = null, modifier = Modifier.size(24.dp))
                if (iconVector != null) Icon(iconVector, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = text, fontWeight = FontWeight.Bold, color = tint)
            }
        } else {
            if (iconRes != null) Image(painter = painterResource(id = iconRes), contentDescription = null, modifier = Modifier.size(32.dp))
            if (iconVector != null) Icon(iconVector, contentDescription = null, tint = tint, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun TermsOfServiceOverlay(
    isEnglish: Boolean,
    hazeState: HazeState,
    onDismiss: () -> Unit,
    onAccept: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val primaryGreen = if (isDark) SaharaStrongGreen else SaharaGreen
    val glassTint = if (isDark) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.6f)
    val textColor = if (isDark) Color.White else Color.Black.copy(alpha = 0.85f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (isDark) 0.5f else 0.3f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(24.dp))
                .hazeEffect(state = hazeState) {
                    blurEffect {
                        blurRadius = 40.dp
                        noiseFactor = 0.1f
                        colorEffects = listOf(HazeColorEffect.tint(glassTint))
                    }
                }
                .background(glassTint)
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            if (isDark) Color.White.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.8f),
                            if (isDark) Color.White.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.3f)
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Terms of Service",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = primaryGreen
                        )
                        Text(
                            if (isEnglish) "Effective: Jan 2026" else "Aakhri update: Jan 2026",
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor.copy(alpha = 0.7f)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = textColor)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .weight(1f)
                ) {
                    Surface(
                        color = SaharaCoral.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp)) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = SaharaCoral)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (isEnglish) "SAHARA AI is not for medical emergencies. Call 115 or go to the nearest hospital if in danger."
                                else "SAHARA AI emergency ke liye nahi hai. Khatray mein 115 call karein ya qareebi hospital jayen.",
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    ToSSection(
                        title = if (isEnglish) "Acceptance of Terms" else "Terms Ki Qubooliyat",
                        body = if (isEnglish) "By accessing or registering with Sahara AI, you confirm that you agree with and are bound by these Terms of Service. If you do not agree, please do not use the app." else "Sahara AI use karne ka matlab hai ke aap in sharait ko mante hain. Agar nahi mante, to baraye meherbani app istemaal na karein.",
                        titleColor = primaryGreen,
                        textColor = textColor
                    )

                    ToSSection(
                        title = if (isEnglish) "Service Description & Medical Disclaimer" else "Service Ka Bayan aur Medical Disclaimer",
                        body = if (isEnglish) "Sahara AI provides a companion platform and connects users with counselors for non-prescription drug recovery support. It is NOT intended to be a substitute for professional medical advice, clinical diagnosis, or psychiatric treatment. Always seek the advice of your physician regarding a medical condition."
                        else "Sahara AI recovery support aur counselor connection faraham karta hai. Ye kisi proper medical ilaaj, diagnosis ya psychiatric treatment ka mutabadil (substitute) nahi hai. Hamesha apne doctor se mashwara lazmi karein.",
                        titleColor = primaryGreen,
                        textColor = textColor
                    )

                    ToSSection(
                        title = if (isEnglish) "Security, Restrictions & Prohibited Activity" else "Security Aur Mamnoo (Prohibited) Harkaat",
                        body = if (isEnglish) "You agree not to reverse engineer, decompile, decrypt, or disassemble any part of Sahara AI. You are strictly prohibited from launching debugging console attacks, packet sniffing, or attempting to exploit vulnerabilities in the platform. Unauthorized tampering is a violation of intellectual property laws and will result in legal action and account termination."
                        else "Aap is app ko reverse engineer, decrypt, ya decompile nahi kar sakte. Debugging console attacks ya system vulnerabilities dhoondne ki koshish karna sakht mana hai aur qanooni jurm hai. Aisa karne par aapka account foran block kar diya jayega.",
                        titleColor = primaryGreen,
                        textColor = textColor
                    )

                    ToSSection(
                        title = if (isEnglish) "Accountability & Fair Use" else "Aapki Zimmedari",
                        body = if (isEnglish) "You are responsible for maintaining the confidentiality of your account. You agree not to use Sahara AI to transmit malicious code, harass other users or counselors, or engage in any unlawful activities."
                        else "Apne account ki hifazat aapki zimmedari hai. App ke zariye koi malicious code bhejna, kisi counselor ko tang karna ya ghair qanooni harkat karna mana hai.",
                        titleColor = primaryGreen,
                        textColor = textColor
                    )

                    ToSSection(
                        title = if (isEnglish) "Age Requirements" else "Umar Ki Sharait",
                        body = if (isEnglish) "You must be at least 13 years old to use this service. Minors require parental or legal guardian consent to register." else "Ye app istemal karne ke liye aapki umar kam az kam 13 saal honi chahiye. Bachon ke liye waldain (parents) ki ijazat zaroori hai.",
                        titleColor = SaharaCoral,
                        textColor = textColor
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                SaharaButton(
                    text = if (isEnglish) "I Agree" else "Mujhe Manzoor Hai",
                    onClick = onAccept,
                    variant = ButtonVariant.GLASS,
                    isFullWidth = true,
                    isEnglish = isEnglish,
                    hazeState = hazeState
                )
            }
        }
    }
}

@Composable
private fun ToSSection(title: String, body: String, titleColor: Color, textColor: Color) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = titleColor)
        Spacer(modifier = Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = textColor.copy(alpha = 0.85f))
    }
}

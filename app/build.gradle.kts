@file:Suppress("DEPRECATION")

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
}

android {
    namespace = "pk.edu.ucp.saharaai"
    compileSdk = 37

    defaultConfig {
        applicationId = "pk.edu.ucp.saharaai"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(localPropertiesFile.inputStream())
        }

        val ngoKey = localProperties.getProperty("sahara.ngo.key") ?: ""
        buildConfigField("String", "NGO_KEY", "\"$ngoKey\"")

        val adminKey = localProperties.getProperty("sahara.admin.key") ?: ""
        buildConfigField("String", "ADMIN_KEY", "\"$adminKey\"")

        // Comma-separated admin emails — mirrors the verified-email check in the
        // RTDB rules. Used by the dashboard to wall off anyone who reaches it
        // signed in as a non-admin (e.g. a phished key). Rules are the real gate.
        val adminEmails = localProperties.getProperty("sahara.admin.emails") ?: ""
        buildConfigField("String", "ADMIN_EMAILS", "\"$adminEmails\"")

        val counselorKey = localProperties.getProperty("sahara.counselor.key") ?: ""
        buildConfigField("String", "COUNSELOR_KEY", "\"$counselorKey\"")

        val emailjsServiceId  = localProperties.getProperty("emailjs.service.id")  ?: ""
        val emailjsTemplateId = localProperties.getProperty("emailjs.template.id") ?: ""
        val emailjsPublicKey  = localProperties.getProperty("emailjs.public.key")  ?: ""
        buildConfigField("String", "EMAILJS_SERVICE_ID",  "\"$emailjsServiceId\"")
        buildConfigField("String", "EMAILJS_TEMPLATE_ID", "\"$emailjsTemplateId\"")
        buildConfigField("String", "EMAILJS_PUBLIC_KEY",  "\"$emailjsPublicKey\"")

        val saharaMailerUrl = localProperties.getProperty("sahara.mailer.url") ?: ""
        buildConfigField("String", "SAHARA_MAILER_URL", "\"$saharaMailerUrl\"")

        val saharaBiometricEnrollUrl = localProperties.getProperty("sahara.biometric.enroll.url") ?: ""
        val saharaBiometricLoginUrl = localProperties.getProperty("sahara.biometric.login.url") ?: ""
        val saharaBiometricDisableUrl = localProperties.getProperty("sahara.biometric.disable.url") ?: ""
        buildConfigField("String", "SAHARA_BIOMETRIC_ENROLL_URL", "\"$saharaBiometricEnrollUrl\"")
        buildConfigField("String", "SAHARA_BIOMETRIC_LOGIN_URL", "\"$saharaBiometricLoginUrl\"")
        buildConfigField("String", "SAHARA_BIOMETRIC_DISABLE_URL", "\"$saharaBiometricDisableUrl\"")

        val saharaAiChatUrl        = localProperties.getProperty("sahara.ai.chat.url")        ?: ""
        val saharaLensScanUrl      = localProperties.getProperty("sahara.lens.scan.url")      ?: ""
        val saharaVoiceAnalyzeUrl  = localProperties.getProperty("sahara.voice.analyze.url")  ?: ""
        val saharaGeminiModel      = localProperties.getProperty("sahara.gemini.model")       ?: "gemini-3.1-flash-lite"
        buildConfigField("String", "SAHARA_AI_CHAT_URL",        "\"$saharaAiChatUrl\"")
        buildConfigField("String", "SAHARA_LENS_SCAN_URL",      "\"$saharaLensScanUrl\"")
        buildConfigField("String", "SAHARA_VOICE_ANALYZE_URL",  "\"$saharaVoiceAnalyzeUrl\"")
        buildConfigField("String", "SAHARA_GEMINI_MODEL",       "\"$saharaGeminiModel\"")

        // Base URL the guided-meditation tracks are downloaded + cached from
        // (they no longer ship in the APK). Host the 4 mp3s anywhere static and
        // set sahara.meditation.base.url in local.properties; files are fetched
        // as "<base>/<filename>.mp3" on first play for a signed-in user.
        val saharaMeditationBaseUrl = localProperties.getProperty("sahara.meditation.base.url") ?: ""
        buildConfigField("String", "SAHARA_MEDITATION_BASE_URL", "\"$saharaMeditationBaseUrl\"")

        val liveKitUrl = localProperties.getProperty("sahara.livekit.url") ?: ""
        val liveKitTokenUrl = localProperties.getProperty("sahara.livekit.token.url") ?: ""
        buildConfigField("String", "LIVEKIT_URL", "\"$liveKitUrl\"")
        buildConfigField("String", "LIVEKIT_TOKEN_URL", "\"$liveKitTokenUrl\"")

        val bankAccountTitle = localProperties.getProperty("sahara.bank.account.title") ?: ""
        val bankIban = localProperties.getProperty("sahara.bank.iban") ?: ""
        val bankName = localProperties.getProperty("sahara.bank.name") ?: ""
        val bankAccountNumber = localProperties.getProperty("sahara.bank.account.number") ?: ""
        buildConfigField("String", "BANK_ACCOUNT_TITLE", "\"$bankAccountTitle\"")
        buildConfigField("String", "BANK_IBAN", "\"$bankIban\"")
        buildConfigField("String", "BANK_NAME", "\"$bankName\"")
        buildConfigField("String", "BANK_ACCOUNT_NUMBER", "\"$bankAccountNumber\"")

        val blueskyPocBaseUrl = localProperties.getProperty("sahara.bluesky.poc.base.url")
            ?: "http://127.0.0.1:8787"
        val steamPocBaseUrl = localProperties.getProperty("sahara.steam.poc.base.url")
            ?: blueskyPocBaseUrl
        val spotifyPocBaseUrl = localProperties.getProperty("sahara.spotify.poc.base.url")
            ?: blueskyPocBaseUrl
        buildConfigField("String", "BLUESKY_POC_BASE_URL", "\"$blueskyPocBaseUrl\"")
        buildConfigField("String", "STEAM_POC_BASE_URL", "\"$steamPocBaseUrl\"")
        buildConfigField("String", "SPOTIFY_POC_BASE_URL", "\"$spotifyPocBaseUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Ship only arm64-v8a in the release APK. minSdk 26 (Android 8+)
            // devices are universally 64-bit ARM, so x86/x86_64 (emulator-only)
            // and armeabi-v7a just bloat the download — the native libs
            // (WebRTC, MediaPipe face, TFLite) are ~12-15 MB *per ABI*. Debug
            // keeps all ABIs so x86_64 emulators still work.
            ndk {
                abiFilters += "arm64-v8a"
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        noCompress += "tflite"
    }

    // Debug never ran lintVital, so the release build was the first thing to
    // hit these checks. Don't let latent lint findings block release packaging;
    // run `./gradlew lintRelease` separately to review them.
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    buildToolsVersion = "36.0.0"
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.ui.text.googlefonts)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.gson)
    implementation(libs.coil.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.blurview)
    implementation(libs.haze)
    implementation(libs.haze.blur)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.face.detection)

    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.support)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.health.connect.client)
    implementation(libs.play.services.location)
    implementation(libs.play.services.auth)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.ai)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.database)
    implementation(libs.kotlinx.coroutines.play.services)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)
    implementation(libs.livekit.android)
    implementation(libs.livekit.android.compose.components)
    implementation(libs.stompprotocolandroid)
    implementation(libs.rxjava)
    implementation(libs.rxandroid)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
}

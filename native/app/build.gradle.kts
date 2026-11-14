import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// Firebase settings, written by scripts/firebase-config.mjs from google-services.json.
// Missing settings still build: the app then says accounts aren't set up.
val firebase = Properties().apply {
    val f = file("firebase.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun fb(key: String): String = "\"" + (firebase.getProperty(key) ?: "").replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.resz073.sleeptracker"
    compileSdk = 37

    defaultConfig {
        // A separate app id while the native app is being built, so it installs next to the current app.
        // It switches to com.resz073.sleeptracker when the native app replaces it.
        applicationId = "com.resz073.sleeptracker.next"
        minSdk = 24
        targetSdk = 36
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = "0.1.0"

        buildConfigField("String", "FIREBASE_API_KEY", fb("api_key"))
        buildConfigField("String", "FIREBASE_APP_ID", fb("app_id"))
        buildConfigField("String", "FIREBASE_PROJECT_ID", fb("project_id"))
        buildConfigField("String", "FIREBASE_STORAGE_BUCKET", fb("storage_bucket"))
        buildConfigField("String", "FIREBASE_SENDER_ID", fb("sender_id"))
        buildConfigField("String", "WEB_CLIENT_ID", fb("web_client_id"))
        buildConfigField("boolean", "FIREBASE_APP_REGISTERED", (firebase.getProperty("registered") == "true").toString())
    }

    buildTypes {
        release {
            // Unsigned here: CI signs the APK and AAB with your upload key (scripts/sign.sh)
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // The sleep engine is checked against the current app (src/test, reference results from native/scripts/golden)
    testImplementation(libs.junit)
}

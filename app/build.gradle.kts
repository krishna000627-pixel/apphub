import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
// Signing values come from keystore.properties (local) or env vars (GitHub Actions).
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signing(prop: String, env: String): String? = keystoreProps.getProperty(prop) ?: System.getenv(env)
val ksPath = signing("storeFile", "KEYSTORE_PATH")
val ksPass = signing("storePassword", "KEYSTORE_PASSWORD")
val ksAlias = signing("keyAlias", "KEY_ALIAS")
val ksKeyPass = signing("keyPassword", "KEY_PASSWORD")
val hasReleaseKey = ksPath != null && ksPass != null && ksAlias != null && ksKeyPass != null &&
    rootProject.file(ksPath).exists()

android {
    namespace = "com.krishna.apphub"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.krishna.apphub"
        minSdk = 26; targetSdk = 35
        versionCode = 26; versionName = "26.0"
        buildConfigField("String", "APPHUB_CLIENT_SECRET", "\"${project.findProperty("APPHUB_CLIENT_SECRET") ?: ""}\"")
        buildConfigField("String", "APPHUB_CLIENT_ID", "\"${project.findProperty("APPHUB_CLIENT_ID") ?: "Ov23lid24TxTmpt2GX9e"}\"")
    }
    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = rootProject.file(ksPath!!)
                storePassword = ksPass
                keyAlias = ksAlias
                keyPassword = ksKeyPass
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasReleaseKey) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }
    lint { checkReleaseBuilds = false; abortOnError = false }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/versions/9/OSGI-INF/MANIFEST.MF") }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
    implementation("com.goterl:lazysodium-android:5.1.0@aar")
    implementation("net.java.dev.jna:jna:5.14.0@aar")
}

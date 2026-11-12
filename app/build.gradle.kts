import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.parkhere.drivewatch"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.parkhere.drivewatch"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        // Family server. Default is the deployed one; test a local server with -PfamilyServer=http://10.0.2.2:8795
        val familyServer = (project.findProperty("familyServer") as String?) ?: "https://drive.olvend.com"
        buildConfigField("String", "FAMILY_SERVER", "\"$familyServer\"")
    }
    buildTypes {
        release { signingConfig = signingConfigs.getByName("debug") } // ponytail: debug key for contest APK, real keystore before market release
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    // 시연 시나리오: the family server's demo/ folder is also the app's offline copy (drives/, histories/).
    sourceSets["main"].assets.srcDir("../family-server/demo")
    useLibrary("android.car") // AAOS Car API for vehicle speed (not in Pleos Vehicle SDK 2.3.2)
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2025.09.00")
    implementation(bom)
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    implementation("com.google.zxing:core:3.5.3") // QR code for the family invite (encoding only, no camera)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303") // real JSON parsing for scenario tests (android.jar has stubs)
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

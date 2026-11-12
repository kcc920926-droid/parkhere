import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Version from git: versionCode = number of commits, so every pushed build is newer than the last;
// versionName carries the short commit after "+", shown in the demo panel and read by tools/publish-apk.sh.
fun git(vararg args: String) = providers.exec { commandLine("git", *args); isIgnoreExitValue = true }.standardOutput.asText.get().trim()
val gitCount = git("rev-list", "--count", "HEAD").toIntOrNull() ?: 1
val gitSha = git("rev-parse", "--short", "HEAD").ifEmpty { "dev" }

// Team release key, kept outside the repo: ~/.drivewatch/signing.properties (or the file in DW_SIGNING) with
// storeFile, storePassword, keyAlias, keyPassword. Everyone who builds with it can update the same installed app.
// Without it, release falls back to this machine's debug key (an install from another machine then needs an uninstall).
val signing = Properties().apply {
    val f = file(System.getenv("DW_SIGNING") ?: "${System.getProperty("user.home")}/.drivewatch/signing.properties")
    if (f.isFile) f.inputStream().use { load(it) }
}

android {
    namespace = "com.parkhere.drivewatch"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.parkhere.drivewatch"
        minSdk = 28
        targetSdk = 36
        versionCode = gitCount
        versionName = "0.2.0+$gitSha"
        // Family server. Default is the deployed one; test a local server with -PfamilyServer=http://10.0.2.2:8795
        val familyServer = (project.findProperty("familyServer") as String?) ?: "https://drive.olvend.com"
        buildConfigField("String", "FAMILY_SERVER", "\"$familyServer\"")
    }
    signingConfigs {
        if (signing.getProperty("storeFile") != null) create("team") {
            storeFile = file(signing.getProperty("storeFile"))
            storePassword = signing.getProperty("storePassword")
            keyAlias = signing.getProperty("keyAlias")
            keyPassword = signing.getProperty("keyPassword")
        }
    }
    buildTypes {
        release { signingConfig = signingConfigs.findByName("team") ?: signingConfigs.getByName("debug") }
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

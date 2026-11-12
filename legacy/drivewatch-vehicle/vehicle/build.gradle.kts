plugins { id("com.android.library"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "com.drivewatch.vehicle"
    compileSdk = 35
    useLibrary("android.car")
    defaultConfig { minSdk = 28 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    api("com.drivewatch:drivewatch-core:0.1.0")
    implementation("ai.pleos.playground:Vehicle:2.3.2")
}

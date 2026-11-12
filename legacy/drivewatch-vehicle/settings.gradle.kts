pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/") }
    }
}
rootProject.name = "drivewatch-vehicle"
includeBuild("../drivewatch-core")
include(":vehicle", ":harness")

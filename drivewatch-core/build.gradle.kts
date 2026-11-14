import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins { kotlin("jvm") version "2.0.21" }

group = "com.drivewatch"
version = "0.1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies { testImplementation(kotlin("test")) }

tasks.test { useJUnitPlatform() }

// 켜져 있는 가족 서버에 차량 쪽 흐름을 실제로 보내 본다. 테스트에는 포함되지 않는다.
tasks.register<JavaExec>("familyCheck") {
    group = "verification"
    description = "가족 서버에 등록 → 초대 코드 → 요약 보내기를 실제로 해 본다 (-Pserver=주소 -Pwait=초, -Pping=true면 닿는지만 본다)"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.drivewatch.core.FamilyCheck")
    args(
        providers.gradleProperty("server").getOrElse("http://127.0.0.1:8795"),
        providers.gradleProperty("wait").getOrElse("0"),
        providers.gradleProperty("ping").getOrElse("false"),
    )
}

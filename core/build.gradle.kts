import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Offizielles Anthropic-SDK für den optionalen KI-Coach (Claude)
    api(libs.anthropic.java)
    implementation(libs.kotlin.reflect)
    testImplementation(libs.junit)
}

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.30-M1"
}

val exposedVersion: String by project
val ktorVersion: String by project
group = "io.beatmaps"
version = "1.0-SNAPSHOT"

kotlin {
    sourceSets.all {
        languageSettings.useExperimentalAnnotation("kotlin.io.path.ExperimentalPathApi")
        languageSettings.useExperimentalAnnotation("kotlin.time.ExperimentalTime")
        languageSettings.useExperimentalAnnotation("io.ktor.util.KtorExperimentalAPI")
        languageSettings.useExperimentalAnnotation("kotlin.ExperimentalUnsignedTypes")
    }
}

dependencies {
    repositories {
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://artifactory.kirkstall.top-cat.me") }
    }

    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.1.1")

    // Database library
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")

    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-client-serialization:$ktorVersion")
    implementation("io.ktor:ktor-client-jackson:$ktorVersion")

    implementation("org.postgresql:postgresql:42.1.4")
    implementation("com.github.JUtupe:ktor-rabbitmq:0.2.0")
    implementation("com.rabbitmq:amqp-client:5.9.0")

    // Serialization
    implementation("io.ktor:ktor-jackson:$ktorVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.6.1")

    // Multimedia
    implementation("org.jaudiotagger:jaudiotagger:2.0.1")
    implementation("net.coobird:thumbnailator:0.4.13")
    implementation("com.twelvemonkeys.imageio:imageio-jpeg:3.6.1")
    implementation("org.sejda.imageio:webp-imageio:0.1.6")
    implementation("nwaldispuehl:java-lame:3.98.4")

    implementation("org.valiktor:valiktor-core:0.12.0")

    implementation("io.beatmaps:BeatMaps-CommonMP:1.0.6")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "15"
}

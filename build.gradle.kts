import java.net.URI

plugins {
    id("java")
    kotlin("jvm") version(libs.versions.kotlin.get())
    alias(libs.plugins.crowdin)
    alias(libs.plugins.pv.entrypoints)
    alias(libs.plugins.pv.java.templates)
}

dependencies {
    compileOnly(libs.pv)
    annotationProcessor(libs.lombok)
}

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.plasmoverse.com/snapshots")
    maven("https://repo.plasmoverse.com/releases")
}

crowdin {
    url = URI.create("https://github.com/plasmoapp/plasmo-voice-crowdin/archive/refs/heads/addons.zip").toURL()
    sourceFileName = "server/whisper.toml"
    resourceDir = "whisper/languages"
    createList = true
}

tasks {
    jar {
        enabled = false
    }

    shadowJar {
        archiveClassifier.set("")
    }

    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(8))
    }
}

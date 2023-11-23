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
    projectId = "plasmo-voice-addons"
    sourceFileName = "server/whisper.toml"
    resourceDir = "whisper/languages"
    createList = true
}

tasks {
    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(8))
    }
}

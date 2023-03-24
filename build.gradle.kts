plugins {
    id("java")
    kotlin("jvm") version("1.6.10")
    id("su.plo.crowdin.plugin") version("1.0.0")
    id("su.plo.voice.plugin") version("1.0.0")
}

group = "su.plo"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()

    maven("https://repo.plo.su")
}

dependencies {
    compileOnly("com.google.guava:guava:31.1-jre")
    compileOnly("org.jetbrains:annotations:23.0.0")
    compileOnly("org.projectlombok:lombok:1.18.24")

    compileOnly("su.plo.voice.api:server:2.0.0+ALPHA")
    compileOnly("su.plo.config:config:1.0.0")

    annotationProcessor("org.projectlombok:lombok:1.18.24")
    annotationProcessor("com.google.guava:guava:31.1-jre")
    annotationProcessor("com.google.code.gson:gson:2.9.0")
}

plasmoCrowdin {
    projectId = "plasmo-voice-addons"
    sourceFileName = "server/whisper.toml"
    resourceDir = "whisper/languages"
    createList = true
}

tasks {
    processResources {
        dependsOn(plasmoCrowdinDownload)
    }

    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(8))
    }
}

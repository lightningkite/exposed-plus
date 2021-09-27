plugins {
    kotlin("jvm") version "1.5.10"
}

group = "com.lightningkite"
version = "1.0-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    api("org.jetbrains.exposed", "exposed-core", "0.35.1-PLUS")
}
plugins {
    kotlin("jvm")
}

version = "1.0-SNAPSHOT"

dependencies {
    implementation(kotlin("stdlib"))
    api("org.jetbrains.exposed", "exposed-core", "0.35.1")
    api("org.jetbrains.exposed", "exposed-jdbc", "0.35.1")
}
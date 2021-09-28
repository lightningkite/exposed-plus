plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp") version "1.5.31-1.0.0"
}

version = "1.0-SNAPSHOT"

dependencies {
    implementation(kotlin("stdlib"))
    api(project(":runtime"))
    ksp(project(":processor"))
    api("org.jetbrains.exposed", "exposed-core", "0.35.1")
    api("org.jetbrains.exposed", "exposed-jdbc", "0.35.1")
    api("com.h2database:h2:1.4.197")
}

kotlin {
    sourceSets.main {
        kotlin.srcDir("build/generated/ksp/main/kotlin")
    }
    sourceSets.test {
        kotlin.srcDir("build/generated/ksp/test/kotlin")
    }
}
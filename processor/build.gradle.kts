plugins {
    kotlin("jvm")
}

version = "1.0-SNAPSHOT"
repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.devtools.ksp:symbol-processing-api:1.5.31-1.0.0")
}

plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp") version "1.5.31-1.0.0"
    `maven-publish`
}

version = "1.0-SNAPSHOT"

dependencies {
    implementation(kotlin("stdlib"))
    kspTest(project(":processor"))
    api(project(":server-runtime"))
    api("org.jetbrains.exposed", "exposed-core", "0.35.1")
    api("org.jetbrains.exposed", "exposed-jdbc", "0.35.1")
    api("io.lettuce:lettuce-core:6.1.5.RELEASE")
    api("org.jetbrains.kotlinx", "kotlinx-coroutines-core", "1.5.2")
    api("org.jetbrains.kotlinx", "kotlinx-coroutines-reactive", "1.5.2")
    testImplementation("com.h2database:h2:1.4.197")
    testImplementation(kotlin("test"))
}

ksp {
    arg("generateKeys", "true")
    arg("generateExposed", "true")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = project.name.toString()
            version = project.version.toString()
            from(components["kotlin"])
        }
    }
}

kotlin {
    sourceSets.main {
        kotlin.srcDir("build/generated/ksp/main/kotlin")
    }
    sourceSets.test {
        kotlin.srcDir("build/generated/ksp/test/kotlin")
    }
}

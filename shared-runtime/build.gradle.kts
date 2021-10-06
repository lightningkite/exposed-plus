plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp") version "1.5.31-1.0.0"
    `maven-publish`
}

version = "1.0-SNAPSHOT"

dependencies {
    implementation(kotlin("stdlib"))
    kspTest(project(":processor"))
    testImplementation(kotlin("test"))
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

ksp {
    arg("generateKeys", "true")
    arg("generateExposed", "false")
}

kotlin {
    sourceSets.main {
        kotlin.srcDir("build/generated/ksp/main/kotlin")
    }
    sourceSets.test {
        kotlin.srcDir("build/generated/ksp/test/kotlin")
    }
}

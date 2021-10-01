plugins {
    kotlin("jvm")
    `maven-publish`
}

version = "1.0-SNAPSHOT"
repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.devtools.ksp:symbol-processing-api:1.5.31-1.0.0")
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
plugins {
    kotlin("jvm")
    `maven-publish`
}

version = "1.0-SNAPSHOT"

dependencies {
    implementation(kotlin("stdlib"))
    api("org.jetbrains.exposed", "exposed-core", "0.35.1")
    api("org.jetbrains.exposed", "exposed-jdbc", "0.35.1")
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
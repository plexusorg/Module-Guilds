plugins {
    java
    `maven-publish`
    id("com.gradleup.shadow") version "9.6.1"
}

repositories {

    maven {
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }

    maven {
        url = uri("https://nexus.telesphoreo.me/repository/plex/")
    }

    mavenCentral()

    maven {
        url = uri("https://repo.infernalsuite.com/repository/maven-snapshots/")
    }
}

dependencies {
    compileOnly("org.projectlombok:lombok:1.18.46")
    annotationProcessor("org.projectlombok:lombok:1.18.46")
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    implementation("org.apache.commons:commons-lang3:3.20.0")
    compileOnly("dev.plex:api:2.0-SNAPSHOT")
    compileOnly("com.infernalsuite.asp:api:4.0.0-SNAPSHOT")
    implementation("com.infernalsuite.asp:file-loader:4.0.0-SNAPSHOT")
    implementation("org.jetbrains:annotations:26.1.0")
}

group = "dev.plex"
version = "2.0-SNAPSHOT"
description = "The Guilds module for Plex"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

tasks.getByName<Jar>("jar") {
    archiveBaseName.set("Module-Guilds")
    archiveVersion.set("")
}

tasks.shadowJar {
    archiveBaseName.set("Module-Guilds")
    archiveClassifier.set("")
    archiveVersion.set("")
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks {
    compileJava {
        options.encoding = Charsets.UTF_8.name()
    }
    javadoc {
        options.encoding = Charsets.UTF_8.name()
    }
    processResources {
        filteringCharset = Charsets.UTF_8.name()
    }
}

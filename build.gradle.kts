plugins {
    java
    checkstyle
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
    compileOnly("io.github.scissorsmc:scissors-asp-api:${providers.gradleProperty("scissorsAspApiVersion").getOrElse("26.2")}")
    implementation("org.apache.commons:commons-lang3:3.20.0")
    compileOnly("dev.plex:api:2.0-SNAPSHOT") {
        exclude(group = "io.papermc.paper", module = "paper-api")
    }
    implementation("org.jetbrains:annotations:26.1.0")
}

group = "dev.plex"
version = "2.0-SNAPSHOT"
description = "The Guilds module for Plex"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

checkstyle {
    toolVersion = "14.1.0"
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
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

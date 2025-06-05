plugins {
    java
    application
    id("me.qoomon.git-versioning") version "6.4.4"
    id("com.gorylenko.gradle-git-properties") version "2.4.2"
    id("io.freefair.lombok") version "8.11"
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("maven-publish")
}

group = "io.github.alkoleft"
version = "0,1.0-SNAPSHOT"

gitVersioning.apply {
    refs {
        considerTagsOnBranches = true
        tag("v(?<tagVersion>[0-9].*)") {
            version = "\${ref.tagVersion}\${dirty}"
        }
        branch(".+") {
            version = "\${ref}-\${commit.short}\${dirty}"
        }
    }

    rev {
        version = "\${commit.short}\${dirty}"
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
    mavenLocal()
    maven(url = "https://jitpack.io")
}
val JACKSON_VERSION = "2.15.2"

dependencies {
    // CLI
    implementation("info.picocli", "picocli", "4.7.5")

    // Spring
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("info.picocli:picocli-spring-boot-starter:4.7.6")

    // HBK
    implementation(project(":bsl-context"))

    // JSON/XML
    implementation("com.fasterxml.jackson.core:jackson-core:$JACKSON_VERSION")
    implementation("com.fasterxml.jackson.core:jackson-databind:$JACKSON_VERSION")
    implementation("com.fasterxml.jackson.core:jackson-annotations:$JACKSON_VERSION")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$JACKSON_VERSION")

    // Logging
    implementation("org.slf4j", "slf4j-api", "1.7.30")

    // Tests
    testImplementation("org.slf4j", "slf4j-log4j12", "1.7.30")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.8.0")

}

tasks.test {
    useJUnitPlatform()

    testLogging {
        events("passed", "skipped", "failed", "standard_error")
    }
}

tasks.jar {
    enabled = true
    archiveClassifier.set("")
}

tasks.bootJar {
    manifest {
        mainClass = "ru.alkoleft.context.App"
    }
}

publishing {
    repositories {
        maven {
            name = "monaco-bsl-context"
            url = uri("https://maven.pkg.github.com/alkoleft/monaco-bsl-context")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("USERNAME")
                password = project.findProperty("gpr.key") as String? ?: System.getenv("TOKEN")
            }
        }
    }
    publications {
        register<MavenPublication>("gpr") {
            from(components["java"])
        }
    }
}

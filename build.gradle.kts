plugins {
    java
    application
    id("me.qoomon.git-versioning") version "6.4.4"
    id("com.gorylenko.gradle-git-properties") version "2.4.2"
    id("io.freefair.lombok") version "8.11"
    id("org.springframework.boot") version "3.5.0"
    id("io.spring.dependency-management") version "1.1.7"
    id("maven-publish")
}

group = "io.github.alkoleft"
version = "0.1.0-SNAPSHOT"

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

extra["springAiVersion"] = "1.0.0"

val JACKSON_VERSION = "2.15.2"

dependencies {
    // CLI
    implementation("info.picocli", "picocli", "4.7.5")

    // Spring
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("info.picocli:picocli-spring-boot-starter:4.7.6")
    
    // Spring AI MCP Server
    implementation("org.springframework.ai:spring-ai-starter-mcp-server")

    // HBK  
    implementation("com.github._1c_syntax.bsl:bsl-context:1.0-SNAPSHOT")

    // JSON/XML
    implementation("com.fasterxml.jackson.core:jackson-core:$JACKSON_VERSION")
    implementation("com.fasterxml.jackson.core:jackson-databind:$JACKSON_VERSION")
    implementation("com.fasterxml.jackson.core:jackson-annotations:$JACKSON_VERSION")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$JACKSON_VERSION")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.18")
    implementation("org.codehaus.janino:janino:3.1.12")
    
    // Reactor Core для Spring AI MCP
    implementation("io.projectreactor:reactor-core:3.6.11")

    // ANTLR - принудительное управление версиями для исправления несоответствия
    implementation("org.antlr:antlr4-runtime:4.9.3")
    implementation("org.antlr:antlr4:4.9.3")

    // Tests
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.slf4j", "slf4j-log4j12", "1.7.30")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.8.0")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
    }
}

tasks.test {
    useJUnitPlatform()

    testLogging {
        events("passed", "skipped", "failed", "standard_error")
    }
}

tasks.jar {
    enabled = false
    archiveClassifier.set("plain")
}

tasks.bootJar {
    enabled = true
    archiveClassifier.set("")
    mainClass.set("ru.alkoleft.context.platform.Main")
}

// Исправление зависимостей для задач распространения
tasks.named("bootDistZip") {
    dependsOn("bootJar")
}

tasks.named("bootDistTar") {
    dependsOn("bootJar")
}

tasks.named("bootStartScripts") {
    dependsOn("bootJar")
}

tasks.named("startScripts") {
    dependsOn("bootJar")
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

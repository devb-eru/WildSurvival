import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket

plugins {
    id("java-library")
    id("com.gradleup.shadow") version "9.4.2"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.69-stable")
    implementation("com.google.code.gson:gson:2.13.2")

    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

val checkDevelopmentServerStopped = tasks.register("checkDevelopmentServerStopped") {
    group = "verification"
    description = "Prevents rebuilding the plugin jar while the development Paper server is running."
    inputs.property("port", providers.gradleProperty("runServerPort").orElse("25565"))

    doLast {
        val port = inputs.properties.getValue("port").toString().toInt()
        val serverRunning = try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", port), 300)
            }
            true
        } catch (_: ConnectException) {
            false
        }

        if (serverRunning) {
            throw GradleException(
                "Paper development server is already using port $port. " +
                    "Stop it with /stop before building or running another server."
            )
        }
    }
}

tasks {
    test {
        useJUnitPlatform()
    }

    jar {
        archiveClassifier.set("plain")
        dependsOn(checkDevelopmentServerStopped)
    }

    build {
        dependsOn(shadowJar)
    }

    shadowJar {
        archiveClassifier.set(providers.gradleProperty("wsArtifactClassifier").orElse(""))
        dependsOn(checkDevelopmentServerStopped)
        relocate("com.google.gson", "com.lsc.corp.wsplugin.lib.gson")
    }

    runServer {
        dependsOn(checkDevelopmentServerStopped)
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("26.1.2")
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    processResources {
        val props = mapOf("version" to version )
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    register<JavaExec>("validatePrototypeContent") {
        group = "verification"
        description = "Validates the embedded ws-prototype-r1 bundle."
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("com.lsc.corp.wsplugin.content.ContentValidationCli")
        args(layout.projectDirectory.dir("src/main/resources/content/ws-prototype-r1").asFile.absolutePath)
        dependsOn(classes)
    }

    register<JavaExec>("validateProductionContent") {
        group = "verification"
        description = "Validates the embedded 70-file ws-content-r2 production catalog bundle."
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("com.lsc.corp.wsplugin.content.ProductionContentValidationCli")
        args(layout.projectDirectory.dir("src/main/resources/content/ws-content-r2").asFile.absolutePath)
        dependsOn(classes)
    }

    check {
        dependsOn("validatePrototypeContent")
        dependsOn("validateProductionContent")
    }
}

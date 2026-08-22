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

tasks {
    test {
        useJUnitPlatform()
    }

    build {
        dependsOn(shadowJar)
    }

    shadowJar {
        archiveClassifier.set("")
        relocate("com.google.gson", "com.lsc.corp.wsplugin.lib.gson")
    }

    runServer {
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

    check {
        dependsOn("validatePrototypeContent")
    }
}

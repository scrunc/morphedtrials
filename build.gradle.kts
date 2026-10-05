plugins {
    `java`
}

group = "dev.servereer"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    mavenLocal()   // MorphCore is published here via `./gradlew publishToMavenLocal` in morphcore/source
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    // MorphCore (plugin_customized/morphcore) — softdepend in plugin.yml. `compileOnly`: the real classes
    // are resolved at RUNTIME from MorphCore's own classloader, never bundled here. Every reference to
    // this lives in MTBridge.java only — see its javadoc for why that matters.
    compileOnly("dev.servereer:morphcore:1.0.0")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.processResources {
    inputs.property("version", version)
    filesMatching("plugin.yml") {
        expand("version" to version)
    }
}

tasks.jar {
    archiveBaseName.set("MorphedTrials")
}

plugins {
    kotlin("jvm") version "2.1.0"
}

group = "de.frohnmeyer-wds"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.github.javaparser:javaparser-symbol-solver-core:3.26.3")
    implementation("net.sourceforge.plantuml:plantuml:1.2025.0")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(23)
}
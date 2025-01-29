plugins {
    kotlin("jvm") version "2.1.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    `maven-publish`
}

group = "de.frohnmeyer-wds"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.github.javaparser:javaparser-symbol-solver-core:3.26.3")
    implementation("net.sourceforge.plantuml:plantuml:1.2025.0")
}

kotlin {
    jvmToolchain(21)
}

tasks {
    withType<Jar> {
        manifest {
            attributes["Main-Class"] = "de.frohnmeyerwds.MainKt"
        }
    }

    val moveArtifacts by creating(Copy::class) {
        from(shadowJar)
        into("pages")
        rename { "cdg.jar" }
    }
}

allprojects {
    afterEvaluate {
        publishing {
            repositories {
                maven {
                    name = "GitHubPackages"
                    url = uri("https://maven.pkg.github.com/Solidarische-Raumnutzung/ClassDiagramGenerator")
                    credentials {
                        username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
                        password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
                    }
                }
            }
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "doctex"
            from(components["kotlin"])
        }
    }
}
plugins {
    kotlin("multiplatform") version "1.7.20"
    kotlin("plugin.serialization") version "1.7.20"
    id("maven-publish")
}

group = "io.github.versi.kgcsfetcher"
version = "0.0.9"

repositories {
    mavenCentral()
    mavenLocal()
    maven { url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap") }
    maven { url = uri("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev/") }
    maven {
        credentials {
            val nexusUser: String? by project
            val nexusPassword: String? by project
            username = (System.getenv("NEXUS_USERNAME") ?: System.getenv("NEXUS_USER") ?: nexusUser) as String
            password = (System.getenv("NEXUS_PASSWORD") ?: System.getenv("NEXUS_PASS") ?: nexusPassword) as String
        }
        url = uri("https://nexus.mtvi.com/nexus/content/repositories/releases/")
    }
}

publishing {
    repositories {
        maven {
            name = "nexusReleases"
            credentials {
                val nexusUser: String? by project
                val nexusPassword: String? by project
                username = System.getenv("NEXUS_USERNAME") ?: System.getenv("NEXUS_USER") ?: nexusUser
                password = System.getenv("NEXUS_PASSWORD") ?: System.getenv("NEXUS_PASS") ?: nexusPassword
            }
            url = uri(System.getenv("NEXUS_URL") ?: "")
        }
    }
}

kotlin {
    linuxX64()
    macosX64()

    sourceSets {
        val commonMain by getting
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val desktopMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")
                implementation("org.jetbrains.kotlinx:atomicfu:0.18.5")
                implementation("io.github.versi.kurl:kurl:0.0.27")
                implementation("com.paramount.kjwt:kjwt:0.0.6")
            }
        }
        val desktopTest by creating {
            dependsOn(commonTest)
            dependsOn(desktopMain)
        }
        val linuxX64Main by getting {
            dependsOn(desktopMain)
        }
        val linuxX64Test by getting {
            dependsOn(desktopTest)
        }
        val macosX64Main by getting {
            dependsOn(desktopMain)
        }
        val macosX64Test by getting {
            dependsOn(desktopTest)
        }
    }
}
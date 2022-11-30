import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("multiplatform") version "1.7.20"
    kotlin("plugin.serialization") version "1.7.20"
    id("maven-publish")
}

group = "io.github.versi.kgcsfetcher"
version = "0.0.1"

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
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "1.8"
        }
        withJava()
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
    js(BOTH) {
        browser {
            commonWebpackConfig {
                cssSupport.enabled = true
            }
        }
    }
    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")
    val nativeTarget = when {
        hostOs == "Mac OS X" -> macosX64("native")
        hostOs == "Linux" -> linuxX64("native")
        isMingwX64 -> mingwX64("native")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    nativeTarget.apply {
        compilations.getByName("main") {
            cinterops {
                val libjwt by creating {
                    defFile(project.file("src/nativeInterop/cinterop/libjwt.def"))
                    packageName("io.github.versi.kgcsfetcher.jwt")
                }
            }
        }
    }


    sourceSets {
        val commonMain by getting
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting
        val jvmTest by getting
        val jsMain by getting
        val jsTest by getting
        val nativeMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.3.3")
                implementation("org.jetbrains.kotlinx:atomicfu:0.17.1")
                if (hostOs == "Linux") {
                    implementation("io.github.versi.kurl:kurl:0.0.22-test")
                } else {
                    implementation("io.github.versi.kurl:kurl-macosx64:0.0.22-test")
                }
            }
        }
        val nativeTest by getting
    }
}
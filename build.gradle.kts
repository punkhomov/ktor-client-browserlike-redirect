plugins {
    kotlin("multiplatform") version "1.9.22"
    `maven-publish`
}

group = "punkhomov.ktor.client.plugins"
version = System.getenv("GITHUB_REF")?.split('/')?.last() ?: "development"

repositories {
    mavenCentral()
}

kotlin {
    jvm {
        jvmToolchain(17)
    }

    js {
        nodejs()
        browser()

        binaries.library()
    }

    iosArm64()
    iosX64()
    iosSimulatorArm64()

    watchosArm32()
    watchosArm64()
    watchosX64()
    watchosSimulatorArm64()

    tvosArm64()
    tvosX64()
    tvosSimulatorArm64()

    macosX64()
    macosArm64()

    linuxX64()

    mingwX64()

    sourceSets {
        commonMain {
            dependencies {
                compileOnly("io.ktor:ktor-client-core:2.3.9")
            }
        }

        commonTest {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-test")
                implementation("io.ktor:ktor-client-mock:2.3.9")
            }
        }
    }
}

System.getenv("GITHUB_REPOSITORY")?.let {
    publishing {
        repositories {
            maven {
                name = "github"
                url = uri("https://maven.pkg.github.com/$it")
                credentials(PasswordCredentials::class)
            }
        }
    }
}
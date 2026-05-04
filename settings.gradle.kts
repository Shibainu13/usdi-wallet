rootProject.name = "usdi_wallet"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
//        mavenLocal()
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/Shibainu13/sdk-kmp")
            credentials {
                username = providers.gradleProperty("gpr.user").getOrElse("")
                password = providers.gradleProperty("gpr.key").getOrElse("")
            }
        }
        maven {
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
            mavenContent { snapshotsOnly() }
        }
        maven {
            url = uri("https://maven.pkg.github.com/LF-Decentralized-Trust-labs/aries-uniffi-wrappers")
            credentials {
                username = providers.gradleProperty("gpr.user").getOrElse("")
                password = providers.gradleProperty("gpr.key").getOrElse("")
            }
        }
        mavenCentral()
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
                includeGroupAndSubgroups("org.multipaz")
            }
        }

    }
}

include(":composeApp")
include(":shared")
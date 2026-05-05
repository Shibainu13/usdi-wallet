import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation("androidx.fragment:fragment-ktx:1.8.5")
            implementation("androidx.recyclerview:recyclerview:1.3.2")
            implementation("com.google.android.material:material:1.12.0")
            implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
            implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
            implementation("androidx.constraintlayout:constraintlayout:2.2.0")
            implementation("androidx.navigation:navigation-compose:2.8.5")
            implementation(project.dependencies.platform("androidx.compose:compose-bom:2024.10.00"))
            implementation("androidx.compose.material3:material3")

            // Use the explicit version if the BOM is failing to resolve it
            implementation("androidx.compose.material:material-icons-extended:1.7.5")
        }
        commonMain.dependencies {
            implementation("org.jetbrains.compose.runtime:runtime:1.10.0")
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.preview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(projects.shared)
            implementation("co.touchlab:kermit:2.0.8")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "com.dev.usdi_wallet"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.dev.usdi_wallet"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    buildFeatures {
        viewBinding = true
        dataBinding = true
    }
    packaging {
        resources {
            pickFirsts += "google/protobuf/*.proto"
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            pickFirsts += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}

configurations.all {
    exclude(group = "org.bouncycastle", module = "bcprov-jdk15on")
    exclude(group = "org.bouncycastle", module = "bcprov-jdk15to18")
    exclude(group = "org.bouncycastle", module = "bcprov-jdk14")
    exclude(group = "com.nimbusds", module = "nimbus-jose-jwt")
    exclude(group = "net.jcip", module = "jcip-annotations")

    resolutionStrategy.eachDependency {
        if (requested.group == "io.ktor") {
            useVersion("2.3.12")
            because("Identus SDK requires Ktor 2.x — overrides EUDI strict constraints")
        }
        if (requested.group == "org.jetbrains.kotlinx" && requested.name == "kotlinx-datetime") {
            useVersion("0.6.1")
            because("Identus SDK requires kotlinx-datetime at runtime")
        }
    }
}
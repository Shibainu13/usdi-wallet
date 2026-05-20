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
            implementation(libs.androidx.fragment.ktx)
            implementation(libs.androidx.recyclerview)
            implementation(libs.material)
            implementation(libs.androidx.lifecycle.viewmodel.ktx)
            implementation(libs.androidx.lifecycle.runtime.ktx)
            implementation(libs.androidx.constraintlayout)
            implementation(libs.androidx.navigation.compose)

            // Compose BOM
            implementation(project.dependencies.platform(libs.compose.bom))
            implementation(libs.androidx.compose.material3)

            // Use the explicit version if the BOM is failing to resolve it
            implementation(libs.androidx.compose.material.icons.extended)
        }
        commonMain.dependencies {
            implementation(libs.jetbrains.compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.preview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(projects.shared)
            implementation(libs.kermit)
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
            pickFirsts += "com/nimbusds/jose/**"
            pickFirsts += "com/nimbusds/jwt/**"
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
    exclude(group = "net.jcip", module = "jcip-annotations")

    resolutionStrategy.eachDependency {
        if (requested.group == "io.ktor") {
            useVersion(libs.versions.ktor.override.get())
            because("Identus SDK requires Ktor 2.x — overrides EUDI strict constraints")
        }
        if (requested.group == "org.jetbrains.kotlinx" && requested.name == "kotlinx-datetime") {
            useVersion(libs.versions.datetime.get())
            because("Identus SDK requires kotlinx-datetime at runtime")
        }
    }
}
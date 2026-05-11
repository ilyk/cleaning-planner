plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.ilyk.cleaningplanner"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ilyk.cleaningplanner"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "CLARA_STREAM_URL", "\"wss://your-production-url.com/v1/clara/stream\"")
            buildConfigField("String", "CLARA_API_BASE_URL", "\"https://your-production-url.com\"")
        }
        debug {
            isMinifyEnabled = false
            buildConfigField("String", "CLARA_STREAM_URL", "\"ws://100.101.151.24:8090/v1/clara/stream\"")
            buildConfigField("String", "CLARA_API_BASE_URL", "\"http://100.101.151.24:8090\"")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core modules
    implementation(project(":core:model"))
    implementation(project(":core:ui"))
    implementation(project(":core:common"))

    // Data modules
    implementation(project(":data:database"))
    implementation(project(":data:network"))
    implementation(project(":data:repository"))

    // Feature modules
    implementation(project(":feature:auth"))
    implementation(project(":feature:clara"))
    implementation(project(":feature:household"))
    implementation(project(":feature:rooms"))
    implementation(project(":feature:qr"))
    implementation(project(":feature:kidmode"))
    implementation(project(":feature:board"))
    implementation(project(":feature:printables"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:setup"))

    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.navigation)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // WorkManager
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging.ktx)
    implementation(libs.firebase.analytics.ktx)

    // DataStore
    implementation(libs.datastore.preferences)
    implementation(libs.datastore.core)

    // ML Kit
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.zxing.core)
    implementation(libs.zxing.android)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    
    // Kotlinx libraries
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)

    // CameraX
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}

ksp {
    arg("room.incremental", "true")
    arg("room.expandProjection", "true")
}


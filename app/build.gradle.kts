plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")

}

android {
    namespace = "com.example.qotd"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.qotd"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.auth.ktx)
    implementation(libs.androidx.runtime.livedata)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.storage)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.auth)
    implementation(libs.androidx.work)
    implementation(libs.material) // for navbar
    implementation(libs.material3) // for BottomNavigation, Material 3 components
    implementation("androidx.compose.material:material:1.3.1") // Add this if missing
    implementation("androidx.compose.ui:ui:1.3.1") // Ensure this is also present
    implementation("androidx.compose.material:material-icons-extended:1.3.1") // If using icons in BottomNavigation
    implementation(platform("androidx.compose:compose-bom:2023.1.0")) // The BOM (Bill of Materials) for Compose
    implementation("androidx.compose.material:material") // For Material Components
    implementation("androidx.compose.ui:ui") // For UI components
    implementation("androidx.compose.foundation:foundation") // For other foundation components
    implementation("androidx.compose.runtime:runtime") // For Compose runtime
    implementation("androidx.compose.material3:material3:1.0.0") // Ensure this is included
    implementation("androidx.compose.ui:ui:1.3.1")
    implementation("androidx.compose.foundation:foundation:1.3.1")
    implementation("androidx.compose.runtime:runtime:1.3.1")
}
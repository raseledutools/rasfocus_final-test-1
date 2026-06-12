plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.rasel.RasFocus"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.rasel.RasFocus"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // FIX: OAuth client ID moved out of source code into BuildConfig.
        // Set GOOGLE_WEB_CLIENT_ID in local.properties (never commit that file).
        // Fallback to env var for CI pipelines.
        val googleClientId = project.findProperty("GOOGLE_WEB_CLIENT_ID") as String?
            ?: System.getenv("GOOGLE_WEB_CLIENT_ID")
            ?: ""
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleClientId\"")
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        // 1.5.14 is the correct compiler extension for Kotlin 1.9.24 + BOM 2024.06.00
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core Android
    implementation("androidx.camera:camera-camera2:1.3.3")
    implementation("androidx.camera:camera-lifecycle:1.3.3")
    implementation("androidx.camera:camera-view:1.3.3")
    implementation("com.google.zxing:core:3.5.2")
    implementation("androidx.core:core-ktx:1.13.1")

    // Lifecycle — 2.8.x requires activity-compose 1.9.x for full compat
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.0")

    // SavedState — explicit dep for setViewTreeSavedStateRegistryOwner extension
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // ViewModel + Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-service:2.8.2")

    // Firebase & Play Services
    implementation(platform("com.google.firebase:firebase-bom:32.7.3"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Legacy GoogleSignIn API — used in MainActivity.kt (GoogleSignIn, GoogleSignInOptions, GoogleSignInAccount)
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // Cloudinary
    implementation("com.cloudinary:cloudinary-android:2.5.0")

    // Google Location Services
    implementation("com.google.android.gms:play-services-location:21.2.0")

    // Coil — Image loading
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // Gson
    implementation("com.google.code.gson:gson:2.10.1")

    // AppCompat — AppCompatActivity (TakeRestActivity) এর জন্য
    implementation("androidx.appcompat:appcompat:1.7.0")

    // WebKit — FamilyBrowser (RasBrowser) এর জন্য
    implementation("androidx.webkit:webkit:1.11.0")

    // Media — MediaSession + MediaStyle notification (YouTube floating controls)
    implementation("androidx.media:media:1.7.0")

    // Security Crypto — FamilyBrowser encrypted prefs এর জন্য
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Accompanist — FamilyBrowser system UI controller এর জন্য
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.34.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
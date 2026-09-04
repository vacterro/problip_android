import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.vacster.problip"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vacster.problip"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.withType<Test>().configureEach {
    // DataStore's atomic .tmp rename collides with the V:-drive host tmpdir
    // (scanner/filter holds the fresh file); use the standard C: temp instead.
    systemProperty(
        "java.io.tmpdir",
        (System.getenv("LOCALAPPDATA") ?: """C:\Windows""") + """\Temp""",
    )
}

dependencies {
    constraints {
        // play-services-base (via Billing) resolves androidx.fragment to 1.1.0,
        // which is below the 1.3.0 that activity's registerForActivityResult
        // requires; lintVitalRelease fails the release build over it. Fragment is
        // already on the classpath, so this only raises its version.
        implementation(libs.androidx.fragment)
    }

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.billing.ktx) {
        // Play Billing still declares a location SDK its own code never calls
        // (re-checked on 9.1.0: no gms/location, gms/places or placereport
        // reference in 607 classes). Keeping it would put a location SDK in the
        // shipped APK and in the Play SDK index for a beep timer that asks for no
        // location permission. placereport arrives only through location, so
        // excluding location drops both.
        exclude(group = "com.google.android.gms", module = "play-services-location")
    }

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

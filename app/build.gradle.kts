import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release signing secrets come from an untracked keystore.properties at the
// project root (storeFile/storePassword/keyAlias/keyPassword) — never from a
// literal here. See docs/release-signing.md.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.vacster.problip"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vacster.problip"
        minSdk = 26
        targetSdk = 36
        // First stable release identity. versionCode 1 is acceptable because no
        // production Play release exists yet; the versionCode policy lives in
        // docs/release-checklist.md (once an artifact is uploaded to Play, every
        // later upload needs a strictly greater versionCode).
        versionCode = 1
        versionName = "1.0.0"

        // Packaged locales stay the supported set, so library translations cannot
        // make Android Settings advertise languages Problip does not ship.
        resourceConfigurations += listOf("en", "ru", "et", "ja")
    }

    androidResources {
        // AGP generates the locale config from res/ + resources.properties;
        // no manual locale_config.xml alongside it.
        generateLocaleConfig = true
    }

    buildTypes {
        release {
            // Deliberately unchanged for v1.0.0: enabling R8 right before RC would
            // add a whole behaviour delta to validate against the accepted build.
            isMinifyEnabled = false
            if (keystorePropertiesFile.exists()) {
                val missing = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
                    .filter { keystoreProperties.getProperty(it).isNullOrBlank() }
                check(missing.isEmpty()) {
                    "keystore.properties is missing: $missing — a signed release build was " +
                        "requested, so refusing to produce a silently unsigned artifact " +
                        "(see docs/release-signing.md)"
                }
                val store = rootProject.file(keystoreProperties.getProperty("storeFile"))
                check(store.isFile) {
                    "keystore.properties points to a missing keystore: ${store.absolutePath} " +
                        "(see docs/release-signing.md)"
                }
                signingConfig = signingConfigs.create("release") {
                    this.storeFile = store
                    this.storePassword = keystoreProperties.getProperty("storePassword")
                    this.keyAlias = keystoreProperties.getProperty("keyAlias")
                    this.keyPassword = keystoreProperties.getProperty("keyPassword")
                }
            }
            // No keystore.properties -> the artifact stays unsigned. Fine for local
            // verification; it is NOT usable for production upload.
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
    implementation(libs.androidx.appcompat)
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

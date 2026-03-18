plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "it.bosler.requestcsr"
    compileSdk = 36

    defaultConfig {
        applicationId = "it.bosler.requestcsr"
        minSdk = 26
        targetSdk = 36
        versionCode = (findProperty("APP_VERSION_CODE") as? String)?.toIntOrNull() ?: 1
        versionName = (findProperty("APP_VERSION_NAME") as? String) ?: "1.0.0"
    }

    signingConfigs {
        create("release") {
            val ksFile = file(System.getenv("KEYSTORE_FILE") ?: "${rootProject.projectDir}/release.keystore")
            if (ksFile.exists()) {
                storeFile = ksFile
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEY_ALIAS") ?: ""
                keyPassword = System.getenv("KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val releaseSigning = signingConfigs.findByName("release")
            if (releaseSigning?.storeFile?.exists() == true) {
                signingConfig = releaseSigning
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(libs.bouncycastle.pkix)
    implementation(libs.bouncycastle.prov)

    debugImplementation(libs.androidx.compose.ui.tooling)
}

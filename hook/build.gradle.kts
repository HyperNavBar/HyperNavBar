plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "cn.ianzb.hypernavbar.hook"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 35
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        buildConfig = false
    }
}

dependencies {
    compileOnly(libs.libxposed.api)
    api(libs.libxposed.service)
    api(libs.dexkit)

    testImplementation(libs.junit)
}

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
    // JVM 单测使用真实 org.json 实现（Android 桩里未 mock）
    testImplementation("org.json:json:20240303")
}

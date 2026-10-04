import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// libxposed API 是 compileOnly（运行时由框架提供，绝不能打包进 APK，否则 LSPosed 拒绝加载）。
// 但 R8 必须能解析 XposedInterface$Hooker / XposedModule 等类型，否则会破坏 Hooker 实现
// （AbstractMethodError，表现为所有 hook 失效）。下面的任务从 AAR 解出 classes.jar，
// 生成仅供 R8 解析用的 -libraryjars 规则（不会进入 APK）。
val libxposedApiClasspath: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}
dependencies {
    libxposedApiClasspath(libs.libxposed.api)
}

val libxposedR8Dir = layout.buildDirectory.dir("libxposed-r8")
val libxposedLibraryJarsFile = libxposedR8Dir.map { it.file("libraryjars.pro") }

val prepareLibxposedR8 by tasks.registering {
    val apiAar = libxposedApiClasspath.elements.map { it.single().asFile }
    val outputDir = libxposedR8Dir
    val libraryJarsFile = libxposedLibraryJarsFile
    inputs.file(apiAar)
    outputs.file(libraryJarsFile)
    doLast {
        val dir = outputDir.get().asFile.also { it.mkdirs() }
        val apiJar = dir.resolve("libxposed-api.jar")
        ZipFile(apiAar.get()).use { zip ->
            val entry = zip.getEntry("classes.jar") ?: error("classes.jar not found in the libxposed api AAR")
            zip.getInputStream(entry).use { input -> apiJar.outputStream().use { input.copyTo(it) } }
        }
        libraryJarsFile.get().asFile.writeText("-libraryjars ${apiJar.absolutePath.replace('\\', '/')}\n")
    }
}

tasks.configureEach {
    if (name == "minifyReleaseWithR8") {
        dependsOn(prepareLibxposedR8)
    }
}

android {
    namespace = "cn.ianzb.hypernavbar"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "cn.ianzb.hypernavbar"
        minSdk = 35
        targetSdk = 37
        versionCode = 27
        versionName = "3.0.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file("../release.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = "hypernavbar"
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                libxposedLibraryJarsFile.get().asFile,
            )
        }
    }

    packaging {
        resources {
            merges += listOf("META-INF/xposed/*")
        }
        dex {
            useLegacyPackaging = true
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":hook"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.miuix.core)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.shader)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.squircle)
    implementation(libs.miuix.navigation)
    implementation(libs.material.icons.core)
    implementation(libs.haze)
}




















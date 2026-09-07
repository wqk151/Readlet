import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// 版本单一来源：versionName 与 APK 产物名共用
val appVersionName = "0.10.0"

// 发布签名：keystore.properties 不入库，缺失时 release 产出未签名 APK
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
val hasReleaseSigning = keystorePropsFile.exists()

android {
    namespace = "com.readlet.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.readlet.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 25
        versionName = appVersionName
        // 本地发音引擎（sherpa-onnx）仅打包 arm64-v8a：2020 后实机全覆盖；x86_64 模拟器不支持为已知限制。
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    // APK 命名规范：Readlet-v<versionName>-<variant>.apk
    base {
        archivesName = "Readlet-v$appVersionName"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.coroutines.android)
    // sherpa-onnx 官方预编译 AAR（v1.13.7，sha256 c4ef49e3…73c8，本地 libs/ 目录入库）：含 JNI .so 与 Kotlin API
    implementation(files("libs/sherpa-onnx-1.13.7.aar"))
    // 语音包 tar.bz2 解压（纯 Java，流式）
    implementation(libs.commons.compress)
    testImplementation(libs.junit)
    // org.json 在 JVM 单测中需要真实实现（Android SDK 的为空壳）
    testImplementation("org.json:json:20240303")
    debugImplementation(libs.androidx.ui.tooling)
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.qmryqmm.hyperopen"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.qmryqmm.hyperopen"
        minSdk = 29
        targetSdk = 35
        versionCode = 63
        versionName = "0.63"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

tasks.matching { it.name.contains("AarMetadata") }.configureEach {
    enabled = false
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")

    implementation("androidx.activity:activity-compose:1.11.0")

    // ---- MIUIX 0.9.4（官方推荐模块化依赖）----
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:0.9.4")
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:0.9.4")
    // 隐藏 API 豁免：让 App 进程也能反射 ViewRootImpl.createBackgroundBlurDrawable，
    // 与 SystemUI 侧真实渲染完全同源（SurfaceFlinger 硬件毛玻璃）
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")
}

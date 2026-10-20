plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.shuiyu.octoberrain"
    compileSdk =
        libs.versions.compileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "com.shuiyu.octoberrain"
        minSdk =
            libs.versions.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.targetSdk
                .get()
                .toInt()
        // V1.0.7 外观即时生效与周期窗口、内置页实验室页签常显与走查缺陷修复；版本号同时作为微读共存判定的阈值下限
        versionCode = 36
        versionName = "1.0.16"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // V1.0 阶段沿用调试签名，使发布构建产物可直接安装到测试设备；
            // 正式发布前替换为独立签名与密钥，调试签名只用于内部验证。
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:device"))
    implementation(project(":core:design"))
    implementation(project(":core:render"))
    implementation(project(":core:data"))
    implementation(project(":core:weread-sync"))
    implementation(project(":core:widget-api"))
    implementation(project(":feature:home"))
    implementation(project(":feature:widgets"))
    implementation(project(":feature:tray"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:content"))
    implementation(project(":feature:files"))
    implementation(project(":feature:weread-app"))
    implementation(project(":service"))
}

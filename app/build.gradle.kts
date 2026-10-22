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
        // 1.0.29：文件阅读器、Office全文搜索与来源身份/验收收口，沿用内部测试签名。
        versionCode = 51
        versionName = "1.0.31"
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
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk)
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
    implementation(project(":feature:reader"))
    implementation(project(":feature:weread-app"))
    implementation(project(":service"))
}

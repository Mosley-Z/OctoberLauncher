plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.shuiyu.octoberrain.feature.reader"
    compileSdk =
        libs.versions.compileSdk
            .get()
            .toInt()

    defaultConfig {
        minSdk =
            libs.versions.minSdk
                .get()
                .toInt()
    }

    buildFeatures { aidl = true }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests {
            // 单元测试运行于本地 JVM，Android 框架方法返回默认值而非抛出异常
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk)
    implementation(libs.androidx.webkit)
    implementation(libs.commonmark)
    implementation(libs.commonmark.tables)
    implementation(libs.jsoup)
    api(project(":core:common"))
    implementation(project(":core:device"))
    implementation(project(":core:design"))
    implementation(project(":core:data"))
    implementation(project(":core:render"))
}

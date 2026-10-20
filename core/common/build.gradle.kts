plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.shuiyu.octoberrain.core.common"
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

    compileOptions {
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
    api(libs.androidx.core.ktx)
    api(libs.kotlinx.coroutines.android)
    api(libs.kotlinx.serialization.json)

    // 版本目录未收拢测试库，此处直接固定版本，避免与其他模块的版本目录改动互相影响
    testImplementation("junit:junit:4.13.2")
}

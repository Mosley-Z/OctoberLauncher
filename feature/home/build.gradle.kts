plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.shuiyu.octoberrain.feature.home"
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
    implementation(project(":core:common"))
    implementation(project(":core:device"))
    implementation(project(":core:design"))
    implementation(project(":core:render"))
    implementation(project(":core:data"))
    implementation(project(":core:widget-api"))

    // 版本目录未收拢测试库，此处直接固定版本，避免与其他模块的版本目录改动互相影响
    testImplementation("junit:junit:4.13.2")
}

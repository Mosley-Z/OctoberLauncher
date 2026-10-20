plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.shuiyu.octoberrain.feature.wereadapp"
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
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:design"))
    implementation(project(":core:render"))
    // 微读墨记内核，提供卡片绘制、周期计算、洞察页与数据装载能力
    implementation(rootProject.files("libs/weread-kernel-1.3.2-k6.jar"))
}

// 复制的微读墨记 Java 控件（com/inkread/weekread/**）是外部复用基线，
// 不参与本仓的 detekt 与 ktlint 风格门禁；新增的 Kotlin 胶水代码照常受检。
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    exclude("**/com/inkread/weekread/**")
}

pluginManager.withPlugin("org.jlleitschuh.gradle.ktlint") {
    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension>("ktlint") {
        filter {
            exclude("**/com/inkread/weekread/**")
        }
    }
}

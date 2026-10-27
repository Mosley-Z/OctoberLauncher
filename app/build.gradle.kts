import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val signingPropertiesFile =
    providers.environmentVariable("OCTOBER_SIGNING_PROPERTIES").orNull?.let { file(it) }
        ?: rootProject.file("release-signing.properties")
val releaseSigningProperties = Properties()
if (signingPropertiesFile.isFile) {
    signingPropertiesFile.inputStream().use { releaseSigningProperties.load(it) }
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
        versionCode =
            rootProject
                .file("VERSION_CODE")
                .readText()
                .trim()
                .toInt()
        versionName = rootProject.file("VERSION").readText().trim()
    }

    signingConfigs {
        if (releaseSigningProperties.isNotEmpty()) {
            create("production") {
                storeFile = file(requireNotNull(releaseSigningProperties.getProperty("storeFile")))
                storePassword = requireNotNull(releaseSigningProperties.getProperty("storePassword"))
                keyAlias = requireNotNull(releaseSigningProperties.getProperty("keyAlias"))
                keyPassword = requireNotNull(releaseSigningProperties.getProperty("keyPassword"))
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // CI没有正式密钥时只构建未签名产物；正式打包脚本要求外部生产签名。
            if (releaseSigningProperties.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("production")
            }
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
    testImplementation("junit:junit:4.13.2")
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

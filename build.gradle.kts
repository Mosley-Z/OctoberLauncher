// 根构建脚本：登记插件版本并统一接入静态检查；模块级配置仍留在各模块
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
}

// 统一清理，避免各模块各写一遍
tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

// 由模块路径生成基线文件名：根模块为 app，子模块以连字符连接。
fun Project.lintBaselineKey(): String = path.trimStart(':').replace(':', '-')

// 静态检查与最小测试依赖统一在根脚本接入。
// detekt 与 ktlint 只作用于含 Kotlin 源码的模块；detekt 的扫描范围显式指向
// 模块下的 src 目录，Android 源集不会被插件按 Java 源集自动识别。
// 存量债务已落入基线，自 0.9.2 起静态检查失败即中止，新增告警必须先修复。
subprojects {
    plugins.withId("com.android.library") {
        dependencies.add("testImplementation", "junit:junit:4.13.2")
        // Android Lint 存量问题集中落入基线，新增问题继续使构建失败；
        // 基线按模块拆分，避免多模块共用一份文件互相覆盖。
        extensions.configure<com.android.build.api.dsl.LibraryExtension> {
            lint {
                baseline = rootProject.file("config/lint/baseline-${lintBaselineKey()}.xml")
                // 生成基线时关闭中止，其余场合保持中止，新增问题仍使构建失败。
                abortOnError = !project.hasProperty("lintBaselineUpdate")
            }
        }
    }

    plugins.withId("com.android.application") {
        extensions.configure<com.android.build.api.dsl.ApplicationExtension> {
            lint {
                baseline = rootProject.file("config/lint/baseline-app.xml")
                abortOnError = !project.hasProperty("lintBaselineUpdate")
            }
        }
    }

    plugins.withId("org.jetbrains.kotlin.android") {
        apply(plugin = "io.gitlab.arturbosch.detekt")
        apply(plugin = "org.jlleitschuh.gradle.ktlint")

        extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
            buildUponDefaultConfig = true
            parallel = true
            config.setFrom(rootProject.files("config/detekt/detekt.yml"))
            // 基线按模块拆分，避免多模块共用一份基线互相覆盖
            val moduleKey = path.trimStart(':').replace(':', '-')
            baseline = rootProject.file("config/detekt/baseline-$moduleKey.xml")
            ignoreFailures = false
        }
        extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
            version.set("1.4.1")
            android.set(true)
            ignoreFailures.set(false)
        }

        tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
            // 扫描范围为模块下全部 Kotlin 源码，包括测试源码
            setSource(project.fileTree(projectDir) { include("src/**/*.kt") })
            ignoreFailures = false
            reports {
                html.required.set(true)
                xml.required.set(true)
                md.required.set(true)
                sarif.required.set(false)
                txt.required.set(false)
            }
        }
        tasks.matching { it.name == "check" }.configureEach {
            dependsOn("ktlintCheck", "detekt")
        }
    }
}

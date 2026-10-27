plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    // app/google-services.json が必要（リポジトリには含めない。docs/design/architecture.md「Firebase の設定」）
    alias(libs.plugins.google.services)
}

/**
 * 名寄せ辞書の元データ（リポジトリの dictionary/）を、アセットの dictionary/ に写す。CSV と VERSION だけを含める
 * （docs/design/suggestions.md「名寄せ辞書」）
 */
abstract class CopyNormalizationDictionary : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val target = outputDir.get().asFile.resolve("dictionary")
        target.deleteRecursively()
        target.mkdirs()
        sourceDir.get().asFile.listFiles { file -> file.name == "VERSION" || file.extension == "csv" }!!
            .forEach { it.copyTo(target.resolve(it.name)) }
    }
}

val copyNormalizationDictionary = tasks.register<CopyNormalizationDictionary>("copyNormalizationDictionary") {
    sourceDir.set(rootProject.layout.projectDirectory.dir("dictionary"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copyNormalizationDictionary, CopyNormalizationDictionary::outputDir)
    }
}

android {
    namespace = "com.cheesecomer.cartmate"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.cheesecomer.cartmate"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // キーボードの表示のタイミングに依存するテストは、通常の実行から外す。
        // -PkeyboardTimingTests を付けたときは、そのテストだけを実行する（docs/testing.md）
        val keyboardTimingTest = "com.cheesecomer.cartmate.testing.KeyboardTimingTest"
        if (project.hasProperty("keyboardTimingTests")) {
            testInstrumentationRunnerArguments["annotation"] = keyboardTimingTest
        } else {
            testInstrumentationRunnerArguments["notAnnotation"] = keyboardTimingTest
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.reorderable)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    // App Check。リリース版は Play Integrity、デバッグ版はデバッグ用のプロバイダ（src/debug, src/release の AppCheckSetup）
    releaseImplementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testRuntimeOnly(libs.junit.platform.launcher)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.truth)
    // Compose の ui-test が依存する Espresso 3.5 は Android 15 以降で動かないため、新しい版に上げる
    androidTestImplementation(libs.androidx.test.espresso.core)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

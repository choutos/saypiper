import com.android.build.api.artifact.SingleArtifact

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("androidx.room")
    id("io.github.takahirom.roborazzi")
}

android {
    namespace = "eu.wanderclan.wordquest"
    compileSdk = 35

    defaultConfig {
        // SayPiper (FRL-14). Fixed for good by the first release; the namespace and class names keep the working title.
        applicationId = "eu.wanderclan.saypiper"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing comes from CI secrets, never from the repo (FRL-13). Without them the release APK is
    // unsigned: that is what F-Droid builds from source before signing it.
    val releaseStoreFile = System.getenv("RELEASE_STORE_FILE")
    signingConfigs {
        if (releaseStoreFile != null) create("release") {
            storeFile = file(releaseStoreFile)
            storePassword = System.getenv("RELEASE_STORE_PASSWORD")
            keyAlias = System.getenv("RELEASE_KEY_ALIAS")
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseStoreFile != null) signingConfig = signingConfigs.getByName("release")
            // The git revision AGP stamps into the APK depends on how the source was checked out. Without it
            // F-Droid's build from the tag matches ours byte for byte and can ship our signature (FRL-13).
            vcsInfo.include = false
        }
    }
    // F-Droid rejects the encrypted dependency list AGP adds to the signing block; nothing reads it here.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.maxHeapSize = "1536m" }
        }
    }
    lint {
        warningsAsErrors = false
        abortOnError = true
        // Version checks need the network and change daily; upgrades are deliberate (ADR D1).
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
}

room { schemaDirectory("$projectDir/schemas") }

// The manifest test reads the merged manifests of the debug and the release app, not the one Robolectric
// builds for tests. Both are checked by testDebugUnitTest, so CI needs no release unit-test run.
androidComponents {
    onVariants { variant ->
        val manifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
        val buildType = variant.buildType ?: return@onVariants
        tasks.withType<Test>().matching { it.name == "testDebugUnitTest" }
            .configureEach {
                inputs.file(manifest).withPropertyName("${buildType}MergedManifest")
                jvmArgumentProviders.add(CommandLineArgumentProvider {
                    listOf("-Dwordquest.mergedManifest.$buildType=${manifest.get().asFile.absolutePath}")
                })
            }
    }
}

// Robolectric's Android runtimes come from Gradle (cached, with retries), never from the test JVM, which
// would download them from Maven Central on every CI run. Robolectric 4.14.1 loads one: Android 15, the
// runtime the tests run on (sdk=35 in robolectric.properties) and also the newest one the JVM supports, whose
// framework resources it reads while parsing the app. If Robolectric asks for another, the test failure
// names the coordinates to add here (see RobolectricRuntimeResolver in the unit tests). One configuration
// per runtime: in a shared one, Gradle would keep only the newest version of the module.
val robolectricRuntimes = listOf(
    "org.robolectric:android-all-instrumented:15-robolectric-12650502-i7",
)
val robolectricRuntimeDir = layout.buildDirectory.dir("robolectric-runtime")
val syncRobolectricRuntime by tasks.registering(Sync::class) {
    robolectricRuntimes.forEach { from(configurations.detachedConfiguration(dependencies.create(it))) }
    into(robolectricRuntimeDir)
}
tasks.withType<Test>().configureEach {
    inputs.files(syncRobolectricRuntime).withPropertyName("robolectricRuntime").withPathSensitivity(PathSensitivity.NAME_ONLY)
    systemProperty("robolectric.offline", "true")
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf("-Drobolectric.dependency.dir=${robolectricRuntimeDir.get().asFile.absolutePath}")
    })
}

tasks.withType<Test>().matching { it.name == "testDebugUnitTest" }.configureEach {
    outputs.dir(layout.buildDirectory.dir("outputs/showcase"))
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.navigation:navigation-compose:2.8.9")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.43.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.43.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-junit-rule:1.43.1")
}

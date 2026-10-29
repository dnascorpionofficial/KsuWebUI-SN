@file:Suppress("UnstableApiUsage")

import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val keystorePropertiesFile: File = rootProject.file("keystore.properties")
val keystoreProperties = if (keystorePropertiesFile.exists() && keystorePropertiesFile.isFile) {
    Properties().apply {
        load(FileInputStream(keystorePropertiesFile))
    }
} else null

val gitCommitCount = run {
    val process = Runtime.getRuntime().exec(arrayOf("git", "rev-list", "--count", "HEAD"))
    process.inputStream.bufferedReader().use { it.readText().trim().toInt() }
}

fun gitOutput(vararg args: String): String? = try {
    val process = ProcessBuilder(listOf("git") + args)
        .directory(rootProject.projectDir)
        .redirectErrorStream(true)
        .start()
    val out = process.inputStream.bufferedReader().use { it.readText() }.trim()
    if (process.waitFor() == 0) out else null
} catch (e: Exception) {
    null
}

val commitsPerVersion = 99
fun versionCycle(count: Int) = (maxOf(count, 1) - 1) / commitsPerVersion

val baseVersion = rootProject.file("update.txt").takeIf { it.isFile }
    ?.readLines()?.firstOrNull()?.removePrefix("\uFEFF")?.trim().orEmpty().ifEmpty { "1.0" }

val versionInfo = run {
    val match = Regex("""(\d+)\.(\d+)""").matchEntire(baseVersion)
    if (match == null) {
        baseVersion to gitCommitCount.toString()
    } else {
        val anchorHash = gitOutput("blame", "-w", "-L", "1,1", "--porcelain", "--", "update.txt")
            ?.lineSequence()?.firstOrNull()?.substringBefore(' ')
            ?.takeIf { it.isNotBlank() && !it.all { c -> c == '0' } }
        val anchorCount = anchorHash?.let { gitOutput("rev-list", "--count", it)?.toIntOrNull() }
            ?: gitCommitCount
        val cycle = versionCycle(gitCommitCount)
        val minor = match.groupValues[2].toInt() + (cycle - versionCycle(anchorCount))
        "${match.groupValues[1]}.$minor" to "%02d".format(gitCommitCount - cycle * commitsPerVersion)
    }
}
val appVersionName = versionInfo.first
val buildSuffix = versionInfo.second

android {
    namespace = "io.github.a13e300.ksuwebui"
    compileSdk = 36

    signingConfigs {
        if (keystoreProperties != null) {
            create("release") {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    }

    defaultConfig {
        applicationId = "io.github.a13e300.ksuwebui"
        minSdk = 26
        targetSdk = 36
        versionCode = gitCommitCount
        versionName = appVersionName
        val updateRepo = (findProperty("updateRepo") as String?)
            ?: System.getenv("GITHUB_REPOSITORY")
            ?: ""
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
        buildConfigField("String", "BUILD_SUFFIX", "\"$buildSuffix\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseSig = signingConfigs.findByName("release")
            signingConfig = if (releaseSig != null) releaseSig else {
                println("use debug signing config")
                signingConfigs["debug"]
            }
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    androidResources {
        generateLocaleConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        aidl = true
        buildConfig = true
        viewBinding = true
    }
    packaging {
        resources {
            excludes += "**"
        }
    }
    compileSdkMinor = 1
    buildToolsVersion = "36.1.0"
}

base {
    archivesName.set(
        "KsuWebUI-SN-${appVersionName}-${buildSuffix}-$name"
    )
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.webkit)
    implementation(libs.material)

    implementation(libs.com.github.topjohnwu.libsu.core)
    implementation(libs.com.github.topjohnwu.libsu.service)
    implementation(libs.com.github.topjohnwu.libsu.io)

    implementation(libs.dev.rikka.rikkax.parcelablelist)
}

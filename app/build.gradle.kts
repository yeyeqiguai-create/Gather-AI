plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val gatherVersionName = providers.gradleProperty("gather.versionName").get()
val gatherVersionPattern = Regex("""^v(\d+)\.(\d+)\.(\d+)\.(\d{2}) \(Revise([1-9]\d*)\)$""")
val gatherVersionMatch = gatherVersionPattern.matchEntire(gatherVersionName)
    ?: error("gather.versionName must use the format vX.X.X.XX (ReviseX)")
require(gatherVersionMatch.groupValues[1] == "0" && gatherVersionMatch.groupValues[2] == "1") {
    "Gather AI must remain on the 0.1.x release line until its foundational features are complete"
}
require(gatherVersionMatch.groupValues[4].toInt() in 1..99) {
    "The two-digit mini-version component must be between 01 and 99"
}
val gatherVersionCode = providers.gradleProperty("gather.versionCode").get().toIntOrNull()
    ?: error("gather.versionCode must be a positive Android versionCode")
require(gatherVersionCode > 0) { "gather.versionCode must be a positive Android versionCode" }

val gatherReleaseChannel = providers.gradleProperty("gather.releaseChannel")
    .orElse("formal-test")
    .get()
require(gatherReleaseChannel in setOf("beta", "formal-test")) {
    "gather.releaseChannel must be either beta or formal-test"
}

android {
    namespace = "com.tim.futureai"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.tim.futureai"
        minSdk = 24
        targetSdk = 37
        versionCode = gatherVersionCode
        versionName = gatherVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

androidComponents {
    onVariants { variant ->
        val channel = when (variant.buildType) {
            "debug" -> "internal-dev"
            "release" -> gatherReleaseChannel
            else -> error("Unsupported Gather AI build type: ${variant.buildType}")
        }
        variant.outputs.forEach { output ->
            output.outputFileName.set(
                "Gather-AI-${gatherVersionName.replace(" ", "-")}-$channel.apk",
            )
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.yudistirosaputro.dimock.ui"
    compileSdk = 35
    defaultConfig { minSdk = 21 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions.unitTests.isReturnDefaultValues = true
}

dependencies {
    api(project(":dimock-okhttp"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}

// PRD section 10, Wave 4: the inspector is Compose only. Fails the build on XML layouts or View subclasses.
val checkComposeOnly by tasks.registering {
    group = "verification"
    description = "Fail if dimock-ui contains XML layouts or View subclasses"
    val srcDir = layout.projectDirectory.dir("src/main")
    inputs.dir(srcDir)
    doLast {
        val layouts = srcDir.dir("res").asFile.listFiles { f -> f.isDirectory && f.name.startsWith("layout") }?.toList().orEmpty()
        check(layouts.isEmpty()) { "dimock-ui must not contain layout resources: $layouts" }
        val offenders = srcDir.dir("kotlin").asFile.walkTopDown().filter { it.extension == "kt" }
            .filter { f -> Regex("""\b(class|object)\s+\w+[^{]*:\s*[^{]*\b(View|ViewGroup|Fragment|TextView|LinearLayout|FrameLayout|RecyclerView)\s*\(""").containsMatchIn(f.readText()) }
            .map { it.relativeTo(projectDir).path }.toList()
        check(offenders.isEmpty()) { "dimock-ui must not subclass Views: $offenders" }
    }
}
tasks.named("check") { dependsOn(checkComposeOnly) }

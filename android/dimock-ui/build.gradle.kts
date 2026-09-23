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
    // Captured at configuration time: reaching for `projectDir` inside doLast is a script object reference,
    // which the configuration cache cannot serialize.
    val moduleDir = layout.projectDirectory.asFile
    inputs.dir(srcDir)
    doLast {
        val layouts = srcDir.dir("res").asFile.listFiles { f -> f.isDirectory && f.name.startsWith("layout") }?.toList().orEmpty()
        check(layouts.isEmpty()) { "dimock-ui must not contain layout resources: $layouts" }
        val offenders = srcDir.dir("kotlin").asFile.walkTopDown().filter { it.extension == "kt" }
            .filter { f -> Regex("""\b(class|object)\s+\w+[^{]*:\s*[^{]*\b(View|ViewGroup|Fragment|TextView|LinearLayout|FrameLayout|RecyclerView)\s*\(""").containsMatchIn(f.readText()) }
            .map { it.relativeTo(moduleDir).path }.toList()
        check(offenders.isEmpty()) { "dimock-ui must not subclass Views: $offenders" }
    }
}
tasks.named("check") { dependsOn(checkComposeOnly) }

// PRD section 10, Wave 6: every screen the inspector shows is renderable in the IDE preview pane, in both
// themes, without a device. Previews live at the bottom of the file that owns the composable, so this scans
// src/main/kotlin. A screen whose preview is deleted or commented out fails the build here.
val checkPreviewCoverage by tasks.registering {
    group = "verification"
    description = "Fail if a screen-level composable has no @InspectorPreviews preview beside it in src/main/kotlin"
    val sourceDir = layout.projectDirectory.dir("src/main/kotlin")
    val screens = listOf("TrafficScreen", "DetailScreen", "ForceStateSheet", "CurlSheet", "MocksScreen", "AgentScreen")
    val annotation = "@InspectorPreviews"
    inputs.dir(sourceDir)
    inputs.property("screens", screens)
    doLast {
        val sources = sourceDir.asFile.walkTopDown().filter { it.extension == "kt" }.toList()
        check(sources.isNotEmpty()) { "dimock-ui has no sources: ${sourceDir.asFile} holds no Kotlin files" }

        // String literals go first — a glob like "/users/*/albums" in a fixture must not open a "comment" — then
        // comments, so a preview commented out reads as absent rather than as still covering its screen.
        val stripped = sources.map {
            it.readText()
                .replace(Regex("\"\"\"[\\s\\S]*?\"\"\"|\"(?:\\\\.|[^\"\\\\\\n])*\""), "\"\"")
                .replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
                .replace(Regex("//[^\\n]*"), "")
        }

        val declaration = stripped.firstOrNull { it.contains("annotation class InspectorPreviews") }
        check(declaration != null) { "dimock-ui previews need the $annotation multipreview annotation in src/main/kotlin" }
        for (mode in listOf("UI_MODE_NIGHT_YES", "UI_MODE_NIGHT_NO")) {
            check(declaration.contains(mode)) {
                "$annotation must declare a @Preview with uiMode = Configuration.$mode, or previews stop covering both themes"
            }
        }

        // One chunk per annotation, cut at the closing brace of the function it decorates, so only a real
        // preview body counts — never the screen's own definition further up the same file.
        val previews = stripped.flatMap { text -> text.split(annotation).drop(1).map { it.substringBefore("\n}") } }
        val missing = screens.filterNot { screen ->
            val call = Regex("\\b" + screen + "\\s*\\(")
            previews.any { call.containsMatchIn(it) }
        }
        check(missing.isEmpty()) {
            "dimock-ui screens with no $annotation preview: ${missing.joinToString(", ")}. " +
                "Every screen-level composable needs one at the bottom of its own file under " +
                "src/main/kotlin/com/yudistirosaputro/dimock/ui/screens/, so a palette or layout change can be " +
                "seen in both themes without a device."
        }
    }
}
tasks.named("check") { dependsOn(checkPreviewCoverage) }

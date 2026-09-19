plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.maven.publish) apply false
}

// Publishing to Maven Central (Wave 5). Coordinates: GROUP:<module>:VERSION_NAME from gradle.properties.
// Credentials come from the environment in CI: ORG_GRADLE_PROJECT_mavenCentralUsername / Password,
// ORG_GRADLE_PROJECT_signingInMemoryKey / signingInMemoryKeyPassword. Nothing secret lives in the repo.
subprojects {
    if (name.startsWith("dimock-")) {
        apply(plugin = "com.vanniktech.maven.publish")
        // GROUP/VERSION_NAME are read from gradle.properties by the plugin itself and the coordinates are
        // finalized before any afterEvaluate of ours runs, so configure the extension eagerly and do not
        // re-declare them here; artifactId defaults to the project name (dimock-core, dimock-okhttp, ...).
        extensions.configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
            publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
            signAllPublications()
            pom {
                name.set(project.name)
                description.set(
                    when (project.name) {
                        "dimock-core" -> "dimock engine: mock rules, capture store, redaction, wire server (pure JVM)"
                        "dimock-okhttp" -> "dimock OkHttp interceptor and Android entry point (debug builds)"
                        "dimock-okhttp-no-op" -> "dimock no-op artifact for release builds"
                        "dimock-ui" -> "dimock in-app inspector (Jetpack Compose)"
                        else -> "dimock"
                    },
                )
                url.set("https://github.com/yudistirosaputro/dimock")
                licenses { license { name.set("Apache-2.0"); url.set("https://www.apache.org/licenses/LICENSE-2.0.txt") } }
                developers { developer { id.set("yudistirosaputro"); name.set("Yudistiro Saputro") } }
                scm {
                    url.set("https://github.com/yudistirosaputro/dimock")
                    connection.set("scm:git:git://github.com/yudistirosaputro/dimock.git")
                    developerConnection.set("scm:git:ssh://git@github.com/yudistirosaputro/dimock.git")
                }
            }
        }
    }
}

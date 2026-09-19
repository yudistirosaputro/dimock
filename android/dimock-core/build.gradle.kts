plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(17) }

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}

// JVM stand-in for a device, used by scripts/contract in CI. Lives in the test source set so it never ships.
tasks.register<JavaExec>("runDevServer") {
    group = "dimock"
    description = "Start a WireServer on the JVM (-Pport=6767) for wire-protocol contract tests"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.yudistirosaputro.dimock.core.DevServer")
    args((project.findProperty("port") ?: "6767").toString())
}

plugins {
    `java-library`
}

import java.util.Locale

group = "com.agulev.jwuff"
version = file("VERSION").readText().trim()

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "4g"
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    // Pass through Gradle's -D... flags to the forked test JVM.
    systemProperty("jwuff.mem", System.getProperty("jwuff.mem"))
    systemProperty("jwuff.perf", System.getProperty("jwuff.perf"))
    systemProperty("jwuff.stress", System.getProperty("jwuff.stress"))
    testLogging {
        showStandardStreams = true
        events(
            "passed",
            "skipped",
            "failed",
            "standardOut",
            "standardError",
        )
    }
}

val osName = System.getProperty("os.name").lowercase(Locale.ROOT)
val archName = System.getProperty("os.arch").lowercase(Locale.ROOT)

val isMac = osName.contains("mac")
val isLinux = osName.contains("linux")
val isWindows = osName.contains("win")

val isArm64 = setOf("aarch64", "arm64").contains(archName)
val isX64 = setOf("x86_64", "amd64", "x64").contains(archName)

val platformId = when {
    isMac && isArm64 -> "arm64-macos"
    isMac && isX64 -> "x86_64-macos"
    isLinux && isX64 -> "x86_64-linux"
    isWindows && isX64 -> "x86_64-win32"
    else -> null
}

val nativeLibFileName = when {
    isWindows -> "wuffs_imageio.dll"
    isMac -> "libwuffs_imageio.dylib"
    else -> "libwuffs_imageio.so"
}

val nativeSourceDir = layout.projectDirectory.dir("src/native")
val nativeBuildDir = layout.buildDirectory.dir("native/build")
val nativeResourcesRoot = layout.buildDirectory.dir("generated-resources")
val nativeResourcesDir = nativeResourcesRoot.map { it.dir("natives/${platformId ?: "unknown"}") }

val nativesDirProperty = providers.gradleProperty("nativesDir")

fun Test.collectAllIncludePatterns(): Set<String> {
    val patterns = linkedSetOf<String>()
    patterns.addAll(filter.includePatterns)

    var cls: Class<*>? = filter.javaClass
    while (cls != null && cls != Any::class.java) {
        for (field in cls.declaredFields) {
            if (!field.name.contains("include", ignoreCase = true)) continue
            field.isAccessible = true
            val value = runCatching { field.get(filter) }.getOrNull() ?: continue
            when (value) {
                is Collection<*> -> value.filterIsInstance<String>().forEach { patterns.add(it) }
                is Array<*> -> value.filterIsInstance<String>().forEach { patterns.add(it) }
            }
        }
        cls = cls.superclass
    }
    return patterns
}

gradle.taskGraph.whenReady {
    val testTask = tasks.named<Test>("test").get()
    val patterns = testTask.collectAllIncludePatterns()

    fun enableIfSelected(flag: String, vararg needles: String) {
        if (System.getProperty(flag) != null) return
        if (patterns.any { pattern -> needles.any { needle -> pattern.contains(needle) } }) {
            testTask.systemProperties[flag] = "true"
        }
    }

    enableIfSelected("jwuff.perf", "PerformanceComparisonTest")
    enableIfSelected("jwuff.mem", "MemoryAndGcTest")
    enableIfSelected("jwuff.stress", "PngMultithreadedStressTest")
}

tasks.register("verifyWuffsSubmodule") {
    onlyIf { platformId != null }
    doLast {
        val releaseC = file("src/native/third_party/wuffs/release/c")
        if (!releaseC.isDirectory) {
            throw GradleException("Wuffs submodule missing/uninitialized. Run: git submodule update --init --recursive")
        }
        val snapshot = File(releaseC, "wuffs-unsupported-snapshot.c")
        if (!snapshot.isFile) {
            throw GradleException("Wuffs release file not found: $snapshot. Run: git submodule update --init --recursive")
        }
    }
}

tasks.register<Exec>("configureNative") {
    onlyIf { platformId != null }
    dependsOn("verifyWuffsSubmodule")
    commandLine(
        "cmake",
        "-S", nativeSourceDir.asFile.absolutePath,
        "-B", nativeBuildDir.get().asFile.absolutePath,
        "-DCMAKE_BUILD_TYPE=Release",
        *(if (isMac) listOf("-DCMAKE_OSX_DEPLOYMENT_TARGET=11.0") else emptyList()).toTypedArray(),
    )
}

tasks.register<Exec>("buildNative") {
    onlyIf { platformId != null }
    dependsOn("configureNative")
    commandLine(
        "cmake",
        "--build", nativeBuildDir.get().asFile.absolutePath,
        "--config", "Release",
    )
}

tasks.register<Copy>("copyNative") {
    onlyIf { platformId != null }
    dependsOn("buildNative")

    from(nativeBuildDir) {
        include(nativeLibFileName)
        include("**/$nativeLibFileName")
    }
    into(nativeResourcesDir)
    rename { nativeLibFileName }
}

tasks.named<ProcessResources>("processResources") {
    dependsOn("copyNative")
    from(nativeResourcesRoot)
    // Used by the release job to bundle all downloaded native artifacts.
    if (nativesDirProperty.isPresent) {
        from(file(nativesDirProperty.get()))
    }
}

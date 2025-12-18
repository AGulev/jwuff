# jwuff: Wuffs the Library for Java

[![CI](https://github.com/AGulev/jwuff/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/AGulev/jwuff/actions/workflows/ci.yml)

Wuffs-backed ImageIO readers for multiple platforms, built with Gradle.

## Supported formats (current)

- PNG
- JPEG

Other formats are intentionally deferred until the core pipeline is solid.

## Supported platforms (current)

- macOS arm64 (`arm64-macos`)
- macOS x86_64 (`x86_64-macos`)
- Linux x86_64 (`x86_64-linux`)
- Windows x86_64 (`x86_64-win32`)

On x86_64 platforms, the jar may contain both a baseline native library and an AVX2-optimized variant.
At runtime, jwuff selects the AVX2 variant only when the CPU+OS support AVX2.

## Structure

- `src/main/java` — ImageIO SPIs, readers, FFM bindings (JDK 22+)
- `src/main/resources` — `META-INF/services` registration + bundled native dylib
- `src/native` — native C ABI wrapper built as a dylib
- `src/test/java` — tests (JUnit 5)

## Build / Run

If you have Gradle installed:

- `gradle test`

Using the Gradle Wrapper:

- `./gradlew --no-daemon test`

## Using in another project

Once the jar is on your classpath, `ImageIO.read(...)` should pick jwuff automatically via `META-INF/services`.
If you run under a custom classloader or with `java -jar ...` (which can break ImageIO plugin discovery), call:

```java
com.agulev.jwuff.JwuffImageIO.register();
javax.imageio.ImageIO.scanForPlugins(); // optional, but helps in some environments
```

## Native dependency

This repo uses a pinned Wuffs git submodule:

- `git submodule update --init --recursive`

### Pin / upgrade

- Check the pinned revision: `git submodule status`
- Upgrade Wuffs:
  - `git submodule update --remote --merge src/native/third_party/wuffs`
  - verify `git diff --submodule`
  - rebuild + run tests: `./gradlew test`

## Release

Version is stored in `VERSION`.

- Tag and push: `git tag v$(cat VERSION) && git push --tags`
- CI builds native libs for each platform and publishes a GitHub release with the jar.

## Tests (optional / not run by default)

Some tests are intentionally gated because they can be slow or flaky on CI runners.

- Performance benchmark (gated):  
  `./gradlew --no-daemon test --tests com.agulev.jwuff.PerformanceComparisonTest`  
  (also works with `-Djwuff.perf=true`)
- Java-side memory + GC behavior (gated):  
  `./gradlew --no-daemon test --tests com.agulev.jwuff.MemoryAndGcTest`  
  (also works with `-Djwuff.mem=true`)
- Multithreaded stress test (gated):  
  `./gradlew --no-daemon test --tests com.agulev.jwuff.PngMultithreadedStressTest`  
  (also works with `-Djwuff.stress=true`)

## Performance

Measured on MacBook Pro M1 Max using `PerformanceComparisonTest` with `test.png` (16384×16384):

- Standard `ImageIO.read`: 2326.05 ms avg
- `jwuff` (Wuffs-backed): 458.12 ms avg

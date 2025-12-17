# jwuff: Wuffs the Library for Java

Wuffs-backed ImageIO readers for macOS arm64, built with Gradle.

## Supported formats (current)

- PNG
- JPEG

Other formats are intentionally deferred until the core pipeline is solid.

## Supported platforms (current)

- macOS arm64 (`arm64-macos`)
- macOS x86_64 (`x86_64-macos`)
- Linux x86_64 (`x86_64-linux`)
- Windows x86_64 (`x86_64-win32`)

## Structure

- `src/main/java` — ImageIO SPIs, readers, FFM bindings (JDK 22+)
- `src/main/resources` — `META-INF/services` registration + bundled native dylib
- `src/native` — native C ABI wrapper built as a dylib
- `src/test/java` — tests (JUnit 5)

## Build / Run

If you have Gradle installed:

- `gradle test`

To add the Gradle Wrapper (recommended):

- `gradle wrapper`
- then use `./gradlew test`

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

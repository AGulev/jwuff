package com.agulev.jwuff.nativelib;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NativeLibrary {
    private static final AtomicBoolean LOADED = new AtomicBoolean(false);

    private NativeLibrary() {}

    public static void load() {
        if (LOADED.get()) return;
        Platform platform = detectPlatform();
        if (!platform.supported) {
            String label = platform.platformId != null ? platform.platformId : (platform.os + " " + platform.arch);
            throw new UnsupportedOperationException("Unsupported platform for jwuff: " + label);
        }
        if (LOADED.compareAndSet(false, true)) {
            Path extracted = extractToTemp(platform);
            System.load(extracted.toAbsolutePath().toString());
        }
    }

    public static String resourcePathForCurrentPlatform() {
        Platform platform = detectPlatform();
        if (!platform.supported) return null;
        return "/natives/" + platform.platformId + "/" + platform.libraryFileName;
    }

    private static Path extractToTemp(Platform platform) {
        String resourcePath = "/natives/" + platform.platformId + "/" + platform.libraryFileName;
        try (InputStream in = NativeLibrary.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Missing native library resource: " + resourcePath);
            }
            Path dir = Files.createTempDirectory("jwuff-natives-");
            dir.toFile().deleteOnExit();
            Path out = dir.resolve(platform.libraryFileName);
            Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
            out.toFile().deleteOnExit();
            return out;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to extract native library", e);
        }
    }

    private static Platform detectPlatform() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String archName = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);

        String os;
        String ext;
        if (osName.contains("mac")) {
            os = "macos";
            ext = "dylib";
        } else if (osName.contains("win")) {
            os = "win32";
            ext = "dll";
        } else if (osName.contains("linux")) {
            os = "linux";
            ext = "so";
        } else {
            return new Platform(osName, archName, false, null, null);
        }

        String arch;
        if (archName.equals("aarch64") || archName.equals("arm64")) {
            arch = "arm64";
        } else if (archName.equals("x86_64") || archName.equals("amd64") || archName.equals("x64")) {
            arch = "x86_64";
        } else {
            return new Platform(os, archName, false, null, null);
        }

        boolean supported =
                (os.equals("macos") && (arch.equals("arm64") || arch.equals("x86_64"))) ||
                (os.equals("linux") && arch.equals("x86_64")) ||
                (os.equals("win32") && arch.equals("x86_64"));

        if (!supported) {
            return new Platform(os, arch, false, null, null);
        }

        String platformId = switch (os) {
            case "macos" -> arch.equals("arm64") ? "arm64-macos" : "x86_64-macos";
            case "linux" -> "x86_64-linux";
            case "win32" -> "x86_64-win32";
            default -> null;
        };
        String libName = os.equals("win32") ? "wuffs_imageio.dll" : ("libwuffs_imageio." + ext);
        return new Platform(os, arch, true, platformId, libName);
    }

    private record Platform(
            String os,
            String arch,
            boolean supported,
            String platformId,
            String libraryFileName) {}
}

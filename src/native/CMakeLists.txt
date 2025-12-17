cmake_minimum_required(VERSION 3.22)

project(wuffs_imageio C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

# macOS minimum supported version (Big Sur).
if(APPLE AND NOT DEFINED CMAKE_OSX_DEPLOYMENT_TARGET)
    set(CMAKE_OSX_DEPLOYMENT_TARGET "11.0" CACHE STRING "Minimum macOS version" FORCE)
endif()

add_library(wuffs_imageio SHARED
        wuffs_imageio_abi.c
        third_party/wuffs/release/c/wuffs-unsupported-snapshot.c
)

target_include_directories(wuffs_imageio PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

set_source_files_properties(
        third_party/wuffs/release/c/wuffs-unsupported-snapshot.c
        PROPERTIES
        COMPILE_DEFINITIONS
        "WUFFS_IMPLEMENTATION;WUFFS_CONFIG__MODULES;WUFFS_CONFIG__MODULE__BASE;WUFFS_CONFIG__MODULE__ADLER32;WUFFS_CONFIG__MODULE__CRC32;WUFFS_CONFIG__MODULE__DEFLATE;WUFFS_CONFIG__MODULE__ZLIB;WUFFS_CONFIG__MODULE__JPEG;WUFFS_CONFIG__MODULE__PNG"
)

# Help the linker strip unused code.
if(APPLE)
    # Optimize for size/speed and strip dead code.
    target_compile_options(wuffs_imageio PRIVATE -ffunction-sections -fdata-sections -fvisibility=hidden)
    target_link_options(wuffs_imageio PRIVATE -Wl,-dead_strip)

    # Prefer aggressive optimization and LTO for Release builds.
    target_compile_options(wuffs_imageio PRIVATE $<$<CONFIG:Release>:-O3>)
    set_property(TARGET wuffs_imageio PROPERTY INTERPROCEDURAL_OPTIMIZATION $<$<CONFIG:Release>:TRUE>)
endif()

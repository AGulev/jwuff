cmake_minimum_required(VERSION 3.22)

project(wuffs_imageio C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

# macOS minimum supported version (Big Sur).
if(APPLE AND NOT DEFINED CMAKE_OSX_DEPLOYMENT_TARGET)
    set(CMAKE_OSX_DEPLOYMENT_TARGET "11.0" CACHE STRING "Minimum macOS version" FORCE)
endif()

set(WUFFS_IMAGEIO_SOURCES
        wuffs_imageio_abi.c
        third_party/wuffs/release/c/wuffs-unsupported-snapshot.c
)

set(WUFFS_COMPILE_DEFS
        "WUFFS_IMPLEMENTATION;WUFFS_CONFIG__MODULES;WUFFS_CONFIG__MODULE__BASE;WUFFS_CONFIG__MODULE__ADLER32;WUFFS_CONFIG__MODULE__CRC32;WUFFS_CONFIG__MODULE__DEFLATE;WUFFS_CONFIG__MODULE__ZLIB;WUFFS_CONFIG__MODULE__JPEG;WUFFS_CONFIG__MODULE__PNG"
)

function(configure_wuffs_target target_name)
    target_include_directories(${target_name} PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

    set_source_files_properties(
            third_party/wuffs/release/c/wuffs-unsupported-snapshot.c
            PROPERTIES
            COMPILE_DEFINITIONS
            "${WUFFS_COMPILE_DEFS}"
    )

    # Optimize aggressively for Release builds and strip unused code.
    if(MSVC)
        # /O2: optimize, /GL + /LTCG: link-time optimization, /Gw /Gy: section-level linking.
        target_compile_options(${target_name} PRIVATE $<$<CONFIG:Release>:/O2 /GL /Gw /Gy>)
        target_compile_definitions(${target_name} PRIVATE $<$<CONFIG:Release>:NDEBUG>)
        target_link_options(${target_name} PRIVATE $<$<CONFIG:Release>:/LTCG /OPT:REF /OPT:ICF>)
    elseif(APPLE)
        target_compile_options(${target_name} PRIVATE -ffunction-sections -fdata-sections -fvisibility=hidden)
        target_link_options(${target_name} PRIVATE -Wl,-dead_strip)
        target_compile_options(${target_name} PRIVATE $<$<CONFIG:Release>:-O3>)
        set_property(TARGET ${target_name} PROPERTY INTERPROCEDURAL_OPTIMIZATION $<$<CONFIG:Release>:TRUE>)
    else()
        # GCC/Clang (Linux): O3 + LTO + gc-sections (similar to Apple's dead_strip).
        target_compile_options(${target_name} PRIVATE -ffunction-sections -fdata-sections -fvisibility=hidden)
        target_compile_options(${target_name} PRIVATE $<$<CONFIG:Release>:-O3>)
        target_link_options(${target_name} PRIVATE -Wl,--gc-sections)
        set_property(TARGET ${target_name} PROPERTY INTERPROCEDURAL_OPTIMIZATION $<$<CONFIG:Release>:TRUE>)
    endif()
endfunction()

add_library(wuffs_imageio SHARED ${WUFFS_IMAGEIO_SOURCES})
configure_wuffs_target(wuffs_imageio)

# Optional AVX2 variant for x86_64 (kept separate for broad compatibility).
if(CMAKE_SYSTEM_PROCESSOR MATCHES "^(x86_64|AMD64)$")
    add_library(wuffs_imageio_avx2 SHARED ${WUFFS_IMAGEIO_SOURCES})
    configure_wuffs_target(wuffs_imageio_avx2)

    if(MSVC)
        target_compile_options(wuffs_imageio_avx2 PRIVATE /arch:AVX2)
    else()
        target_compile_options(wuffs_imageio_avx2 PRIVATE -mavx2 -mfma)
    endif()
endif()

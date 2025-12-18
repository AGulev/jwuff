#pragma once

#include <stddef.h>
#include <stdint.h>

#if defined(_WIN32)
  #define WUFFS_IMAGEIO_API __declspec(dllexport)
#elif defined(__GNUC__) || defined(__clang__)
  #define WUFFS_IMAGEIO_API __attribute__((visibility("default")))
#else
  #define WUFFS_IMAGEIO_API
#endif

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
  uint32_t width;
  uint32_t height;
  uint32_t frame_count;
  uint32_t bytes_per_pixel;
  uint32_t stride_bytes;
} wuffs_probe_result;

typedef struct {
  uint32_t pixel_format;
  uint32_t flags;
} wuffs_decode_params;

typedef struct {
  uint32_t width;
  uint32_t height;
  uint32_t stride_bytes;
  uint32_t bytes_written;
} wuffs_frame_result;

WUFFS_IMAGEIO_API int wuffs_probe_image(const uint8_t* data, size_t len, wuffs_probe_result* out);

WUFFS_IMAGEIO_API int wuffs_decode_frame_into(
    const uint8_t* data, size_t len,
    uint32_t frame_index,
    const wuffs_decode_params* params,
    uint8_t* dst_pixels, size_t dst_len,
    wuffs_frame_result* out);

// Returns 1 if this CPU+OS can execute AVX2 instructions safely, otherwise 0.
WUFFS_IMAGEIO_API int wuffs_cpu_supports_avx2(void);

WUFFS_IMAGEIO_API const char* wuffs_error_message(int code);

#ifdef __cplusplus
}  // extern "C"
#endif

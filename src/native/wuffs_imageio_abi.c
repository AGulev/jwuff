#include "wuffs_imageio_abi.h"

// Compile Wuffs as its own translation unit (see CMakeLists.txt) and include
// this file here for declarations only.
#include "third_party/wuffs/release/c/wuffs-unsupported-snapshot.c"

#include <stdlib.h>

#if defined(__x86_64__) || defined(_M_X64) || defined(__i386__) || defined(_M_IX86)
  #if defined(_MSC_VER)
    #include <intrin.h>
  #else
    #include <cpuid.h>
  #endif
#endif

enum {
  WUFFS_IMAGEIO_OK = 0,
  WUFFS_IMAGEIO_ERR_INVALID_ARGUMENT = -1,
  WUFFS_IMAGEIO_ERR_UNSUPPORTED_FORMAT = -2,
  WUFFS_IMAGEIO_ERR_WUFFS = -3,
  WUFFS_IMAGEIO_ERR_NOT_IMPLEMENTED = -4,
};

static _Thread_local const char* wuffs_imageio_last_error;

static void wuffs_imageio_set_error(const char* msg) {
  wuffs_imageio_last_error = msg;
}

static int wuffs_imageio_fail_wuffs(wuffs_base__status status) {
  if (wuffs_base__status__is_ok(&status)) {
    wuffs_imageio_set_error("wuffs error");
  } else {
    wuffs_imageio_set_error(status.repr ? status.repr : "wuffs error");
  }
  return WUFFS_IMAGEIO_ERR_WUFFS;
}

WUFFS_IMAGEIO_API int wuffs_probe_image(const uint8_t* data, size_t len, wuffs_probe_result* out) {
  wuffs_imageio_set_error(NULL);
  if (!data || !out) {
    wuffs_imageio_set_error("invalid argument");
    return WUFFS_IMAGEIO_ERR_INVALID_ARGUMENT;
  }
  if (len == 0) {
    wuffs_imageio_set_error("invalid argument");
    return WUFFS_IMAGEIO_ERR_INVALID_ARGUMENT;
  }

  wuffs_base__slice_u8 prefix = wuffs_base__make_slice_u8((uint8_t*)data, len);
  int32_t fourcc = wuffs_base__magic_number_guess_fourcc(prefix, true);

  wuffs_base__image_decoder* decoder = NULL;

  switch (fourcc) {
    case WUFFS_BASE__FOURCC__JPEG:
      decoder = wuffs_jpeg__decoder__alloc_as__wuffs_base__image_decoder();
      break;
    case WUFFS_BASE__FOURCC__PNG:
      decoder = wuffs_png__decoder__alloc_as__wuffs_base__image_decoder();
      break;
    default:
      wuffs_imageio_set_error("unsupported format");
      return WUFFS_IMAGEIO_ERR_UNSUPPORTED_FORMAT;
  }

  if (!decoder) {
    wuffs_imageio_set_error("out of memory");
    return WUFFS_IMAGEIO_ERR_WUFFS;
  }

  wuffs_base__image_config image_config = wuffs_base__null_image_config();
  wuffs_base__io_buffer src =
      wuffs_base__ptr_u8__reader((uint8_t*)data, len, true);
  wuffs_base__status status =
      wuffs_base__image_decoder__decode_image_config(decoder, &image_config, &src);
  if (!wuffs_base__status__is_ok(&status)) {
    free(decoder);
    return wuffs_imageio_fail_wuffs(status);
  }

  uint32_t w = wuffs_base__pixel_config__width(&image_config.pixcfg);
  uint32_t h = wuffs_base__pixel_config__height(&image_config.pixcfg);
  if ((w == 0) || (h == 0)) {
    free(decoder);
    return wuffs_imageio_fail_wuffs(wuffs_base__make_status(wuffs_base__error__bad_argument));
  }

  out->width = w;
  out->height = h;
  out->frame_count = 1;
  out->bytes_per_pixel = 4;
  out->stride_bytes = w * 4;
  free(decoder);
  return WUFFS_IMAGEIO_OK;
}

WUFFS_IMAGEIO_API int wuffs_decode_frame_into(
    const uint8_t* data, size_t len,
    uint32_t frame_index,
    const wuffs_decode_params* params,
    uint8_t* dst_pixels, size_t dst_len,
    wuffs_frame_result* out) {
  wuffs_imageio_set_error(NULL);
  if (!data || !dst_pixels || !out) {
    wuffs_imageio_set_error("invalid argument");
    return WUFFS_IMAGEIO_ERR_INVALID_ARGUMENT;
  }
  if (len == 0) {
    wuffs_imageio_set_error("invalid argument");
    return WUFFS_IMAGEIO_ERR_INVALID_ARGUMENT;
  }
  if (frame_index != 0) {
    wuffs_imageio_set_error("not implemented");
    return WUFFS_IMAGEIO_ERR_NOT_IMPLEMENTED;
  }

  wuffs_base__slice_u8 prefix = wuffs_base__make_slice_u8((uint8_t*)data, len);
  int32_t fourcc = wuffs_base__magic_number_guess_fourcc(prefix, true);

  wuffs_base__image_decoder* decoder = NULL;
  switch (fourcc) {
    case WUFFS_BASE__FOURCC__JPEG:
      decoder = wuffs_jpeg__decoder__alloc_as__wuffs_base__image_decoder();
      break;
    case WUFFS_BASE__FOURCC__PNG:
      decoder = wuffs_png__decoder__alloc_as__wuffs_base__image_decoder();
      break;
    default:
      wuffs_imageio_set_error("unsupported format");
      return WUFFS_IMAGEIO_ERR_UNSUPPORTED_FORMAT;
  }

  if (!decoder) {
    wuffs_imageio_set_error("out of memory");
    return WUFFS_IMAGEIO_ERR_WUFFS;
  }

  uint32_t dst_pixfmt = WUFFS_BASE__PIXEL_FORMAT__BGRA_NONPREMUL;
  if (params && params->pixel_format) {
    dst_pixfmt = params->pixel_format;
  }
  if (dst_pixfmt != WUFFS_BASE__PIXEL_FORMAT__BGRA_NONPREMUL) {
    free(decoder);
    wuffs_imageio_set_error("not implemented");
    return WUFFS_IMAGEIO_ERR_NOT_IMPLEMENTED;
  }

  wuffs_base__image_config image_config = wuffs_base__null_image_config();
  wuffs_base__io_buffer src =
      wuffs_base__ptr_u8__reader((uint8_t*)data, len, true);
  wuffs_base__status status =
      wuffs_base__image_decoder__decode_image_config(decoder, &image_config, &src);
  if (!wuffs_base__status__is_ok(&status)) {
    free(decoder);
    return wuffs_imageio_fail_wuffs(status);
  }

  uint32_t w = wuffs_base__pixel_config__width(&image_config.pixcfg);
  uint32_t h = wuffs_base__pixel_config__height(&image_config.pixcfg);
  if ((w == 0) || (h == 0)) {
    free(decoder);
    return wuffs_imageio_fail_wuffs(wuffs_base__make_status(wuffs_base__error__bad_argument));
  }

  uint64_t row_bytes = ((uint64_t)w) * 4;
  if ((row_bytes == 0) || (h == 0) || (row_bytes > (UINT64_MAX / (uint64_t)h))) {
    free(decoder);
    wuffs_imageio_set_error("invalid argument");
    return WUFFS_IMAGEIO_ERR_INVALID_ARGUMENT;
  }
  uint64_t expected = row_bytes * ((uint64_t)h);
  if (expected > UINT32_MAX) {
    free(decoder);
    wuffs_imageio_set_error("invalid argument");
    return WUFFS_IMAGEIO_ERR_INVALID_ARGUMENT;
  }
  if ((uint64_t)dst_len < expected) {
    free(decoder);
    wuffs_imageio_set_error("invalid argument");
    return WUFFS_IMAGEIO_ERR_INVALID_ARGUMENT;
  }

  wuffs_base__frame_config frame_config = wuffs_base__null_frame_config();
  status = wuffs_base__image_decoder__decode_frame_config(decoder, &frame_config, &src);
  if (!wuffs_base__status__is_ok(&status)) {
    free(decoder);
    return wuffs_imageio_fail_wuffs(status);
  }

  wuffs_base__pixel_config pixcfg = wuffs_base__null_pixel_config();
  wuffs_base__pixel_config__set(&pixcfg, dst_pixfmt,
                                WUFFS_BASE__PIXEL_SUBSAMPLING__NONE, w, h);

  wuffs_base__pixel_buffer pb;
  status = wuffs_base__pixel_buffer__set_from_slice(
      &pb, &pixcfg, wuffs_base__make_slice_u8(dst_pixels, dst_len));
  if (!wuffs_base__status__is_ok(&status)) {
    free(decoder);
    wuffs_imageio_set_error("invalid argument");
    return WUFFS_IMAGEIO_ERR_INVALID_ARGUMENT;
  }

  wuffs_base__range_ii_u64 workbuf_range =
      wuffs_base__image_decoder__workbuf_len(decoder);
  uint64_t workbuf_len_u64 = workbuf_range.max_incl;
  if (workbuf_len_u64 > (uint64_t)SIZE_MAX) {
    free(decoder);
    wuffs_imageio_set_error("invalid argument");
    return WUFFS_IMAGEIO_ERR_INVALID_ARGUMENT;
  }
  size_t workbuf_len = (size_t)workbuf_len_u64;
  uint8_t* workbuf_ptr = NULL;
  if (workbuf_len) {
    workbuf_ptr = (uint8_t*)malloc(workbuf_len);
    if (!workbuf_ptr) {
      free(decoder);
      wuffs_imageio_set_error("out of memory");
      return WUFFS_IMAGEIO_ERR_WUFFS;
    }
  }
  wuffs_base__slice_u8 workbuf = wuffs_base__make_slice_u8(workbuf_ptr, workbuf_len);

  status = wuffs_base__image_decoder__decode_frame(
      decoder, &pb, &src, WUFFS_BASE__PIXEL_BLEND__SRC, workbuf, NULL);
  if (workbuf_ptr) {
    free(workbuf_ptr);
  }
  if (!wuffs_base__status__is_ok(&status)) {
    free(decoder);
    return wuffs_imageio_fail_wuffs(status);
  }

  out->width = w;
  out->height = h;
  out->stride_bytes = w * 4;
  out->bytes_written = (uint32_t)expected;
  free(decoder);
  return WUFFS_IMAGEIO_OK;
}

WUFFS_IMAGEIO_API int wuffs_cpu_supports_avx2(void) {
#if defined(__x86_64__) || defined(_M_X64) || defined(__i386__) || defined(_M_IX86)
  // AVX2 requires:
  //  - CPU support (CPUID.(EAX=7,ECX=0):EBX.AVX2[bit 5])
  //  - OS support for XSAVE/XRESTORE + XMM/YMM state enabled (CPUID.(EAX=1):ECX.OSXSAVE + XGETBV)
  uint32_t eax = 0, ebx = 0, ecx = 0, edx = 0;

  // CPUID leaf 1: check OSXSAVE + AVX.
#if defined(_MSC_VER)
  int info1[4] = {0};
  __cpuidex(info1, 1, 0);
  ecx = (uint32_t)info1[2];
#else
  unsigned int a = 0, b = 0, c = 0, d = 0;
  if (!__get_cpuid(1, &a, &b, &c, &d)) {
    return 0;
  }
  ecx = (uint32_t)c;
#endif

  const uint32_t ecx_osxsave = (1u << 27);
  const uint32_t ecx_avx = (1u << 28);
  if (((ecx & ecx_osxsave) == 0) || ((ecx & ecx_avx) == 0)) {
    return 0;
  }

  // XGETBV(0): ensure XMM (bit 1) and YMM (bit 2) state enabled by OS.
  uint64_t xcr0 = 0;
#if defined(_MSC_VER)
  xcr0 = _xgetbv(0);
#else
  uint32_t xcr0_lo = 0, xcr0_hi = 0;
  __asm__ volatile("xgetbv" : "=a"(xcr0_lo), "=d"(xcr0_hi) : "c"(0));
  xcr0 = ((uint64_t)xcr0_hi << 32) | (uint64_t)xcr0_lo;
#endif
  if ((xcr0 & 0x6u) != 0x6u) {
    return 0;
  }

  // CPUID leaf 7 subleaf 0: check AVX2.
#if defined(_MSC_VER)
  int info7[4] = {0};
  __cpuidex(info7, 7, 0);
  ebx = (uint32_t)info7[1];
#else
  __cpuid_count(7, 0, eax, ebx, ecx, edx);
#endif
  const uint32_t ebx_avx2 = (1u << 5);
  return ((ebx & ebx_avx2) != 0) ? 1 : 0;
#else
  return 0;
#endif
}

WUFFS_IMAGEIO_API const char* wuffs_error_message(int code) {
  if ((code != 0) && wuffs_imageio_last_error) {
    return wuffs_imageio_last_error;
  }
  switch (code) {
    case WUFFS_IMAGEIO_OK:
      return "ok";
    case WUFFS_IMAGEIO_ERR_INVALID_ARGUMENT:
      return "invalid argument";
    case WUFFS_IMAGEIO_ERR_UNSUPPORTED_FORMAT:
      return "unsupported format";
    case WUFFS_IMAGEIO_ERR_WUFFS:
      return wuffs_imageio_last_error ? wuffs_imageio_last_error : "wuffs error";
    case WUFFS_IMAGEIO_ERR_NOT_IMPLEMENTED:
      return "not implemented";
    default:
      return "unknown error";
  }
}

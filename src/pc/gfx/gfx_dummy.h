#ifndef GFX_DUMMY_H
#define GFX_DUMMY_H

// The dummy (headless) backend is always compiled in so that it can be
// selected at runtime via the --no-render CLI flag, regardless of which
// real graphics backend the binary was built with.

#include "gfx_rendering_api.h"
#include "gfx_window_manager_api.h"

extern struct GfxRenderingAPI gfx_dummy_renderer_api;
extern struct GfxWindowManagerAPI gfx_dummy_wm_api;

#endif

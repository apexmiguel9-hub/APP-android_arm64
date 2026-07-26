OBlender — Blender 3.6 for Android ARM64 (Mali GPU Fork — Unofficial Port)

Fork of ePai's (https://github.com/dshawshank) Blender Android ARM64 port focused on identifying and fixing GPU compatibility bugs affecting ARM Mali devices, which represent a large share of the Android hardware market (Mediatek Dimensity/Helio, older Samsung Exynos) but historically receive less attention from mobile GPU driver testing than Adreno (Qualcomm Snapdragon).

**IMPORTANT**: This is an independent, non-commercial, community-driven **UNOFFICIAL PORT**. It is **NOT** affiliated with or endorsed by the Blender Foundation, by ePai, or by Zalith Launcher. Blender is a registered trademark of the Blender Foundation. This port is provided as-is with no guarantees of stability or suitability for production use.

Related repositories
This project is split across four repositories:
Blender-android_arm64 — Blender 3.6 source, patched for Android ARM64 and Mali GPU compatibility. Compiles to ~146 static libraries (.a) via GitHub Actions.
APP-android_arm64 (this repository) — The Android application shell, UI, virtual keyboard, and native bridge (JNI/GHOST) that links the static libraries into the final APK.
lib-android_arm64 — Precompiled third-party dependencies (Python, FFmpeg, OpenImageIO, Boost, TBB, OpenCOLLADA, etc.).
Utilities-android_arm64 — Build tooling used by the original ePai pipeline (MakeSDNA/MakeSRNA host tools). Superseded in this fork by the GitHub Actions workflow described below, kept for reference.
How to build
Both Blender-android_arm64 and this repository use GitHub Actions workflows that can be run with no local Android/NDK setup.
Build the static libraries: trigger .github/workflows/build-static-libs.yml in Blender-android_arm64 (manual workflow_dispatch, or on push to blender-v3.6-release). This produces a blender-android-arm64-static-libs artifact (a .zip containing the .a files), typically in 6–20 minutes depending on cache state.
Build the APK: trigger .github/workflows/release.yml in this repository. The pipeline fetches the latest successful Blender static-libs build, extracts the .a files, and produces a signed APK.
Requirements
Minimum
SoC: Mediatek Helio P65 (Mali-G52 MC2) or equivalent
RAM: 4 GB
Android 9 (API 28) or later
ARM64-v8a (64-bit) only — 32-bit-only devices are not supported
Recommended
SoC: Mediatek Helio G99 / Dimensity 7100 or equivalent
RAM: 8 GB
Android 10 or later
Current status
This is a work-in-progress fork under active development. The summary below reflects the state as of this writing and will go out of date; check commit history for the latest.

Fixed in this fork
EEVEE viewport, Shading Editor, and final render (F12) — previously produced a black screen or corrupted/noisy output on Mali. Root causes identified and fixed: a G-buffer layout in the material shader exceeded Mali's fragment output limit; GPU_RGBA32F was used as a render target format in several places, which Mali does not support as color-renderable (switched to GPU_RGBA16F); sign-loss bugs in cubeFaceCoordEEVEE() and spot_attenuation() broke Point, Area, and Spot lights under certain angles.
Workbench (Solid shading mode) — previously rendered fully black or with tile-aligned visual corruption on Mali, across all editors that use it (3D viewport, Sculpt Mode, Grease Pencil / 2D Animation). Root cause: glDrawBuffers() was called with a hardcoded attachment count that exceeded GL_MAX_DRAW_BUFFERS on Mali GLES, silently invalidating the framebuffer. Fixed by using the real active attachment count, plus adding a GPU_BARRIER_FRAMEBUFFER memory barrier appropriate for Mali's tile-based rendering architecture.
Sculpt Mode — tested functional up to ~397k vertices with no perceptible lag; usable up to ~687k vertices with degraded but workable performance, on a Helio P65.
Cloth and particle physics — functional; particle counts up to ~100k tested with acceptable performance.
Virtual keyboard — the on-screen key panel shipped by ePai was missing several keys relevant to common Blender workflows (K/Knife, L/Select Linked, P/Separate, U/Unwrap, several number and function keys, some punctuation). These have been added and the layout reorganized into a more predictable structure (modifiers, then letters in QWERTY order, then numbers, then function keys, then numpad/navigation).
Known issues / not yet fixed
Grease Pencil (2D Animation) stability — CRITICAL. This editor has caused full device reboots during testing on Mali hardware. Treat this mode as unstable and avoid relying on it until further notice. This is the highest-priority open issue.
App resume after minimize is unreliable. Returning to the app after minimizing (or after using F12 render followed by Esc) frequently shows a blank/grey viewport that requires one or more screen taps to redraw, and in some cases still renders corrupted or black content afterward, particularly after a render has occurred.
No practical way to exit the Render view. Currently requires switching the editor type from the corner dropdown and saving preferences.
Splash screen shows Blender 4.0 branding instead of the actual bundled version (3.6.22).
Android 12+ compatibility unverified.
License
GNU General Public License v3.0 (GPL-3.0). See LICENSE for the full text.
This project is distributed free of charge. It is not published on Google Play.
Third-party dependencies and precompiled libraries bundled with the APK may be covered by their own respective licenses (Apache 2.0, MIT, BSD, LGPL, CC0, etc.); their attribution is maintained in the THIRD-PARTY-LICENSES file under the Blender source distribution and in the respective package metadata shipped with the Python environment.

Acknowledgments
Built on a fork of ePai (https://github.com/dshawshank) original Blender Android ARM64 port. This fork's focus has been specifically on Mali GPU driver compatibility, which was not resolved in the upstream project at the time this work began.
The on-screen control layout editor is adapted from Zalith Launcher 2 (https://github.com/ZalithLauncher/ZalithLauncher2) — specifically the `:LayerController` module (Jetpack Compose), distributed under GPL-3.0. Portions of adapted code carry the original copyright header.

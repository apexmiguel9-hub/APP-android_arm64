OBlender — Blender 3.6 for Android ARM64 (Mali GPU Fork)
Fork of ePai's Blender Android port focused on identifying and fixing GPU compatibility bugs affecting ARM Mali devices, which represent a large share of the Android hardware market (Mediatek Dimensity/Helio, older Samsung Exynos) but historically receive less attention from mobile GPU driver testing than Adreno (Qualcomm Snapdragon).
This is an independent, non-commercial, community-driven effort. It is not affiliated with the Blender Foundation or with ePai.
Related repositories
This project is split across four repositories:
Blender-android_arm64 — Blender 3.6 source, patched for Android ARM64 and Mali GPU compatibility. Compiles to ~146 static libraries (.a) via GitHub Actions.
APP-android_arm64 (this repository) — The Android application shell (Java/Kotlin), UI, virtual keyboard, and native bridge (JNI/GHOST) that links the static libraries into the final APK.
lib-android_arm64 — Precompiled third-party dependencies (Python, FFmpeg, OpenImageIO, Boost, TBB, OpenCOLLADA, etc.).
Utilities-android_arm64 — Build tooling used by the original ePai pipeline (MakeSDNA/MakeSRNA host tools). Superseded in this fork by the GitHub Actions workflow described below, kept for reference.
How to build
Both Blender-android_arm64 and this repository use GitHub Actions workflows that can be run with no local Android/NDK setup.
Build the static libraries: trigger .github/workflows/build-static-libs.yml in Blender-android_arm64 (manual workflow_dispatch, or on push to blender-v3.6-release). This produces a blender-android-arm64-static-libs artifact (a .zip containing the .a files), typically in 6–20 minutes depending on cache state.
Build the APK: trigger .github/workflows/build-apk.yml in this repository, passing the blender_run_id input with the run ID from step 1 explicitly. Do not rely on "latest successful run" without checking it corresponds to the intended commit — this pipeline has no automatic cross-repo trigger by design, to keep control over which Blender build feeds which APK build.
The resulting blender-android-apk artifact is signed with the Android debug key (v1+v2+v3 signing schemes enabled) for testing purposes. A dedicated release keystore has not yet been set up; see Roadmap.
Toolchain: NDK r21.4.7075529 (r21e), Android API 24 (compile target), Gradle 6.5, Java 17 for SDK tooling / Java 11 for the Gradle build itself.
Read both workflow YAML files directly for the exact build steps, including the Windows-path patching applied at build time to the original ePai source (which hardcodes D:/... and F:/... paths from the original developer's machine).
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
Compatibility with Android 12+ has not been verified on real hardware at time of writing (no 64-bit Android 12+ device was available for testing during this development cycle). The manifest does not declare an upper SDK bound, so installation is expected to succeed, but this remains unconfirmed. Compatibility with Qualcomm Adreno and Samsung Xclipse GPUs is also unverified; the fixes in this fork specifically target Mali/GLES driver behavior and have only been tested on Mali hardware (Helio P65, Helio G80).
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
App resume after minimize is unreliable. Returning to the app after minimizing (or after using F12 render followed by Esc) frequently shows a blank/grey viewport that requires one or more screen taps to redraw, and in some cases still renders corrupted or black content afterward, particularly after a render has occurred. A partial fix (correcting a stale window-manager mapping in wmInitReInit()) improved but did not fully resolve this; diagnostic logging is in place and root-causing is ongoing. Given the same visual corruption pattern has been observed in unrelated contexts, this is being treated with the same level of caution as the Grease Pencil instability above until proven otherwise.
No practical way to exit the Render view. Currently requires switching the editor type from the corner dropdown and saving preferences, rather than a direct close action. Documented as a known issue in the original ePai project as well.
Splash screen shows Blender 4.0 branding instead of the actual bundled version (3.6.22), which may mislead users about what they are running.
First-launch storage permission dialog is in Chinese, with no English or Spanish fallback string.
Android 12+ compatibility unverified (see Requirements section above).
86+ additional issues were filed against the original ePai project (crashes on open/close, import bugs, UI issues, etc.) that have not yet been triaged against this fork. Many may be resolved as a side effect of the fixes above; this has not been systematically verified.
UI and usability
The default UI scale (1.80, against a maximum of 2.0 in Blender's own preferences) causes the workspace tab bar to be cut off with no way to scroll to hidden tabs, making some workspaces (Sculpting, UV Editing, Shading, Animation, etc.) inaccessible without manually lowering the scale first. Adjusting the default shipped preference to a lower value is a planned near-term fix; see Roadmap.
Touch input for standard Blender UI elements (buttons, menus, sliders) is handled via Godot integration. Touch input for 3D viewport navigation (orbit, pan, zoom, gizmo manipulation) uses ePai's own implementation rather than Godot's viewport handling, which the original developer left disabled in source (commented out) in favor of a custom system. This custom viewport input system has not been evaluated for quality or correctness in this fork and may be a source of some of the "less practical" interaction issues noted by users.
Roadmap
Not committed to any timeline; listed in rough priority order.
Resolve the Grease Pencil / device reboot issue (critical, safety-related).
Root-cause and fix the app resume / render-exit corruption issue.
Add a proper close/cancel affordance for the Render view.
Correct the splash screen version display and translate the first-launch permission dialog.
Lower the default UI scale to a value that keeps all workspace tabs visible and reachable on common screen sizes.
Replace or supplement the current Java/Canvas-based virtual keyboard with a configurable shortcut system — likely implemented as a native Blender addon (Python, using Blender's own panel/operator and keymap systems) rather than an Android-side overlay, to preserve compatibility with Blender's standard addon ecosystem.
Investigate raising targetSdkVersion (currently API 30) and validate real-world compatibility on Android 12+ / Adreno / Xclipse hardware as such devices become available for testing.
Triage the ~86 issues open against the original ePai project against this fork.
License
GNU General Public License v2.0 (GPL-2.0), consistent with Blender's own licensing and the license under which the original ePai fork was distributed. See LICENSE for the full text.
This project is distributed free of charge, with optional donations to support continued development. It is not published on Google Play.
Acknowledgments
Built on a fork of ePai's (dshawshank) original Blender Android ARM64 port. This fork's focus has been specifically on Mali GPU driver compatibility, which was not resolved in the upstream project at the time this work began.

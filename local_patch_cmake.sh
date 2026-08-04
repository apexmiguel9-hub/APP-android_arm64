#!/usr/bin/env bash
# local_patch_cmake.sh
# Parche LOCAL de CMakeLists.txt de la app para build fuera de CI (ej. prototipos UI
# en otro dispositivo). Replica exactamente el sed del release.yml (steps 108-127)
# sustituyendo las rutas Windows hardcodeadas D:/FreeProjects/... por ABS paths locales.
#
# REQUISITOS previos (ver AGENTS.md):
#  1. .a de Blender extraidas en: $BLENDER_LIBS_RELEASE_DIR/lib/
#     (artifact "blender-android-arm64-static-libs" -> run LATEST_BLENDER_RUN)
#       gh run download <RUN_ID> --repo apexmiguel9-hub/Blender-android_arm64 \
#           --name blender-android-arm64-static-libs -D /tmp/blender-libs
#     luego:  mkdir -p <dir>/lib  &&  cp /tmp/blender-libs/*.a <dir>/lib/
#
#  2. repo lib-android_arm64 clonado (solo para libjpeg.a/pugixml.a/libepoxy.a de
#     lineas 416-418 + headers de boost/openvdb/tbb). Si el artifact de Blender ya
#     incluye esas .a, apunte $LIB_ANDROID_ARM64 al mismo dir del punto 1.
#       git clone https://github.com/apexmiguel9-hub/lib-android_arm64.git
#
#  3. repo Blender-android_arm64 clonado (headers, para includes):
#       git clone -b blender-v3.6-release \
#           https://github.com/apexmiguel9-hub/Blender-android_arm64.git
#
#  4. generar buildinfo.h/buildinfo_static.h con cmake -P (steps 129-166 del release.yml).
#
# NOTA: este script es LOCAL-ONLY. CMakeLists.txt esta en .gitignore? NO, esta trackeado,
# asi que si corresta un `git diff` despues veras los cambios; committea NUNCA (el CI
# aplica su propio sed). Para revertir: git checkout -- CMakeLists.txt
set -euo pipefail

# EDITA ESTOS PATHS (usar ABS, sin ~, sin trailing slash):
LIB_ANDROID_ARM64="${LIB_ANDROID_ARM64:-$PWD/lib-android_arm64}"
BLENDER_ANDROID_ARM64="${BLENDER_ANDROID_ARM64:-$PWD/Blender-android_arm64}"
# Dir donde pusiste las .a de Blender (el artifact extraído). El CMake le anexa /lib/
# y /source/creator/ en los include dirs.
BLENDER_LIBS_RELEASE_DIR="${BLENDER_LIBS_RELEASE_DIR:-$PWD/OBlender/app/.cxx/blender-libs/release/arm64-v8a}"

CMAKE="$PWD/OBlender/app/src/main/cpp/CMakeLists.txt"

if [[ ! -f "$CMAKE" ]]; then
  echo "ERROR: no encuentro $CMAKE (ejecutar desde repo root)" >&2
  exit 1
fi

echo "=== CMakeLists.txt : antes ==="
grep -n "D:/FreeProjects" "$CMAKE" || echo "(ningun D:/FreeProjects restante)"

sed -i \
  -e "s|D:/FreeProjects/Blender/Blender/lib-android_arm64|${LIB_ANDROID_ARM64}|g" \
  -e "s|D:/FreeProjects/Blender/Blender/Blender-android_arm64|${BLENDER_ANDROID_ARM64}|g" \
  -e "s|D:/FreeProjects/Blender/Blender/Utilities-android_arm64/MakeBlender/app/.cxx/cmake/release/arm64-v8a|${BLENDER_LIBS_RELEASE_DIR}|g" \
  -e "s|D:/FreeProjects/Blender/Blender/Utilities-android_arm64/MakeBlender/app/.cxx/cmake/debug/arm64-v8a|${BLENDER_LIBS_RELEASE_DIR}|g" \
  "$CMAKE"

echo
echo "=== CMakeLists.txt : despues ==="
grep -n "FreeProjects\|blenderLibLibDirRelease\|libjpeg.a" "$CMAKE" || echo "(paths parcheados OK)"
echo
echo "Paths resultantes:"
echo "  blenderLibLibDirRelease -> $(grep -o '.*blenderLibLibDirRelease[^ ]*' "$CMAKE" | head -1)"
echo "  .a count esperados en:  ${BLINNER:-${BLENDER_LIBS_RELEASE_DIR}}/lib -> $(ls "${BLENDER_LIBS_RELEASE_DIR}/lib/"*.a 2>/dev/null | wc -l)"
echo
echo "Para revertir:  git checkout -- OBlender/app/src/main/cpp/CMakeLists.txt"
echo "Para build:     cd OBlender && ./gradlew assembleDebug --no-daemon"

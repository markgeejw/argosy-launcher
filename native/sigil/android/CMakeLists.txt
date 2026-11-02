cmake_minimum_required(VERSION 3.22.1)
project(argosy-sigil-jni C)

get_filename_component(SIGIL_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../sigil" ABSOLUTE)
add_subdirectory("${SIGIL_ROOT}/bindings/android/src/main/cpp" sigil-jni-build)
include("${CMAKE_CURRENT_SOURCE_DIR}/../ApplyPatch.cmake")
argosy_patch_sigil()

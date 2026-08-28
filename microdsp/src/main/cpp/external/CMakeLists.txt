# External dependencies
set(ENABLE_FLOAT ON CACHE BOOL "Build single precision" FORCE)
set(ENABLE_THREADS ON CACHE BOOL "Use pthread for multithreading" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "Build static lib" FORCE)
set(BUILD_TESTS OFF CACHE BOOL "Don't build tests" FORCE)

# 1. libusb (Target: usb-1.0)
add_subdirectory(libusb)

# 2. FFTW (Target: fftw3f)
add_subdirectory(fftw-3.3.11)

# 3. Handle libhackrf dependencies
# Create the imported target libhackrf's FindLIBUSB expects
if(NOT TARGET LIBUSB::LIBUSB)
    add_library(LIBUSB::LIBUSB INTERFACE IMPORTED)
    set_target_properties(LIBUSB::LIBUSB PROPERTIES
        INTERFACE_LINK_LIBRARIES usb-1.0
        INTERFACE_INCLUDE_DIRECTORIES ${CMAKE_CURRENT_SOURCE_DIR}/libusb/libusb
    )
endif()

# 3. libhackrf (Android specialized build)
# We don't use add_subdirectory(libhackrf/host) because we want to avoid
# modifying the library sources directly. Instead, we use a "glue" file
# that includes the original source to access static members.

add_library(hackrf_android STATIC
    ${CMAKE_CURRENT_SOURCE_DIR}/../src/hardware/hackrf/hackrf_android_glue.c
)

target_include_directories(hackrf_android
    PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/libhackrf/host/libhackrf/src
    ${CMAKE_CURRENT_SOURCE_DIR}/libusb/libusb
)

target_compile_definitions(hackrf_android PRIVATE
    LIBRARY_VERSION="0.10.0"
    LIBRARY_RELEASE="2026.08.1"
)

target_link_libraries(hackrf_android PRIVATE usb-1.0)

# Interface for main module to consume everything
add_library(external_dependencies INTERFACE)
target_link_libraries(external_dependencies
        INTERFACE
        fftw3f
        usb-1.0
        hackrf_android
)

target_include_directories(external_dependencies INTERFACE
        ${CMAKE_CURRENT_SOURCE_DIR}/fftw-3.3.11/api
        ${CMAKE_CURRENT_SOURCE_DIR}/libhackrf/host/libhackrf/src
)

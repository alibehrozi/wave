# Set the minimum required version of CMake
cmake_minimum_required(VERSION 3.20)
cmake_policy(SET CMP0054 NEW)
project(wave LANGUAGES C CXX)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Wall -Wunused-function")

# libusb
set(libusb usb-1.0)
add_subdirectory(libs/libusb)

# Include the libfftw3 in single precision mode (libfftw3f)
set(libfftw3 fftw3f)
set(BUILD_TESTS OFF)
set(ENABLE_FLOAT ON)
set(ENABLE_THREADS ON)
set(DISABLE_FORTRAN ON)

set(FFTW_LIBRARIES ${libfftw3})
set(FFTW_INCLUDES ${CMAKE_CURRENT_SOURCE_DIR}/libs/fftw-3.3.10/api)

add_subdirectory(libs/fftw-3.3.10)

# Include the libhackrf
set(libhackrf hackrf)
set(LIBUSB_LIBRARIES ${libusb})
set(LIBUSB_INCLUDE_DIR libs/libusb)
set(THREADS_PTHREAD_ARG "-pthread")
add_subdirectory(libs/libhackrf/host)

target_include_directories(${libhackrf} PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/libs/fftw-3.3.10/api
        ${CMAKE_CURRENT_SOURCE_DIR}/libs/libusb)

target_link_libraries(
        ${libhackrf}
        ${libfftw3})

set(libwave wave)
add_library(${libwave} SHARED
        ${CMAKE_CURRENT_SOURCE_DIR}/HackRFTransfer.cpp
        ${CMAKE_CURRENT_SOURCE_DIR}/HackRFWrapper.cpp
        test.c)

target_include_directories(${libwave} PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/dataStream
        ${CMAKE_CURRENT_SOURCE_DIR}/libs/fftw-3.3.10/api
        ${CMAKE_CURRENT_SOURCE_DIR}/libs/libusb
        ${CMAKE_CURRENT_SOURCE_DIR}/libs/libhackrf/host/libhackrf/src)

# stream bridge
set(libstream dataStream)
add_subdirectory(dataStream)

# Include libraries
target_link_libraries(
        ${libwave}
        ${libfftw3}
        ${libhackrf}
        ${libstream}
        log)
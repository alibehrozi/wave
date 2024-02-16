# Set the minimum required version of CMake
cmake_minimum_required(VERSION 3.20)
project(wave LANGUAGES C CXX)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Wall")

# libusb
set(libusb usb-1.0)
add_subdirectory(libs/libusb)

# Include the libhackrf
set(libhackrf hackrf)
set(LIBUSB_LIBRARIES ${libusb})
set(LIBUSB_INCLUDE_DIR libs/libusb)
set(THREADS_PTHREAD_ARG "-pthread")
add_subdirectory(libs/libhackrf/host/libhackrf)

set(libwave wave)
add_library(${libwave} SHARED
        test.c)

target_include_directories(${libwave} PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/dataStream
        ${CMAKE_CURRENT_SOURCE_DIR}/libs/libusb
        ${CMAKE_CURRENT_SOURCE_DIR}/libs/libhackrf/host/libhackrf/src)

# stream bridge
set(libstream dataStream)
add_subdirectory(dataStream)

# Include libraries
target_link_libraries(
        ${libwave}
        ${libhackrf}
        ${libstream}
        log)
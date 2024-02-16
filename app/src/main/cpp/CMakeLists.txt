# Set the minimum required version of CMake
cmake_minimum_required(VERSION 3.20)
project(wave LANGUAGES C CXX)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Wall")

# libusb
set(libusb usb-1.0)
add_subdirectory(libs/libusb)

set(libwave wave)
add_library(${libwave} SHARED
        test.c)

target_include_directories(${libwave} PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/dataStream
        ${CMAKE_CURRENT_SOURCE_DIR}/libs/libusb)

# stream bridge
set(libstream dataStream)
add_subdirectory(dataStream)

# Include libraries
target_link_libraries(
        ${libwave}
        ${libstream}
        log)
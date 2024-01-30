# Set the minimum required version of CMake
cmake_minimum_required(VERSION 3.20)
project(wave LANGUAGES C)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Wall")

set(libwave wave)
add_library(${libwave} SHARED
        test.c)

# Include libraries
target_link_libraries(
        ${libwave}
        log)
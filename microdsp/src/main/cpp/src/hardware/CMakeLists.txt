add_library(hardware OBJECT
        hackrf/HackRFDevice.cpp
        hackrf/HackRFInfo.cpp
        hackrf/HackrfJni.cpp
        hackrf/HackRFInfo_jni.cpp
)

target_include_directories(hardware
        PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/..
)

target_link_libraries(hardware
        PUBLIC
        external_dependencies
        android
        log
)

# Subdirectories for components
add_subdirectory(core)
add_subdirectory(blocks)
add_subdirectory(hardware)

# Main library
add_library(microdsp SHARED
        microdsp.cpp
        microdspJni.cpp
)

target_include_directories(microdsp PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(microdsp
        PRIVATE
        core
        blocks
        hardware
        external_dependencies
        android
        log
        OpenSLES
)

# Install rules
install(TARGETS microdsp
        LIBRARY DESTINATION lib
        ARCHIVE DESTINATION lib
        RUNTIME DESTINATION bin
)

install(DIRECTORY ./
        DESTINATION include/microdsp
        FILES_MATCHING PATTERN "*.h"
)

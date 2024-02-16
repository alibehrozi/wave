add_library(${libstream} SHARED
        ${CMAKE_CURRENT_SOURCE_DIR}/BuffersStorage.cpp
        ${CMAKE_CURRENT_SOURCE_DIR}/ByteArray.cpp
        ${CMAKE_CURRENT_SOURCE_DIR}/ByteStream.cpp
        ${CMAKE_CURRENT_SOURCE_DIR}/ByteStreamWrapper.cpp
        ${CMAKE_CURRENT_SOURCE_DIR}/NativeByteBuffer.cpp
        ${CMAKE_CURRENT_SOURCE_DIR}/NativeByteBufferWrapper.cpp
        )

# Include libraries
target_link_libraries(
        ${libstream}
        log)
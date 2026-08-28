add_library(core OBJECT
        Block.cpp
        BufferPool.cpp
        Flowgraph.cpp
        Port.cpp
        RingBuffer.cpp
        Scheduler.cpp
        Types.cpp
)

target_include_directories(core
        PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/..
)

target_link_libraries(core
        PUBLIC
        external_dependencies
)

cmake_minimum_required(VERSION 3.10.2)

project("native-lib")

# Add the prebuilt libnode
add_library(node SHARED IMPORTED)
set_target_properties(
    node
    PROPERTIES IMPORTED_LOCATION
    ${CMAKE_SOURCE_DIR}/../../../libnode/bin/${ANDROID_ABI}/libnode.so
)

# Set include directories for Node headers
include_directories(${CMAKE_SOURCE_DIR}/../../../libnode/include/node)

add_library(
    native-lib
    SHARED
    native-lib.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    native-lib
    node
    ${log-lib}
)

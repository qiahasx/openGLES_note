


cmake_minimum_required(VERSION 3.22.1)
project("opengl")

SET(SOURCE_STB_IMAGE libary/libstbImage)
INCLUDE_DIRECTORIES(${SOURCE_STB_IMAGE}/include)

add_compile_definitions(GLM_ENABLE_EXPERIMENTAL)
SET(SOURCE_GLM libary/libglm)
INCLUDE_DIRECTORIES(${SOURCE_GLM}/include)

add_library(${CMAKE_PROJECT_NAME} SHARED
        GLRender.cpp
        ShaderRender.cpp
        ImageRender.cpp
        ElementRender.cpp
        Render.cpp
        Shader.cpp
        TransitionRender.cpp
        stb.cpp
)
target_link_libraries(${CMAKE_PROJECT_NAME}
        -lGLESv1_CM
        -lGLESv2
        -lGLESv3
        -lEGL
        log)
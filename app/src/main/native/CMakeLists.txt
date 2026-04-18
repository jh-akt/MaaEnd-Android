cmake_minimum_required(VERSION 3.22.1)

project(maaend-rootlauncher C)

add_library(rootlauncher SHARED launcher.c)

find_library(log-lib log)

target_link_libraries(rootlauncher ${log-lib})

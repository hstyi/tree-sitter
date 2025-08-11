#!/usr/bin/env kotlin

import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.system.exitProcess

val targets = listOf(
    ("x86_64-windows" to "dll"),
    ("aarch64-windows" to "dll"),
    ("aarch64-macos" to "dylib"),
    ("aarch64-linux-gnu" to "so"),
    ("x86_64-linux-gnu" to "so"),
    ("x86_64-macos" to "dylib"),
)
val buildDir = File("build")

if (buildDir.exists().not()) {
    if (buildDir.mkdirs().not()) {
        throw IOException("Failed to create directory ${buildDir.absolutePath}")
    }
}

fun build(name: String, includeDirs: List<String>, srcDirs: List<String>, cFiles: List<String> = mutableListOf()) {
    val projectDir = File(name)

    val pool = Executors.newCachedThreadPool()
    val futures = ArrayList<Future<*>>()

    for ((target, extension) in targets) {
        pool.submit {
            val commands = mutableListOf<String>()
            commands.add("zig")
            commands.add("c++")
            commands.add("-g0")
            commands.add("-fno-sanitize=undefined")
            commands.add("-shared")
            commands.add("-target")
            commands.add(target)
            commands.add("-I")
            commands.add(projectDir.path)

            for (includeDir in includeDirs) {
                commands.add("-I")
                commands.add(File(projectDir, includeDir).path)
            }

            for (srcDir in srcDirs) {
                commands.add("-I")
                commands.add(File(projectDir, srcDir).path)
            }

            val file = File(File(File(buildDir, name), target), "${name}.${extension}")
            if (file.parentFile.exists().not()) {
                if (file.parentFile.mkdirs().not()) {
                    throw IOException("Failed to create directory ${file.parentFile.absolutePath}")
                }
            }

            commands.add("-o")
            commands.add(file.path)

            for (c in cFiles) {
                commands.add(File(projectDir, c).path)
            }

            println(commands.joinToString(" "))
            val pb = ProcessBuilder(*commands.toTypedArray())
            pb.inheritIO()
            val exitCode = pb.start().waitFor()
            if (exitCode != 0) {
                exitProcess(exitCode)
            }
        }.apply { futures.add(this) }
    }

    futures.forEach { it.get() }

    pool.shutdown()
}


build("tree-sitter", listOf("lib/include"), listOf("lib/src"), listOf("lib/src/lib.c"))
build("tree-sitter-java", emptyList(), listOf("src"), listOf("src/parser.c"))
build("tree-sitter-markdown/tree-sitter-markdown", emptyList(), listOf("src"), listOf("src/parser.c", "src/scanner.c"))
build("tree-sitter-markdown/tree-sitter-markdown-inline", emptyList(), listOf("src"), listOf("src/parser.c", "src/scanner.c"))
build("tree-sitter-json", emptyList(), listOf("src"), listOf("src/parser.c"))

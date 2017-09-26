package com.mooregreatsoftware.aem.grapher

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption


fun Path.readText(): String =
    Files.newBufferedReader(this).
        use { it.lineSequence().reduce { acc, s -> StringBuilder(acc).append("\n").append(s).toString() } }

fun Path.mkdirs(): Path = Files.createDirectories(this)
fun Path.createFile(): Path {
    if (this.parent != null && !this.parent.exists()) this.parent.mkdirs()
    return Files.createFile(this)
}

fun Path.exists(): Boolean = Files.exists(this)
fun Path.writeText(text: String): Path {
    if (!exists()) createFile()
    Files.newBufferedWriter(this, StandardOpenOption.CREATE).use { writer -> writer.write(text) }
    return this
}

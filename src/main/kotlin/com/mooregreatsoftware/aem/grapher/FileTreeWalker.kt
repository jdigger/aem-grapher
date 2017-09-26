package com.mooregreatsoftware.aem.grapher

import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.jar.JarFile

class FileTreeWalker(val rootPath: Path) {

    init {
        val jar = JarFile("")
        jar.entries().iterator().forEach { entry ->
//            entry.
        }
    }

}

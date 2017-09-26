package com.mooregreatsoftware.aem.grapher

import org.redundent.kotlin.xml.xml
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Path

class JcrRootFixture(val jcrRoot: JcrRoot) {

    constructor(rootPath: Path) : this(JcrRoot.fromPath(rootPath))

    @Deprecated("Use ComponentNodeMarshaller")
    fun createComponentNodeFile(resourceType: String, title: String, classname: String, componentGroup: String = "Components"): Path {
        val output = xml("jcr:root") {
            namespace("jcr", NodeParser.NS_JCR_URI)
            namespace("cq", NodeParser.NS_CQ_URI)
            attributes(
                "className" to classname,
                "componentGroup" to componentGroup,
                "cq:isContainer" to "{Boolean}false",
                "cq:noDecoration" to "{Boolean}false",
                "jcr:primaryType" to "cq:Component",
                "jcr:title" to title)
        }.toString()

        return jcrRoot.path.resolve("apps/$resourceType/.content.xml").writeText(output)
    }

    fun createComponentClientlibNodeFile(resourceType: String,
                                         categories: Iterable<String> = listOf(resourceType.replace("/", ".")),
                                         dependencies: Iterable<String> = emptyList(),
                                         embed: Iterable<String> = emptyList()): Path {
        if (!categories.iterator().hasNext()) throw IllegalArgumentException("Need at least one category")
        val output = xml("jcr:root") {
            namespace("jcr", NodeParser.NS_JCR_URI)
            attribute("jcr:primaryType", "cq:ClientLibraryFolder")
            fun addListAttr(iter: Iterable<String>, attrName: String) {
                if (iter.iterator().hasNext())
                    attribute(attrName, iter.joinToString(",", "[", "]"))
            }
            addListAttr(categories, "categories")
            addListAttr(dependencies, "dependencies")
            addListAttr(embed, "embed")
        }.toString()

        return jcrRoot.path.resolve("apps/$resourceType/clientlibs/.content.xml").writeText(output)
    }

    fun createVendorClientlibNodeFile(libName: String,
                                      categories: Iterable<String> = listOf("myapp.$libName"),
                                      dependencies: Iterable<String> = emptyList(),
                                      embed: Iterable<String> = emptyList()): Path {
        if (!categories.iterator().hasNext()) throw IllegalArgumentException("Need at least one category")
        val output = xml("jcr:root") {
            namespace("jcr", NodeParser.NS_JCR_URI)
            attribute("jcr:primaryType", "cq:ClientLibraryFolder")
            fun addListAttr(iter: Iterable<String>, attrName: String) {
                if (iter.iterator().hasNext())
                    attribute(attrName, iter.joinToString(",", "[", "]"))
            }
            addListAttr(categories, "categories")
            addListAttr(dependencies, "dependencies")
            addListAttr(embed, "embed")
        }.toString()

        val path = "etc/clientlibs/myapp/vendor/$libName"

        return jcrRoot.path.resolve("$path/.content.xml").writeText(output)
    }


    companion object {
        fun createPackageFile(newZipFile: Path, block: JcrRootFixture.() -> Unit): Path {
            val zipFsUri = URI.create("jar:${newZipFile.toUri()}")
            FileSystems.newFileSystem(zipFsUri, mapOf("create" to "true")).use {
                val jcrRootPath = it.getPath("/jcr_root").mkdirs().toAbsolutePath()
                val jcrRootFixture = JcrRootFixture(jcrRootPath)
                block.invoke(jcrRootFixture)
            }
            return newZipFile
        }
    }

}

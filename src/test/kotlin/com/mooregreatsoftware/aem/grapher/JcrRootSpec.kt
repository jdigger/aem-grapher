package com.mooregreatsoftware.aem.grapher

import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import java.nio.file.Files

class JcrRootSpec : Spek({
    val filesystem = Jimfs.newFileSystem()
    val specPath = Files.createDirectories(filesystem.getPath("/"))

    given("fooble") {
        it("does stuff") {
            val zipPath = specPath.resolve("test_package.zip")
            JcrRootFixture.createPackageFile(zipPath) {
                createComponentNodeFile("myapp/aComp1", "A Comp 1", "com.foo.Comp1")
                createComponentNodeFile("myapp/aComp2", "A Comp 2", "com.foo.Comp2")
                createVendorClientlibNodeFile("jquery")
                createComponentClientlibNodeFile("myapp/aComp1", dependencies = listOf("myapp.jquery"))
                createComponentClientlibNodeFile("myapp/aComp2", embed = listOf("myapp.jquery"))
            }
            val jcrRoot = JcrRoot.fromPackageFile(zipPath)
            val items = jcrRoot.items()
            assertThat(items.toSet().size, equalTo(5))

            val dot = Grapher.toDot(jcrRoot.associations())
            println("DOT: $dot")
//
//            val graph = Factory.graph("example1").directed()
//
//            val g = graph.with(Factory.node("a").link(Factory.node("d")))
//            val serializer = Serializer(g as MutableGraph)
//            println("DOT: ${serializer.serialize()}")
        }
    }
})

package com.mooregreatsoftware.aem.grapher

import com.google.common.jimfs.Jimfs
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isEmpty
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import java.nio.file.Files
import java.util.*


class NodeParserSpec : Spek({
    val filesystem = Jimfs.newFileSystem()

    given("proj1") {
        val proj1Path = Files.createDirectories(filesystem.getPath("/proj1"))

        val jcrRoot = JcrRoot.fromPath(proj1Path.resolve("jcr_root").mkdirs())

        describe("reading .context files") {
            val jcrRootFixture = JcrRootFixture(jcrRoot)
            val componentNodeMarshaller = ComponentNodeMarshaller(jcrRoot)

            it("should parse a component definition") {
                val resourceType = "myapp/components/testComponent1"

                val nodePath = componentNodeMarshaller.serialize(ComponentNode(resourceType = resourceType, title = "Test Comp 1", className = "com.myco.TestComponent1"))

                val compNode = componentNodeMarshaller.deserialize(nodePath)
                assertThat(compNode, Objects::nonNull)
                compNode as ComponentNode
                assertThat(compNode.title, equalTo("Test Comp 1"))
                assertThat(compNode.resourceType, equalTo(resourceType))
                assertThat(compNode.className, equalTo("com.myco.TestComponent1"))
                assertThat(compNode.componentGroup, equalTo("Components"))
            }

            it("should parse a clientlib definition") {
                val nodePath = jcrRootFixture.createVendorClientlibNodeFile("jquery-cookie", dependencies = listOf("myapp.jquery"))

                val clibNode = NodeParser.createClientlibNode(nodePath)
                assertThat(clibNode, Objects::nonNull)
                clibNode as ClientlibNode
                assertThat(clibNode.path, equalTo("etc/clientlibs/myapp/vendor/jquery-cookie"))
                assertThat(clibNode.categories.toSet(), equalTo(setOf("myapp.jquery-cookie")))
                assertThat(clibNode.embed.toSet(), isEmpty)
                assertThat(clibNode.dependencies.toSet(), equalTo(setOf("myapp.jquery")))

//            val uri = URI.create("jar://foo")
//            val map = mapOf<String, Any>()
//            val fs = FileSystems.newFileSystem(uri, map)
//            println(fs)
            }
        }
    }

})

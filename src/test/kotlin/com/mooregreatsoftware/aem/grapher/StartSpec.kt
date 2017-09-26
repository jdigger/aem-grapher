package com.mooregreatsoftware.aem.grapher

import guru.nidi.graphviz.model.Factory.graph
import guru.nidi.graphviz.model.Factory.node
import guru.nidi.graphviz.model.MutableGraph
import guru.nidi.graphviz.model.Serializer
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it


class StartSpec : Spek({
    describe("Reading .context files") {
        it("should work for a component definition") {
            val g = graph("example1").directed().with(node("a").link(node("d")))
            val serializer = Serializer(g as MutableGraph)
            println("DOT: ${serializer.serialize()}")
//            val fromGraph = Graphviz.fromGraph(g)
//            fromGraph.engine(Engine.DOT).width(200)
//            val width = fromGraph.width(200)
//            width.render(Format.PNG).toFile(File(File("."), "ex1.png"))
        }
    }
})

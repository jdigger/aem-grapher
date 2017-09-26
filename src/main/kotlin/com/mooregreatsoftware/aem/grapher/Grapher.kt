package com.mooregreatsoftware.aem.grapher

import guru.nidi.graphviz.model.Factory
import guru.nidi.graphviz.model.MutableGraph
import guru.nidi.graphviz.model.Serializer

object Grapher {

    fun toDot(associations: Iterable<Association<*, *>>): String {
        var graph = Factory.graph("example1").directed()

        associations.forEach { association ->
            graph = graph.with(Factory.node(nodeName(association.left)).link(Factory.node(nodeName(association.right))))
        }
        val serializer = Serializer(graph as MutableGraph)
        return serializer.serialize()
    }

    private fun nodeName(node: Any): String {
        return when (node) {
            is ComponentNode -> node.resourceType
            is ClientlibNode -> node.path
            else -> throw IllegalArgumentException("Don't know how to do node name for $node")
        }
    }

}
package com.mooregreatsoftware.aem.grapher

import org.redundent.kotlin.xml.xml
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory

interface JcrNode<in T> : Comparable<T>

data class ComponentNode(val resourceType: String, val title: String, val componentGroup: String? = null, val className: String? = null) : JcrNode<ComponentNode> {
    override fun compareTo(other: ComponentNode) = this.resourceType.compareTo(other.resourceType)
}

data class ClientlibNode(val path: String, val categories: Iterable<String>, val dependencies: Iterable<String>, val embed: Iterable<String>) : JcrNode<ClientlibNode> {
    override fun compareTo(other: ClientlibNode) = this.path.compareTo(other.path)
}


interface JcrNodeDeserializer<in T> {
    fun deserialize(path: Path): JcrNode<T>?
}


interface JcrNodeSerializer<in T> {
    fun serialize(jcrNode: T): Path
}


class ComponentNodeMarshaller(private val jcrRoot: JcrRoot) : JcrNodeSerializer<ComponentNode>, JcrNodeDeserializer<ComponentNode> {

    private class ComponentNodeBuilder(private val nodePath: Path) : NodeParser.AbstractNodeBuilder(nodePath) {
        override fun isValid() = super.isValid() && title != null

        override val rootNS = NodeParser.NS_JCR_URI
        override val rootLocalName = "root"
        override val primaryType = "cq:Component"

        val title: String?
            get() {
                val title = rootElement.getAttributeNS(NodeParser.NS_JCR_URI, "title")
                return if (title != null && title.isNotBlank())
                    title
                else {
                    LOG.debug("There's no \"{{}}title\" attribute on the root element: {}", NodeParser.NS_JCR_URI, xml)
                    null
                }
            }

        val resourceType: String get() = resourceType(nodePath)
        val componentGroup: String? get() = rootElement.getAttribute("componentGroup")
        val className: String? get() = rootElement.getAttribute("className")
    }

    private fun isContentXmlFile(path: Path): Boolean {
        return if (!Files.isRegularFile(path)) false
        else path.fileName.toString() == ".content.xml"
    }

    fun createComponentNode(path: Path): ComponentNode? {
        if (!isContentXmlFile(path)) return null

        val nodePath = path.toAbsolutePath()

        val builder = ComponentNodeBuilder(nodePath)
        return if (builder.isValid())
            ComponentNode(resourceType = builder.resourceType, title = builder.title!!, componentGroup = builder.componentGroup, className = builder.className)
        else null
    }


    override fun deserialize(path: Path): ComponentNode? {
        return createComponentNode(path)
    }

    override fun serialize(jcrNode: ComponentNode): Path {
        val output = xml("jcr:root") {
            namespace("jcr", NodeParser.NS_JCR_URI)
            namespace("cq", NodeParser.NS_CQ_URI)
            attributes(
                "className" to (jcrNode.className ?: "com.something.ClassName"),
                "componentGroup" to (jcrNode.componentGroup ?: "Components"),
                "cq:isContainer" to "{Boolean}false",
                "cq:noDecoration" to "{Boolean}false",
                "jcr:primaryType" to "cq:Component",
                "jcr:title" to jcrNode.title)
        }.toString()

        return jcrRoot.path.resolve("apps").resolve(jcrNode.resourceType).resolve(".content.xml").writeText(output)
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ComponentNodeMarshaller::class.java)
    }
}


object NodeParser {
    const val NS_JCR_URI = "http://www.jcp.org/jcr/1.0"
    const val NS_CQ_URI = "http://www.day.com/jcr/cq/1.0"

    private val documentBuilder = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()

    abstract class AbstractNodeBuilder(private val nodePath: Path) {
        val xml get() = nodePath.readText()

        val rootElement: Element

        init {
            val xmlDoc: Document = documentBuilder.parse(xml.byteInputStream())
            rootElement = xmlDoc.documentElement
            rootElement.normalize()
        }

        open fun isValid(): Boolean = isRightRoot() && isRightPrimaryType()

        abstract val rootNS: String
        abstract val rootLocalName: String
        abstract val primaryType: String

        fun isRightRoot(): Boolean =
            when {
                rootElement.namespaceURI != rootNS -> {
                    LOG.debug("The root element namespace is not {}: {}", rootNS, xml)
                    false
                }
                rootElement.localName != rootLocalName -> {
                    LOG.debug("The root element is not \"{}\": {} - {}", rootLocalName, rootElement.localName, xml)
                    false
                }
                else -> true
            }

        fun isRightPrimaryType(): Boolean {
            val primaryType = rootElement.getAttributeNS(NS_JCR_URI, "primaryType")
            return when {
                primaryType == null || primaryType.isBlank() -> {
                    LOG.debug("The root element does not have \"primaryType\" set: {}", xml)
                    false
                }
                primaryType != this.primaryType -> {
                    LOG.debug("The \"primaryType\" is not \"{}\": {} - {}", this.primaryType, primaryType, xml)
                    false
                }
                else -> true
            }
        }

        protected fun resourceType(nodePath: Path): String {
            val jcrPath = jcrPath(nodePath)
            val topPath = jcrPath.getName(0).toString()
            return if (topPath == "apps" || topPath == "libs")
                jcrPath.subpath(1, jcrPath.nameCount - 1).toString()
            else throw IllegalArgumentException("$jcrPath does not start with \"apps\" or \"libs\"")
        }

        protected fun jcrPath(nodePath: Path): Path {
            val jcrRoot = findJcrRoot(nodePath)
            return jcrRoot.relativize(nodePath)
        }

        @Throws(IllegalArgumentException::class)
        protected fun findJcrRoot(path: Path): Path =
            recFindJcrRoot(path.toAbsolutePath()) ?:
                throw IllegalArgumentException("Could not find jcr_root in $path")

        private tailrec fun recFindJcrRoot(path: Path): Path? {
            val fileName = path.fileName.toString()
            if (fileName == "jcr_root") return path

            if (path.parent == null) return null

            return recFindJcrRoot(path.parent)
        }

        protected fun parseIterable(value: String?): Iterable<String> =
            if (value == null || value.isBlank())
                emptyList()
            else if (value.startsWith("[") && value.endsWith("]"))
                value.substring(1, value.length - 1).split(",")
            else
                listOf(value)

    }

    private class ClientlibNodeBuilder(private val nodePath: Path) : AbstractNodeBuilder(nodePath) {
        override fun isValid() = super.isValid() && categories.iterator().hasNext()

        override val rootNS = NS_JCR_URI
        override val rootLocalName = "root"
        override val primaryType = "cq:ClientLibraryFolder"

        val path: String
            get() {
                val jcrPath = jcrPath(nodePath)
                return jcrPath.subpath(0, jcrPath.nameCount - 1).toString()
            }
        val categories: Iterable<String> get() = parseIterable(rootElement.getAttribute("categories"))
        val embed: Iterable<String> get() = parseIterable(rootElement.getAttribute("embed"))
        val dependencies: Iterable<String> get() = parseIterable(rootElement.getAttribute("dependencies"))
    }

    private fun isContentXmlFile(path: Path): Boolean {
        return if (!Files.isRegularFile(path)) false
        else path.fileName.toString() == ".content.xml"
    }

    fun createClientlibNode(path: Path): ClientlibNode? {
        if (!isContentXmlFile(path)) return null

        val nodePath = path.toAbsolutePath()

        val builder = ClientlibNodeBuilder(nodePath)
        return if (builder.isValid())
            ClientlibNode(path = builder.path, categories = builder.categories, dependencies = builder.dependencies, embed = builder.embed)
        else null
    }

    private val LOG: Logger = LoggerFactory.getLogger(NodeParser::class.java)
}

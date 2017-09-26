package com.mooregreatsoftware.aem.grapher

import org.slf4j.LoggerFactory
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.reflect.KClass
import kotlin.streams.asSequence


enum class AssociationType(val leftClass: KClass<*>, val rightClass: KClass<*>) {
    CLIENTLIB_FOR_COMPONENT(ComponentNode::class, ClientlibNode::class),
    CLIENTLIB_SHARE_CATEGORY(ClientlibNode::class, ClientlibNode::class),
    CLIENTLIB_DEPENDENCY(ClientlibNode::class, ClientlibNode::class),
    CLIENTLIB_EMBED(ClientlibNode::class, ClientlibNode::class)
}

class Association<L : Comparable<L>, R : Comparable<R>> private constructor(val left: L, val right: R, val type: AssociationType, val data: String?) {

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun <L : Comparable<L>, R : Comparable<R>> create(left: L, right: R, type: AssociationType): Association<L, R> {
            return when (type) {
                AssociationType.CLIENTLIB_SHARE_CATEGORY -> {
                    left as ClientlibNode
                    right as ClientlibNode
                    val sharedCategory = sharedCategory(left.categories, right.categories) ?:
                        throw IllegalArgumentException("${left.categories} and ${right.categories} do not contain a match")
                    if (left.compareTo(right as L) < 1) Association<L, R>(left, right, type, sharedCategory)
                    else Association(right, left as R, type, sharedCategory)
                }
                AssociationType.CLIENTLIB_FOR_COMPONENT -> {
                    left as ComponentNode
                    right as ClientlibNode
                    Association(left, right, type, left.resourceType)
                }
                AssociationType.CLIENTLIB_DEPENDENCY -> {
                    left as ClientlibNode
                    right as ClientlibNode
                    Association(left, right, type, null)
                }
                AssociationType.CLIENTLIB_EMBED -> {
                    left as ClientlibNode
                    right as ClientlibNode
                    Association(left, right, type, null)
                }
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Association<*, *>

        if (left != other.left) return false
        if (right != other.right) return false
        if (type != other.type) return false
        if (data != other.data) return false

        return true
    }

    override fun hashCode(): Int {
        var result = left.hashCode()
        result = 31 * result + right.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + (data?.hashCode() ?: 0)
        return result
    }
}

class JcrRoot private constructor(val path: Path) {
    private val componentNodeMarshaller = ComponentNodeMarshaller(this)

    fun items(): Iterable<Any> {
        return Files.walk(path).asSequence().map { path ->
            componentNodeMarshaller.deserialize(path) ?: NodeParser.createClientlibNode(path)
        }.filterNotNull().asIterable()
    }

    fun associations(): Iterable<Association<*, *>> {
        val items = items().toSet()
        return items.flatMap { outer ->
            items.flatMap { inner ->
                if (outer != inner) {
                    when (outer) {
                        is ComponentNode -> outer.associationsWith(inner)
                        is ClientlibNode -> outer.associationsWith(inner)
                        else -> throw IllegalStateException("Don't know what to do with $outer")
                    }
                }
                else emptyList()
            }
        }
    }

    private fun ClientlibNode.associationsWith(other: Any): List<Association<*, *>> {
        return when (other) {
            is ComponentNode ->
                componentToClientlibAssociations(this, other)
            is ClientlibNode ->
                clientlibToClientlibAssociations(this, other)
            else -> throw IllegalStateException("Don't know what to do with $other")
        }
    }

    private fun ComponentNode.associationsWith(other: Any): List<Association<*, *>> {
        return when (other) {
            is ComponentNode ->
                componentToComponentAssociations(this, other)
            is ClientlibNode ->
                componentToClientlibAssociations(other, this)
            else -> throw IllegalStateException("Don't know what to do with $other")
        }
    }

    private fun componentToClientlibAssociations(clientlibNode: ClientlibNode, componentNode: ComponentNode): List<Association<*, ClientlibNode>> {
        val clientlibPath = path.resolve(clientlibNode.path)
        val clientlibSubpath = clientlibPath.subpath(2, clientlibPath.nameCount - 1).toString()
        return if (componentNode.resourceType == clientlibSubpath)
            listOf(Association.create(componentNode, clientlibNode, AssociationType.CLIENTLIB_FOR_COMPONENT))
        else {
            LOG.debug("{} is not associated with {} checking on {}", componentNode, clientlibNode, clientlibSubpath)
            emptyList()
        }
    }

    private fun componentToComponentAssociations(outer: ComponentNode, inner: ComponentNode): List<Association<*, *>> {
        if (outer.resourceType == inner.resourceType)
            throw IllegalStateException("$outer and $inner have the same resource type")
        else {
            LOG.debug("{} is not associated with {}", outer, inner)
            return emptyList()
        }
    }

    private fun clientlibToClientlibAssociations(outer: ClientlibNode, inner: ClientlibNode): List<Association<ClientlibNode, *>> {
        val associations = mutableListOf<Association<ClientlibNode, *>>()
        if (sharedCategory(outer.categories, inner.categories) != null) {
            associations.add(Association.create(outer, inner, AssociationType.CLIENTLIB_SHARE_CATEGORY))
        }
        if (outer.dependsOn(inner)) {
            associations.add(Association.create(outer, inner, AssociationType.CLIENTLIB_DEPENDENCY))
        }
        if (outer.embeds(inner)) {
            associations.add(Association.create(outer, inner, AssociationType.CLIENTLIB_EMBED))
        }
        return associations.toList()
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(JcrRoot::class.java)

        fun fromPackageFile(packageFilePath: Path): JcrRoot {
            if (isNotAZipFile(packageFilePath)) throw IllegalArgumentException("Must be a .zip file: $packageFilePath")
            // the "jar" schema is used for .zip files
            val zipFsUri = URI.create("jar:${packageFilePath.toUri()}")
            val filesystem = FileSystems.newFileSystem(zipFsUri, emptyMap<String, Any>())
            return fromPath(filesystem.getPath("/jcr_root"))
        }

        private fun isNotAZipFile(filePath: Path) = !filePath.fileName.toString().endsWith(".zip")

        fun fromPath(packagePath: Path): JcrRoot {
            return _fromPath(packagePath) ?: throw IllegalArgumentException("Could not find jcr_root for $packagePath")
        }

        private fun _fromPath(packagePath: Path?): JcrRoot? {
            if (packagePath == null) return null
            if (!packagePath.endsWith("jcr_root")) return _fromPath(packagePath.parent)
            return JcrRoot(packagePath)
        }
    }
}


private fun ClientlibNode.dependsOn(right: ClientlibNode): Boolean =
    right.categories.firstOrNull { category -> this.dependencies.contains(category) } != null

private fun ClientlibNode.embeds(right: ClientlibNode): Boolean =
    right.categories.firstOrNull { category -> this.embed.contains(category) } != null

private fun sharedCategory(outerCategories: Iterable<String>, innerCategories: Iterable<String>): String? =
    outerCategories.firstOrNull { outerCategory -> innerCategories.contains(outerCategory) }

package com.mooregreatsoftware.aem.grapher

import java.io.File

object Start {
    @JvmStatic
    fun main(args: Array<String>) {
        val testComponent1XmlUrl = Start.javaClass.classLoader.getResource("/testComponent1/.content.xml")
        val testComponent1File = File(testComponent1XmlUrl.file)

        println("testComponent1File.exists(): "+testComponent1File.exists())
    }
}

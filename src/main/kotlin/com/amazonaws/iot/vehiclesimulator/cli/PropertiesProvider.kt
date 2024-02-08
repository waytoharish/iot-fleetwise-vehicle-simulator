package com.amazonaws.iot.fleetwise.vehiclesimulator.cli

import picocli.CommandLine
import java.time.Instant
import java.util.Properties

class VersionProvider : CommandLine.IVersionProvider {
    override fun getVersion(): Array<String> {
        return arrayOf(
            "${PropertiesProvider.version} built at ${PropertiesProvider.buildTime}"
        )
    }
}

object PropertiesProvider {
    private val properties: Properties? by lazy {
        javaClass.getResourceAsStream("/version.properties")?.use {
            Properties().apply {
                load(it)
            }
        }
    }

    val version: String by lazy {
        properties?.getProperty("version") ?: "Undefined"
    }

    val buildTime: Instant? by lazy {
        properties?.getProperty("buildTime")?.toLongOrNull()?.let {
            Instant.ofEpochMilli(it)
        }
    }
}

package com.nowcent.mc.component.prometheus

import com.mojang.logging.LogUtils
import net.minecraft.server.MinecraftServer
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket

/**
 * @author orangeboyChen
 * @version 1.0
 * @date 2026/5/28 00:00
 */
object PrometheusMetricsServer {

    private val logger = LogUtils.getLogger()
    private var serverSocket: ServerSocket? = null
    private var serverThread: Thread? = null
    private var minecraftServer: MinecraftServer? = null
    @Volatile
    private var running = false

    fun start(mcServer: MinecraftServer, port: Int) {
        minecraftServer = mcServer
        try {
            serverSocket = ServerSocket(port)
            running = true
            serverThread = Thread({
                while (running) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        handleClient(client)
                    } catch (e: Exception) {
                        if (running) {
                            logger.debug("[PrometheusMetrics] Accept error: ${e.message}")
                        }
                    }
                }
            }, "PrometheusMetrics-Server").apply {
                isDaemon = true
                start()
            }
            logger.info("[PrometheusMetrics] Metrics server started on port $port")
        } catch (e: Exception) {
            logger.error("[PrometheusMetrics] Failed to start metrics server on port $port", e)
        }
    }

    fun stop() {
        running = false
        serverSocket?.close()
        serverSocket = null
        serverThread = null
        minecraftServer = null
        logger.info("[PrometheusMetrics] Metrics server stopped")
    }

    private fun handleClient(client: Socket) {
        Thread({
            client.use { socket ->
                try {
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                    val requestLine = reader.readLine() ?: return@use

                    // Consume remaining headers
                    while (reader.readLine()?.isNotEmpty() == true) { /* skip */ }

                    val output = socket.getOutputStream()

                    if (requestLine.startsWith("GET /metrics")) {
                        val body = buildString { appendLogisticsMetrics() }
                        val bodyBytes = body.toByteArray(Charsets.UTF_8)
                        val response = buildString {
                            append("HTTP/1.1 200 OK\r\n")
                            append("Content-Type: text/plain; version=0.0.4; charset=utf-8\r\n")
                            append("Content-Length: ${bodyBytes.size}\r\n")
                            append("Connection: close\r\n")
                            append("\r\n")
                        }
                        output.write(response.toByteArray(Charsets.UTF_8))
                        output.write(bodyBytes)
                    } else {
                        val response = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                        output.write(response.toByteArray(Charsets.UTF_8))
                    }
                    output.flush()
                } catch (e: Exception) {
                    logger.debug("[PrometheusMetrics] Error handling request: ${e.message}")
                }
            }
        }, "PrometheusMetrics-Handler").apply {
            isDaemon = true
            start()
        }
    }

    private fun StringBuilder.appendLogisticsMetrics() {
        try {
            val logisticsManagerClass = Class.forName(
                "com.simibubi.create.content.logistics.packagerLink.LogisticsManager"
            )

            val getSummaryMethod = logisticsManagerClass.getMethod(
                "getSummaryOfNetwork",
                java.util.UUID::class.java,
                Boolean::class.javaPrimitiveType
            )

            val networkIds = getActiveNetworkIds()
            if (networkIds.isEmpty()) {
                logger.debug("[PrometheusMetrics] No active logistics networks found")
                return
            }

            appendLine("# HELP minecraft_logistics_item_count Total count of items in a Create logistics network")
            appendLine("# TYPE minecraft_logistics_item_count gauge")

            for (networkId in networkIds) {
                try {
                    val summary = getSummaryMethod.invoke(null, networkId, false) ?: continue
                    val summaryClass = summary.javaClass

                    // Use getStacksByCount() for sorted results
                    val getStacksMethod = summaryClass.getMethod("getStacksByCount")
                    val stacks = getStacksMethod.invoke(summary) as? List<*> ?: continue

                    for (bigItemStack in stacks) {
                        if (bigItemStack == null) continue
                        val bigItemStackClass = bigItemStack.javaClass

                        val stackField = bigItemStackClass.getField("stack")
                        val countField = bigItemStackClass.getField("count")

                        val itemStack = stackField.get(bigItemStack)
                        val count = countField.getInt(bigItemStack)

                        val itemName = getItemRegistryName(itemStack) ?: continue

                        appendLine("minecraft_logistics_item_count{network=\"$networkId\",item=\"$itemName\"} $count")
                    }
                } catch (e: Exception) {
                    logger.warn("[PrometheusMetrics] Error processing network $networkId: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
        } catch (e: ClassNotFoundException) {
            logger.warn("[PrometheusMetrics] Create mod not found, skipping logistics metrics")
        } catch (e: Exception) {
            logger.warn("[PrometheusMetrics] Error collecting logistics metrics: ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getActiveNetworkIds(): Set<java.util.UUID> {
        return try {
            // Access Create.LOGISTICS.logisticsNetworks (public field)
            val createClass = Class.forName("com.simibubi.create.Create")
            val logisticsField = createClass.getField("LOGISTICS")
            val globalLogisticsManager = logisticsField.get(null)
                ?: run {
                    logger.warn("[PrometheusMetrics] LOGISTICS field is null")
                    return emptySet()
                }

            val networksField = globalLogisticsManager.javaClass.getField("logisticsNetworks")
            val networks = networksField.get(globalLogisticsManager) as? Map<java.util.UUID, *>
            if (networks == null) {
                logger.warn("[PrometheusMetrics] logisticsNetworks field is null")
                return emptySet()
            }
            logger.debug("[PrometheusMetrics] Found ${networks.size} logistics networks")
            networks.keys
        } catch (e: Exception) {
            logger.warn("[PrometheusMetrics] Error getting network IDs: ${e.javaClass.simpleName}: ${e.message}")
            emptySet()
        }
    }

    private fun getItemRegistryName(itemStack: Any): String? {
        return try {
            val getItemMethod = itemStack.javaClass.getMethod("getItem")
            val item = getItemMethod.invoke(itemStack)

            val builtInRegistriesClass = Class.forName("net.minecraft.core.registries.BuiltInRegistries")
            val itemRegistryField = builtInRegistriesClass.getField("ITEM")
            val itemRegistry = itemRegistryField.get(null)

            val getKeyMethod = itemRegistry.javaClass.getMethod("getKey", Any::class.java)
            val resourceLocation = getKeyMethod.invoke(itemRegistry, item)
            resourceLocation?.toString()
        } catch (e: Exception) {
            null
        }
    }
}

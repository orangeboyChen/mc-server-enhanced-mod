package com.nowcent.mc.component.prometheus

import com.mojang.logging.LogUtils
import com.nowcent.mc.Config
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent

/**
 * @author orangeboyChen
 * @version 1.0
 * @date 2026/5/28 00:00
 */
object PrometheusManager {

    private val logger = LogUtils.getLogger()

    @SubscribeEvent
    fun onServerStarted(event: ServerStartedEvent) {
        val port = Config.prometheusPort
        if (port <= 0) {
            logger.info("[PrometheusMetrics] Metrics server disabled (port <= 0)")
            return
        }
        PrometheusMetricsServer.start(event.server, port)
    }

    @SubscribeEvent
    fun onServerStopping(event: ServerStoppingEvent) {
        PrometheusMetricsServer.stop()
    }
}

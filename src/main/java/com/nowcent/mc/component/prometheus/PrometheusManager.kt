package com.nowcent.mc.component.prometheus

import com.mojang.logging.LogUtils
import com.nowcent.mc.Config
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent

/**
 * @author orangeboyChen
 * @version 1.0
 * @date 2026/5/28 00:00
 */
object PrometheusManager {

    private val logger = LogUtils.getLogger()
    private var tickCounter = 0
    private var enabled = false
    private const val REFRESH_INTERVAL_TICKS = 600 // 30 seconds

    @SubscribeEvent
    fun onServerStarted(event: ServerStartedEvent) {
        val port = Config.prometheusPort
        if (port <= 0) {
            logger.info("[PrometheusMetrics] Metrics server disabled (port <= 0)")
            return
        }
        enabled = true
        PrometheusMetricsServer.start(event.server, port)
        LogisticsChunkLoader.init(event.server)
    }

    @SubscribeEvent
    fun onServerTick(event: ServerTickEvent.Post) {
        if (!enabled) return
        tickCounter++
        if (tickCounter >= REFRESH_INTERVAL_TICKS) {
            tickCounter = 0
            LogisticsChunkLoader.refresh()
        }
    }

    @SubscribeEvent
    fun onServerStopping(event: ServerStoppingEvent) {
        enabled = false
        LogisticsChunkLoader.cleanup()
        PrometheusMetricsServer.stop()
    }
}

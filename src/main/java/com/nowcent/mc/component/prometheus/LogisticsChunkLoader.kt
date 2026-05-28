package com.nowcent.mc.component.prometheus

import com.mojang.logging.LogUtils
import net.minecraft.core.GlobalPos
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import java.util.UUID

/**
 * Automatically force-loads chunks containing Create logistics network links,
 * so Prometheus can always collect inventory data even when no players are online.
 *
 * @author orangeboyChen
 * @version 1.0
 * @date 2026/5/28 00:00
 */
object LogisticsChunkLoader {

    private val logger = LogUtils.getLogger()
    private val forceLoadedChunks = mutableSetOf<Pair<ResourceKey<Level>, ChunkPos>>()
    private var server: MinecraftServer? = null

    fun init(mcServer: MinecraftServer) {
        server = mcServer
        refresh()
    }

    fun cleanup() {
        unloadAll()
        server = null
    }

    /**
     * Refresh force-loaded chunks based on current logistics network state.
     * Call periodically or after network changes.
     */
    fun refresh() {
        val mcServer = server ?: return
        try {
            val positions = getLogisticsLinkPositions()
            if (positions.isEmpty()) {
                logger.debug("[LogisticsChunkLoader] No logistics link positions found")
                unloadAll()
                return
            }

            // Compute new set of chunks needed
            val newChunks = mutableSetOf<Pair<ResourceKey<Level>, ChunkPos>>()
            for (globalPos in positions) {
                val dimension = globalPos.dimension
                val chunkPos = ChunkPos(globalPos.pos)
                newChunks.add(dimension to chunkPos)
            }

            // Remove chunks no longer needed
            val toRemove = forceLoadedChunks - newChunks
            for ((dimension, chunkPos) in toRemove) {
                val level = mcServer.getLevel(dimension) ?: continue
                level.setChunkForced(chunkPos.x, chunkPos.z, false)
            }

            // Add new chunks
            val toAdd = newChunks - forceLoadedChunks
            for ((dimension, chunkPos) in toAdd) {
                val level = mcServer.getLevel(dimension) ?: continue
                level.setChunkForced(chunkPos.x, chunkPos.z, true)
            }

            if (toAdd.isNotEmpty() || toRemove.isNotEmpty()) {
                logger.info("[LogisticsChunkLoader] Force-loaded ${newChunks.size} chunks (added ${toAdd.size}, removed ${toRemove.size})")
            }

            forceLoadedChunks.clear()
            forceLoadedChunks.addAll(newChunks)
        } catch (e: Exception) {
            logger.warn("[LogisticsChunkLoader] Error refreshing force-loaded chunks: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun unloadAll() {
        val mcServer = server ?: return
        for ((dimension, chunkPos) in forceLoadedChunks) {
            try {
                val level = mcServer.getLevel(dimension) ?: continue
                level.setChunkForced(chunkPos.x, chunkPos.z, false)
            } catch (_: Exception) {}
        }
        if (forceLoadedChunks.isNotEmpty()) {
            logger.info("[LogisticsChunkLoader] Unloaded ${forceLoadedChunks.size} force-loaded chunks")
        }
        forceLoadedChunks.clear()
    }

    /**
     * Get all link positions from Create's GlobalLogisticsManager via reflection.
     * LogisticsNetwork.totalLinks contains GlobalPos for ALL links (even in unloaded chunks).
     */
    @Suppress("UNCHECKED_CAST")
    private fun getLogisticsLinkPositions(): Set<GlobalPos> {
        val createClass = Class.forName("com.simibubi.create.Create")
        val logisticsField = createClass.getField("LOGISTICS")
        val globalLogisticsManager = logisticsField.get(null) ?: return emptySet()

        val networksField = globalLogisticsManager.javaClass.getField("logisticsNetworks")
        val networks = networksField.get(globalLogisticsManager) as? Map<UUID, *> ?: return emptySet()

        val allPositions = mutableSetOf<GlobalPos>()
        for ((_, network) in networks) {
            if (network == null) continue
            val totalLinksField = network.javaClass.getField("totalLinks")
            val totalLinks = totalLinksField.get(network) as? Set<*> ?: continue
            for (pos in totalLinks) {
                if (pos is GlobalPos) {
                    allPositions.add(pos)
                }
            }
        }
        return allPositions
    }
}

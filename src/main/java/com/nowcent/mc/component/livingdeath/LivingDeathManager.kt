package com.nowcent.mc.component.livingdeath

import com.mojang.logging.LogUtils
import com.nowcent.mc.Config
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.minecraft.world.entity.player.Player
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * @author orangeboyChen
 * @version 1.0
 * @date 2026/1/5 01:27
 */
object LivingDeathManager {

    private val httpClient = HttpClient.newBuilder().build()
    private val logger = LogUtils.getLogger()

    @SubscribeEvent
    fun onLivingDeath(event: LivingDeathEvent) {
        val player = event.entity as? Player ?: return
        val message = event.source.getLocalizedDeathMessage(player)
        beginNotification(message.string)
    }

    private fun beginNotification(message: String) {
        val json = RequestBody(message = message)
        val config = Config.livingDeath
        if (config.url.isEmpty()) {
            return
        }

        val request = HttpRequest.newBuilder()
            .uri(URI.create(config.url))
            .POST(HttpRequest.BodyPublishers.ofString(Json.encodeToString(json)))
            .header("Content-Type", "application/json; charset=utf-8")
            .header("Authorization", config.auth)
            .version(HttpClient.Version.HTTP_1_1)
            .build()
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenAcceptAsync {
                if (it.statusCode() != 200) {
                    logger.info("[LivingDeathManager] beginNotification - error! ${it.statusCode()} ${it.body()}")
                    return@thenAcceptAsync
                }
                logger.info("[LivingDeathManager] beginNotification - finished! ${it.body()}")
            }
            .exceptionallyAsync { e ->
                logger.error("[LivingDeathManager] beginNotification - error!", e)
                null
            }
    }
}

@Serializable
private data class RequestBody(
    val message: String,
)

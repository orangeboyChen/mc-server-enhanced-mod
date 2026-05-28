package com.nowcent.mc

import net.neoforged.neoforge.common.ModConfigSpec

/**
 * @author orangeboyChen
 * @version 1.0
 * @date 2026/1/5 01:30
 */
object Config {
    private lateinit var livingDeathURL: ModConfigSpec.ConfigValue<String>
    private lateinit var livingDeathAuth: ModConfigSpec.ConfigValue<String>
    private lateinit var prometheusPortConfig: ModConfigSpec.IntValue

    val livingDeath: LivingDeathConfig
        get() = LivingDeathConfig(url = livingDeathURL.get(), auth = livingDeathAuth.get())

    val prometheusPort: Int
        get() = prometheusPortConfig.get()

    val spec: ModConfigSpec

    init {
        val builder = ModConfigSpec.Builder()
        builder.into("living-death-notification") {
            comment("Living Death Notification URL")
            livingDeathURL = define("url", "")

            comment("Living Death Notification Auth")
            livingDeathAuth = define("auth", "")
        }
        builder.into("prometheus") {
            comment("Prometheus metrics HTTP server port. Set to 0 to disable.")
            prometheusPortConfig = defineInRange("port", 9225, 0, 65535)
        }
        spec = builder.build()
    }

    private inline fun ModConfigSpec.Builder.into(
        path: String,
        action: ModConfigSpec.Builder.() -> Unit
    ) {
        push(path)
        action()
        pop()
    }
}

data class LivingDeathConfig(
    val url: String,
    val auth: String,
)

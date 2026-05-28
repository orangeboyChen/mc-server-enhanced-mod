package com.nowcent.mc.component.locationcache.logic

import com.mojang.logging.LogUtils
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.saveddata.SavedData

/**
 * @author orangeboyChen
 * @version 1.0
 * @date 2026/1/2 22:41
 */
object LocationCacheManager {

    private val logger = LogUtils.getLogger()
    private var server: MinecraftServer? = null

    fun init(server: MinecraftServer) {
        this.server = server
    }

    fun cleanup() {
        this.server = null
    }

    private fun getData(): LocationSavedData {
        val overworld = server!!.overworld()
        return overworld.dataStorage.computeIfAbsent(
            LocationSavedData.factory(),
            LocationSavedData.DATA_NAME
        )
    }

    fun putPublicLocation(key: String, location: BlockPos) {
        val data = getData()
        data.publicLocations[key] = location
        data.setDirty()
    }

    fun putPrivateLocation(user: String, key: String, location: BlockPos) {
        val data = getData()
        data.privateLocations.getOrPut(user) { mutableMapOf() }[key] = location
        data.setDirty()
    }

    fun getLocation(user: String, key: String): BlockPos? {
        val data = getData()
        return data.privateLocations[user]?.get(key) ?: data.publicLocations[key]
    }

    fun removePrivateLocation(user: String, key: String): BlockPos? {
        val data = getData()
        val pos = data.privateLocations[user]?.remove(key)
        if (pos != null) data.setDirty()
        return pos
    }

    fun removePublicLocation(key: String): BlockPos? {
        val data = getData()
        val pos = data.publicLocations.remove(key)
        if (pos != null) data.setDirty()
        return pos
    }
}

class LocationSavedData(
    val publicLocations: MutableMap<String, BlockPos> = mutableMapOf(),
    val privateLocations: MutableMap<String, MutableMap<String, BlockPos>> = mutableMapOf()
) : SavedData() {

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        // Save public locations
        val publicTag = CompoundTag()
        publicLocations.forEach { (key, pos) ->
            val posTag = CompoundTag()
            posTag.putInt("x", pos.x)
            posTag.putInt("y", pos.y)
            posTag.putInt("z", pos.z)
            publicTag.put(key, posTag)
        }
        tag.put("public", publicTag)

        // Save private locations
        val privateTag = CompoundTag()
        privateLocations.forEach { (user, locations) ->
            val userTag = CompoundTag()
            locations.forEach { (key, pos) ->
                val posTag = CompoundTag()
                posTag.putInt("x", pos.x)
                posTag.putInt("y", pos.y)
                posTag.putInt("z", pos.z)
                userTag.put(key, posTag)
            }
            privateTag.put(user, userTag)
        }
        tag.put("private", privateTag)

        return tag
    }

    companion object {
        const val DATA_NAME = "serverenhancedmod_locations"

        fun factory(): Factory<LocationSavedData> {
            return Factory(
                { LocationSavedData() },
                { tag, _ -> load(tag) },
                null
            )
        }

        private fun load(tag: CompoundTag): LocationSavedData {
            val publicLocations = mutableMapOf<String, BlockPos>()
            val privateLocations = mutableMapOf<String, MutableMap<String, BlockPos>>()

            // Load public locations
            if (tag.contains("public")) {
                val publicTag = tag.getCompound("public")
                for (key in publicTag.allKeys) {
                    val posTag = publicTag.getCompound(key)
                    publicLocations[key] = BlockPos(
                        posTag.getInt("x"),
                        posTag.getInt("y"),
                        posTag.getInt("z")
                    )
                }
            }

            // Load private locations
            if (tag.contains("private")) {
                val privateTag = tag.getCompound("private")
                for (user in privateTag.allKeys) {
                    val userTag = privateTag.getCompound(user)
                    val userLocations = mutableMapOf<String, BlockPos>()
                    for (key in userTag.allKeys) {
                        val posTag = userTag.getCompound(key)
                        userLocations[key] = BlockPos(
                            posTag.getInt("x"),
                            posTag.getInt("y"),
                            posTag.getInt("z")
                        )
                    }
                    privateLocations[user] = userLocations
                }
            }

            return LocationSavedData(publicLocations, privateLocations)
        }
    }
}

package xyz.bluspring.replaymaprecovery.client

import com.mojang.blaze3d.platform.InputConstants
import com.mojang.serialization.DataResult
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.*
import net.minecraft.util.datafix.DataFixers
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.chunk.ChunkStatus
import net.minecraft.world.level.chunk.ProtoChunk
import net.minecraft.world.level.chunk.storage.ChunkSerializer
import net.minecraft.world.level.chunk.storage.ChunkStorage
import net.minecraft.world.level.entity.ChunkEntities
import net.minecraft.world.level.levelgen.BelowZeroRetrogen
import net.minecraft.world.level.levelgen.GenerationStep.Carving
import net.minecraft.world.level.levelgen.blending.BlendingData
import net.minecraft.world.level.lighting.LevelLightEngine
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import xyz.bluspring.replaymaprecovery.ClientEntityStorage
import xyz.bluspring.replaymaprecovery.mixin.*
import java.io.File
import java.util.*

class ReplayMapRecoveryClient : ClientModInitializer {
    private val logger = LoggerFactory.getLogger(ReplayMapRecoveryClient::class.java)

    override fun onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register {
            if (DUMP_REGIONS.consumeClick()) {
                val level = Minecraft.getInstance().level ?: return@register
                println(level)

                run { // chunks
                    val file = File(FabricLoader.getInstance().gameDir.toFile(), "stored_regions")
                    val chunkStorage = ChunkStorage(file.toPath(), DataFixers.getDataFixer(), true)

                    if (!file.exists())
                        file.mkdirs()

                    val storage = (level.chunkSource as ClientChunkCacheAccessor).storage
                    val chunks = (storage as StorageAccessor).chunks

                    for (i in 0 until chunks.length()) {
                        val chunk = chunks.get(i) ?: continue

                        chunkStorage.write(chunk.pos, writeChunk(level, chunk))
                    }

                    chunkStorage.close()
                }

                run { // entities
                    val file = File(FabricLoader.getInstance().gameDir.toFile(), "stored_entities")

                    if (!file.exists())
                        file.mkdirs()

                    val storage = ClientEntityStorage(level, file.toPath(), DataFixers.getDataFixer(), true, Minecraft.getInstance())
                    val entities = (level as ClientLevelAccessor).entityStorage

                    val entityMap = mutableMapOf<ChunkPos, MutableList<Entity>>()

                    (entities as TransientEntitySectionManagerAccessor<Entity>).entityStorage.allEntities.forEach { entity ->
                        entityMap.computeIfAbsent(entity.chunkPosition()) { mutableListOf() }
                            .add(entity)
                    }

                    for ((chunkPos, entityList) in entityMap) {
                        storage.storeEntities(ChunkEntities(chunkPos, entityList))
                    }

                    storage.flush(true)
                    storage.close()
                }

                run { // map data
                    val file = File(FabricLoader.getInstance().gameDir.toFile(), "stored_data")

                    if (!file.exists())
                        file.mkdirs()

                    val mapDatas = (level as ClientLevelAccessor).callGetAllMapData()

                    for ((name, mapData) in mapDatas) {
                        val mapFile = File(file, "$name.dat")

                        val tag = CompoundTag()
                        mapData.save(tag)

                        if (!mapFile.exists())
                            mapFile.createNewFile()

                        NbtIo.writeCompressed(tag, mapFile)
                    }
                }
            }
        }
    }

    fun writeChunk(level: ClientLevel, chunk: ChunkAccess): CompoundTag {
        val chunkPos = chunk.pos
        val compoundTag = NbtUtils.addCurrentDataVersion(CompoundTag())
        compoundTag.putInt("xPos", chunkPos.x)
        compoundTag.putInt("yPos", chunk.minSection)
        compoundTag.putInt("zPos", chunkPos.z)
        compoundTag.putLong("LastUpdate", level.gameTime)
        compoundTag.putLong("InhabitedTime", chunk.inhabitedTime)
        compoundTag.putString("Status", BuiltInRegistries.CHUNK_STATUS.getKey(chunk.status).toString())
        val blendingData = chunk.blendingData
        var var10000: DataResult<*>
        var var10001: Logger
        if (blendingData != null) {
            var10000 = BlendingData.CODEC.encodeStart(NbtOps.INSTANCE, blendingData)
            var10001 = logger
            Objects.requireNonNull(var10001)
            var10000.resultOrPartial(var10001::error).ifPresent { tag ->
                compoundTag.put("blending_data", tag)
            }
        }

        val belowZeroRetrogen = chunk.belowZeroRetrogen
        if (belowZeroRetrogen != null) {
            var10000 = BelowZeroRetrogen.CODEC.encodeStart(NbtOps.INSTANCE, belowZeroRetrogen)
            var10001 = logger
            Objects.requireNonNull(var10001)
            var10000.resultOrPartial(var10001::error).ifPresent { tag ->
                compoundTag.put("below_zero_retrogen", tag)
            }
        }

        val upgradeData = chunk.upgradeData
        if (!upgradeData.isEmpty) {
            compoundTag.put("UpgradeData", upgradeData.write())
        }

        val levelChunkSections = chunk.sections
        val listTag = ListTag()
        val levelLightEngine: LevelLightEngine = level.chunkSource.lightEngine
        val registry = level.registryAccess().registryOrThrow(Registries.BIOME)
        val codec = ChunkSerializerAccessor.callMakeBiomeCodec(registry)
        val bl = chunk.isLightCorrect

        for (i in levelLightEngine.minLightSection until levelLightEngine.maxLightSection) {
            val j = chunk.getSectionIndexFromSectionY(i)
            val bl2 = j >= 0 && j < levelChunkSections.size
            val dataLayer =
                levelLightEngine.getLayerListener(LightLayer.BLOCK).getDataLayerData(SectionPos.of(chunkPos, i))
            val dataLayer2 =
                levelLightEngine.getLayerListener(LightLayer.SKY).getDataLayerData(SectionPos.of(chunkPos, i))
            if (bl2 || dataLayer != null || dataLayer2 != null) {
                val compoundTag2 = CompoundTag()
                if (bl2) {
                    val levelChunkSection = levelChunkSections[j]
                    var var10002: DataResult<*> =
                        ChunkSerializerAccessor.getBLOCK_STATE_CODEC().encodeStart(NbtOps.INSTANCE, levelChunkSection.states)
                    var var10004 = logger
                    Objects.requireNonNull(var10004)
                    compoundTag2.put("block_states", var10002.getOrThrow(false, var10004::error) as Tag)
                    var10002 = codec.encodeStart(NbtOps.INSTANCE, levelChunkSection.biomes)
                    var10004 = logger
                    Objects.requireNonNull(var10004)
                    compoundTag2.put("biomes", var10002.getOrThrow(false, var10004::error))
                }

                if (dataLayer != null && !dataLayer.isEmpty) {
                    compoundTag2.putByteArray("BlockLight", dataLayer.data)
                }

                if (dataLayer2 != null && !dataLayer2.isEmpty) {
                    compoundTag2.putByteArray("SkyLight", dataLayer2.data)
                }

                if (!compoundTag2.isEmpty) {
                    compoundTag2.putByte("Y", i.toByte())
                    listTag.add(compoundTag2)
                }
            }
        }

        compoundTag.put("sections", listTag)
        if (bl) {
            compoundTag.putBoolean("isLightOn", true)
        }

        val listTag2 = ListTag()
        val var23: Iterator<*> = chunk.blockEntitiesPos.iterator()

        var compoundTag3: CompoundTag?
        while (var23.hasNext()) {
            val blockPos = var23.next() as BlockPos
            compoundTag3 = chunk.getBlockEntityNbtForSaving(blockPos)
            if (compoundTag3 != null) {
                listTag2.add(compoundTag3)
            }
        }

        compoundTag.put("block_entities", listTag2)
        if (chunk.status.chunkType == ChunkStatus.ChunkType.PROTOCHUNK) {
            val protoChunk = chunk as ProtoChunk
            val listTag3 = ListTag()
            listTag3.addAll(protoChunk.entities)
            compoundTag.put("entities", listTag3)
            compoundTag3 = CompoundTag()
            val var31 = Carving.entries.toTypedArray()
            val var32 = var31.size

            for (var33 in 0 until var32) {
                val carving = var31[var33]
                val carvingMask = protoChunk.getCarvingMask(carving)
                if (carvingMask != null) {
                    compoundTag3.putLongArray(carving.toString(), carvingMask.toArray())
                }
            }

            compoundTag.put("CarvingMasks", compoundTag3)
        }

        compoundTag.put("PostProcessing", ChunkSerializer.packOffsets(chunk.postProcessing))
        val compoundTag4 = CompoundTag()
        val var28 = chunk.heightmaps.iterator()

        while (var28.hasNext()) {
            val entry = var28.next()
            if (chunk.status.heightmapsAfter().contains(entry.key)) {
                compoundTag4.put(entry.key.serializationKey, LongArrayTag(entry.value.rawData))
            }
        }

        compoundTag.put("Heightmaps", compoundTag4)

        return compoundTag
    }

    companion object {
        val DUMP_REGIONS = KeyBindingHelper.registerKeyBinding(KeyMapping("replaymaprecovery.dump_regions", InputConstants.KEY_F7,  "Replay Map Recovery"))
    }
}

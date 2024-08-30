package xyz.bluspring.replaymaprecovery

import com.google.common.collect.ImmutableList
import com.mojang.datafixers.DataFixer
import com.mojang.logging.LogUtils
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import it.unimi.dsi.fastutil.longs.LongSet
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.IntArrayTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtUtils
import net.minecraft.util.datafix.DataFixTypes
import net.minecraft.util.thread.ProcessorMailbox
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.entity.ChunkEntities
import net.minecraft.world.level.entity.EntityPersistentStorage
import org.slf4j.Logger
import xyz.bluspring.replaymaprecovery.mixin.IOWorkerAccessor
import java.io.IOException
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.function.Function


class ClientEntityStorage(
    private val level: ClientLevel,
    folder: Path?,
    protected val fixerUpper: DataFixer,
    sync: Boolean,
    mainThreadExecutor: Executor
) : EntityPersistentStorage<Entity> {
    private val worker = IOWorkerAccessor.createIOWorker(folder, sync, "entities")
    private val emptyChunks: LongSet = LongOpenHashSet()
    private val entityDeserializerQueue = ProcessorMailbox.create(mainThreadExecutor, "entity-deserializer")

    override fun loadEntities(pos: ChunkPos): CompletableFuture<ChunkEntities<Entity>> {
        if (emptyChunks.contains(pos.toLong())) {
            return CompletableFuture.completedFuture(emptyChunk(pos))
        } else {
            val var10000 = worker.loadAsync(pos)
            val var10002 = this.entityDeserializerQueue
            Objects.requireNonNull(var10002)
            return var10000.thenApplyAsync(Function { optional ->
                if (optional.isEmpty()) {
                    emptyChunks.add(pos.toLong())
                    return@Function emptyChunk(pos)
                } else {
                    try {
                        val chunkPos2 =
                            readChunkPos(optional.get() as CompoundTag)
                        if (pos != chunkPos2) {
                            LOGGER.error(
                                "Chunk file at {} is in the wrong location. (Expected {}, got {})",
                                *arrayOf<Any>(pos, pos, chunkPos2)
                            )
                        }
                    } catch (var6: Exception) {
                        val exception = var6
                        LOGGER.warn("Failed to parse chunk {} position info", pos, exception)
                    }

                    val compoundTag =
                        upgradeChunkTag(optional.get() as CompoundTag)
                    val listTag = compoundTag.getList("Entities", 10)
                    val list: List<Entity?> =
                        EntityType.loadEntitiesRecursive(listTag, this.level)
                            .collect(
                                ImmutableList.toImmutableList()
                            )
                    return@Function ChunkEntities(pos, list)
                }
            }, Executor { task ->
                var10002.tell(
                    task
                )
            })
        }
    }

    override fun storeEntities(entities: ChunkEntities<Entity>) {
        val chunkPos = entities.pos
        if (entities.isEmpty) {
            if (emptyChunks.add(chunkPos.toLong())) {
                worker.store(chunkPos, null as CompoundTag?)
            }
        } else {
            val listTag = ListTag()
            entities.entities.forEach { entity: Entity? ->
                val compoundTag = CompoundTag()
                if (entity!!.save(compoundTag)) {
                    listTag.add(compoundTag)
                }
            }
            val compoundTag = NbtUtils.addCurrentDataVersion(CompoundTag())
            compoundTag.put("Entities", listTag)
            writeChunkPos(compoundTag, chunkPos)
            worker.store(chunkPos, compoundTag).exceptionally { throwable: Throwable? ->
                LOGGER.error("Failed to store chunk {}", chunkPos, throwable)
                null
            }
            emptyChunks.remove(chunkPos.toLong())
        }
    }

    override fun flush(synchronize: Boolean) {
        worker.synchronize(synchronize).join()
        entityDeserializerQueue.runAll()
    }

    private fun upgradeChunkTag(tag: CompoundTag): CompoundTag {
        val i = NbtUtils.getDataVersion(tag, -1)
        return DataFixTypes.ENTITY_CHUNK.updateToCurrentVersion(this.fixerUpper, tag, i)
    }

    @Throws(IOException::class)
    override fun close() {
        worker.close()
    }

    companion object {
        private val LOGGER: Logger = LogUtils.getLogger()
        private const val ENTITIES_TAG = "Entities"
        private const val POSITION_TAG = "Position"
        private fun readChunkPos(tag: CompoundTag): ChunkPos {
            val `is` = tag.getIntArray("Position")
            return ChunkPos(`is`[0], `is`[1])
        }

        private fun writeChunkPos(tag: CompoundTag, pos: ChunkPos) {
            tag.put("Position", IntArrayTag(intArrayOf(pos.x, pos.z)))
        }

        private fun emptyChunk(pos: ChunkPos): ChunkEntities<Entity> {
            return ChunkEntities(pos, ImmutableList.of())
        }
    }
}

package me.senseiwells.multiverse

import me.senseiwells.multiverse.commands.MultiverseCommand
import me.senseiwells.multiverse.level.TickManagedCustomLevelFactory
import me.senseiwells.multiverse.utils.MultiverseRegistries
import me.senseiwells.multiverse.utils.multiverse
import net.casual.arcade.commands.register
import net.casual.arcade.dimensions.utils.DimensionRegistries
import net.casual.arcade.events.GlobalEventHandler
import net.casual.arcade.events.ListenerRegistry.Companion.register
import net.casual.arcade.events.server.ServerRegisterCommandEvent
import net.casual.arcade.events.server.registry.RegistryEventHandler
import net.casual.arcade.events.server.registry.RegistryLoadedFromResourcesEvent
import net.casual.arcade.utils.serialization.codec.CodecProvider.Companion.register
import net.casual.arcade.utils.toKey
import net.fabricmc.api.ModInitializer
import net.minecraft.core.Holder
import net.minecraft.core.HolderLookup
import net.minecraft.core.Registry
import net.minecraft.core.registries.Registries
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.dimension.BuiltinDimensionTypes
import net.minecraft.world.level.dimension.LevelStem
import net.minecraft.world.level.levelgen.FlatLevelSource
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings
import org.slf4j.LoggerFactory
import java.util.*
import net.casual.arcade.dimensions.level.CustomLevel
import net.casual.arcade.dimensions.utils.deleteCustomLevel
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import java.util.concurrent.ConcurrentLinkedQueue

object Multiverse: ModInitializer {
    const val MOD_ID = "multiverse"

    val logger = LoggerFactory.getLogger(MOD_ID)

    val worldsToDelete = ConcurrentLinkedQueue<CustomLevel>()

    val worldCreations = ConcurrentLinkedQueue<Runnable>()

    override fun onInitialize() {
        TickManagedCustomLevelFactory.register(DimensionRegistries.CUSTOM_LEVEL_FACTORY)

        RegistryEventHandler.register(MultiverseRegistries.LEVEL_STEM, ::registerCustomStems)

        GlobalEventHandler.Server.register<ServerRegisterCommandEvent> {
            it.register(MultiverseCommand)
        }

        ServerTickEvents.END_SERVER_TICK.register { server ->
            while (!worldsToDelete.isEmpty()) {
                val level = worldsToDelete.poll()
                // Now it is safe to delete because the tick loop is over
                server.deleteCustomLevel(level)
                logger.info("Processed deferred deletion for dimension: ${level.dimension().location()}")
            }

            while (!worldCreations.isEmpty()) {
                val creationTask = worldCreations.poll()
                try {
                    creationTask.run()
                } catch (e: Exception) {
                    logger.error("Failed to run deferred creation task", e)
                }
            }
        }
    }



    private fun registerCustomStems(event: RegistryLoadedFromResourcesEvent<LevelStem>) {
        val dimensions = event.lookupOrThrow(Registries.DIMENSION_TYPE)
        val overworld = dimensions.getOrThrow(BuiltinDimensionTypes.OVERWORLD)
        val biomes = event.lookupOrThrow(Registries.BIOME)
        val plains = FlatLevelGeneratorSettings.getDefaultBiome(biomes)

        Registry.register(
            event.registry, multiverse("void"), LevelStem(overworld, this.createSingleLayerGenerator(Blocks.AIR, plains))
        )
        Registry.register(
            event.registry, multiverse("white_glass"), LevelStem(overworld, this.createSingleLayerGenerator(Blocks.WHITE_STAINED_GLASS, plains))
        )

        // Copy stems from the vanilla registry
        val stems = event.lookupOrThrow(Registries.LEVEL_STEM) as HolderLookup
        for (stem in stems.listElements()) {
            Registry.register(event.registry, stem.key().location(), stem.value())
        }
    }

    private fun createSingleLayerGenerator(block: Block, biome: Holder<Biome>): FlatLevelSource {
        val settings = FlatLevelGeneratorSettings(Optional.empty(), biome, listOf())
        settings.layersInfo.add(FlatLayerInfo(1, block))
        settings.updateLayers()
        return FlatLevelSource(settings)
    }
}
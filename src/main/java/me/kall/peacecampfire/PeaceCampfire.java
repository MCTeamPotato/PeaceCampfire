package me.kall.peacecampfire;

import me.kall.duplicationless.event.BlockChangeEvent;
import me.kall.peacecampfire.data.PeaceChunks;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

@Mod(PeaceCampfire.MOD_ID)
public final class PeaceCampfire {
    public static final String MOD_ID = "peacecampfire";
    public static final Logger LOGGER = LogManager.getLogger(PeaceCampfire.class);

    public PeaceCampfire() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CONFIG);

        IEventBus forgeBus = MinecraftForge.EVENT_BUS;

        forgeBus.addListener(this::blockChange);
        forgeBus.addListener(this::dataRebuild);
        forgeBus.addListener(this::enemySpawn);
    }

    public void blockChange(@NotNull BlockChangeEvent event) {
        ServerLevel level = event.level();
        long chunk = event.chunkPos();
        long block = event.blockPos();
        int radius = PEACE_RADIUS.get();

        if (CampfireBlock.isLitCampfire(event.oldState())) {
            level.getServer().execute(() -> iterate(chunk, radius, chunkPos -> PeaceChunks.get(level).remove(level, chunkPos, block)));
        }

        if (CampfireBlock.isLitCampfire(event.newState())) {
            level.getServer().execute(() -> iterate(chunk, radius, chunkPos -> PeaceChunks.get(level).add(level, chunkPos, block)));
        }
    }

    private void iterate(long centerChunk, int radius, Consumer<Long> action) {
        int chunkX = ChunkPos.getX(centerChunk);
        int chunkZ = ChunkPos.getZ(centerChunk);

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                action.accept(ChunkPos.asLong(chunkX + dx, chunkZ + dz));
            }
        }
    }

    public void dataRebuild(@NotNull FMLServerStartedEvent event) {
        event.getServer().execute(() -> event.getServer().getAllLevels().forEach(level -> PeaceChunks.get(level).rebuild(level)));
    }

    public void enemySpawn(LivingSpawnEvent.@NotNull CheckSpawn event) {
        if (event.getResult().equals(Event.Result.DENY)) return;
        if (!(event.getEntity() instanceof Enemy) || !(event.getWorld() instanceof ServerLevel)) return;

        ServerLevel level = (ServerLevel) event.getWorld();
        long chunk = ChunkPos.asLong(SectionPos.blockToSectionCoord(Mth.floor(event.getX())), SectionPos.blockToSectionCoord(Mth.floor(event.getZ())));
        if (PeaceChunks.get(level).viewChunk(level, chunk).isEmpty()) return;
        event.setResult(Event.Result.DENY);
        if (DEBUG.get()) LOGGER.info("[PeaceCampfire] Prevent enemy {} spawning as the chunk [{}, {}] is in peace", event.getEntity(), ChunkPos.getX(chunk), ChunkPos.getZ(chunk));
    }

    private static final ForgeConfigSpec CONFIG;
    private static final ForgeConfigSpec.BooleanValue DEBUG;
    private static final ForgeConfigSpec.IntValue PEACE_RADIUS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("PeaceCampfire");

        DEBUG = builder.define("EnableDebugLogPrint", false);
        PEACE_RADIUS = builder.comment("The radius of chunks affected around the center. 1 means 3*3=9 chunks, 0 means only the current chunk is affected.").defineInRange("PeaceRadius", 2, 0, 16);

        builder.pop();
        CONFIG = builder.build();
    }
}

package dev.slowlight.block;

import dev.slowlight.Slowlight;
import dev.slowlight.SlowlightBlockEntities;
import dev.slowlight.recipe.AgeingRecipe;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Sandglass's memory: how many days it was set to and when it last turned over. Like the kilns it reads
 * {@link Level#getGameTime()}, so sleeping does not run the sand, and an unloaded chunk catches up when it loads.
 */
public class SandglassBlockEntity extends BlockEntity {
    public static final int MAX_DAYS = 7;
    private static final ResourceLocation FULL_WEEK = ResourceLocation.fromNamespaceAndPath(Slowlight.MOD_ID, "sandglass_week");

    /** Days per turn, 1 to 7; 0 when it has never been set and stands idle. */
    private int days;
    /** Game time it last turned over. */
    private long startedAt = -1L;

    public SandglassBlockEntity(BlockPos pos, BlockState state) {
        super(SlowlightBlockEntities.SANDGLASS.get(), pos, state);
    }

    public int days() {
        return days;
    }

    private long total() {
        return (long) days * AgeingRecipe.TICKS_PER_DAY;
    }

    /** One more day per click, back to one after seven; every click turns the glass over and starts again. */
    public void cycleDays(Level level, BlockPos pos, BlockState state) {
        days = days >= MAX_DAYS ? 1 : days + 1;
        startedAt = level.getGameTime();
        setChanged();
        level.setBlock(pos, state.setValue(SandglassBlock.SAND, 7).setValue(SandglassBlock.RUNNING, true), 3);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.6F, 0.7F);
    }

    /** Whether this world keeps days the glass can follow. Where it does not, the sand never settles. */
    public static boolean canTell(Level level) {
        return level.dimensionType().natural();
    }

    public Component status() {
        if (level != null && !canTell(level)) {
            return Component.translatable("block.slowlight.sandglass.lost");
        }
        if (days <= 0 || level == null) {
            return Component.translatable("block.slowlight.sandglass.idle");
        }
        long left = total() - (level.getGameTime() - startedAt);
        if (left <= AgeingRecipe.TICKS_PER_DAY) {
            return Component.translatable("block.slowlight.sandglass.today", days);
        }
        long d = (left + AgeingRecipe.TICKS_PER_DAY - 1L) / AgeingRecipe.TICKS_PER_DAY;
        return Component.translatable("block.slowlight.sandglass.days", d, days);
    }

    /** Sand left on top: 15 just turned, falling to 0 as the last grain goes. Idle, 0. */
    public int comparatorSignal() {
        if (level == null) {
            return 0;
        }
        if (!canTell(level)) {
            return getBlockState().getValue(SandglassBlock.SAND) * 2;
        }
        if (days <= 0) {
            return 0;
        }
        long left = Math.max(0L, total() - (level.getGameTime() - startedAt));
        return (int) ((left * 15L + total() - 1L) / total());
    }

    /** Eighths of sand on top for the model: 7 at the start of a turn, 0 in its last eighth. */
    private int sandLevel(long now) {
        long elapsed = Math.max(0L, now - startedAt);
        return Math.max(0, 7 - (int) (elapsed * 8L / total()));
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SandglassBlockEntity glass) {
        if (level.getGameTime() % 20L != 0L) {
            return;
        }
        BlockState next = state;
        if (!canTell(level)) {
            // Lost: the sand tumbles to a new place every second, and the glass will not pulse.
            next = state.setValue(SandglassBlock.SAND, level.random.nextInt(8)).setValue(SandglassBlock.RUNNING, true);
            level.setBlock(pos, next, 3);
            level.updateNeighbourForOutputSignal(pos, state.getBlock());
            return;
        }
        if (glass.days <= 0) {
            return;
        }
        long now = level.getGameTime();
        if (now - glass.startedAt >= glass.total()) {
            glass.turnOver(level, pos, state);
            return;
        }
        int sand = glass.sandLevel(now);
        if (sand != state.getValue(SandglassBlock.SAND) || !state.getValue(SandglassBlock.RUNNING)) {
            level.setBlock(pos, state.setValue(SandglassBlock.SAND, sand).setValue(SandglassBlock.RUNNING, true), 3);
        }
        level.updateNeighbourForOutputSignal(pos, state.getBlock());
    }

    /** The last grain is down: one pulse, and the glass turns itself over. */
    private void turnOver(Level level, BlockPos pos, BlockState state) {
        long now = level.getGameTime();
        // Keep the beat steady when it is only a tick or two late; after an unloaded stretch start afresh from now.
        startedAt = now - startedAt < total() * 2L ? startedAt + total() : now;
        setChanged();
        level.setBlock(pos, state.setValue(SandglassBlock.SAND, 7).setValue(SandglassBlock.RUNNING, true)
                .setValue(SandglassBlock.POWERED, true), 3);
        level.scheduleTick(pos, state.getBlock(), SandglassBlock.PULSE_TICKS);
        level.updateNeighbourForOutputSignal(pos, state.getBlock());
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.8F, 0.6F);
        if (days == MAX_DAYS && level instanceof ServerLevel server) {
            AdvancementHolder week = server.getServer().getAdvancements().get(FULL_WEEK);
            if (week != null) {
                for (ServerPlayer player : server.getPlayers(p -> p.blockPosition().closerThan(pos, 32.0D))) {
                    player.getAdvancements().award(week, "turned");
                }
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Days", days);
        tag.putLong("StartedAt", startedAt);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        days = tag.getInt("Days");
        startedAt = tag.contains("StartedAt") ? tag.getLong("StartedAt") : -1L;
    }
}

package dev.slowlight.block;

import dev.slowlight.SlowlightCrafts;
import dev.slowlight.recipe.AgeingRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * How long the candle has burned. It burns by the world's clock, not while someone watches: light it, go away for two
 * days, and it is two stages shorter when you come back. Each stage is a day of light; after the last it is gone.
 */
public class SlowCandleBlockEntity extends BlockEntity {
    public static final long STAGE_TICKS = AgeingRecipe.TICKS_PER_DAY;

    /** The burn, as one explicit state. Saved by name, never by ordinal. */
    public enum Burn {
        OUT("out"), LIT("lit");

        public final String id;

        Burn(String id) {
            this.id = id;
        }

        static Burn byId(String id) {
            for (Burn b : values()) {
                if (b.id.equals(id)) {
                    return b;
                }
            }
            return OUT;
        }
    }

    private Burn burn = Burn.OUT;
    /** Ticks burned before the current lighting. */
    private long burned;
    /** Game time it was lit; only meaningful while LIT. */
    private long litAt;

    public SlowCandleBlockEntity(BlockPos pos, BlockState state) {
        super(SlowlightCrafts.SLOW_CANDLE_BE.get(), pos, state);
    }

    private long burnedNow(Level level) {
        return burned + (burn == Burn.LIT ? level.getGameTime() - litAt : 0L);
    }

    public Burn burn() {
        return burn;
    }

    /** The one place the burn changes: lighting starts the clock, putting it out banks what has burned. */
    private void transition(Burn to, Level level) {
        if (to == burn) {
            return;
        }
        if (to == Burn.LIT) {
            litAt = level.getGameTime();
        } else {
            burned = burnedNow(level);
            litAt = 0L;
        }
        burn = to;
        setChanged();
    }

    public Component status() {
        if (level == null) {
            return Component.empty();
        }
        long left = (long) SlowCandleBlock.STAGES * STAGE_TICKS - burnedNow(level);
        long days = Math.max(1L, (left + STAGE_TICKS / 2L) / STAGE_TICKS);
        return Component.translatable(getBlockState().getValue(SlowCandleBlock.LIT)
                ? "block.slowlight.slow_candle.lit" : "block.slowlight.slow_candle.out", days);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SlowCandleBlockEntity candle) {
        if (level.getGameTime() % 20L != 0L) {
            return;
        }
        // A candle picked up and put down again keeps its height, so it has burned at least that much.
        int stage = state.getValue(SlowCandleBlock.STAGE);
        candle.burned = Math.max(candle.burned, (SlowCandleBlock.STAGES - stage) * STAGE_TICKS);
        // The block-state LIT is what flint, water and arrows change; the burn follows it here and nowhere else.
        boolean lit = state.getValue(SlowCandleBlock.LIT);
        candle.transition(lit ? Burn.LIT : Burn.OUT, level);
        if (!lit) {
            return;
        }
        long b = candle.burnedNow(level);
        if (b >= SlowCandleBlock.STAGES * STAGE_TICKS) {
            // Burned down to nothing: a last curl of smoke, and the dish is bare.
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            level.playSound(null, pos, SoundEvents.CANDLE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 0.8F);
            if (level instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5D, pos.getY() + 0.2D, pos.getZ() + 0.5D, 6, 0.1D, 0.1D, 0.1D, 0.01D);
            }
            return;
        }
        int now = SlowCandleBlock.STAGES - (int) (b / STAGE_TICKS);
        if (now != stage) {
            level.setBlock(pos, state.setValue(SlowCandleBlock.STAGE, now), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("State", burn.id);
        tag.putLong("Burned", burned);
        if (burn == Burn.LIT) {
            tag.putLong("LitAt", litAt);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        burned = tag.getLong("Burned");
        // Saves from before 0.8.1 have no State: a LitAt of -1 meant out. A lit burn without its start time is out.
        long at = tag.contains("LitAt") ? tag.getLong("LitAt") : -1L;
        burn = tag.contains("State") ? Burn.byId(tag.getString("State")) : at >= 0L ? Burn.LIT : Burn.OUT;
        if (burn == Burn.LIT && at < 0L) {
            burn = Burn.OUT;
        }
        litAt = burn == Burn.LIT ? at : 0L;
    }
}

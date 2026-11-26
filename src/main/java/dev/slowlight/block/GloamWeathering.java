package dev.slowlight.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.WeatheringCopperDoorBlock;
import net.minecraft.world.level.block.WeatheringCopperTrapDoorBlock;
import net.minecraft.world.level.block.WeatheringCopperFullBlock;
import net.minecraft.world.level.block.WeatheringCopperSlabBlock;
import net.minecraft.world.level.block.WeatheringCopperStairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;

/**
 * Gloamwood that silvers in the weather, in copper's four steps: Gloamwood, Faded, Weathered, Silvered. It uses
 * copper's own machinery, so the steps come from NeoForge's oxidizables data map, a honeycomb waxes it and an axe
 * scrapes a step (or the wax) back off. Unlike copper it only weathers out in the open: a plank with sky over one of
 * its faces. Houses built of it keep their colour indoors. The plank patterns, fence, gate, door, trapdoor, button,
 * pressure plate, logs, wood and signs weather the same way. An axe strips a log before it scrapes it, so a weathered
 * log strips to the same step's stripped log. Signs are waxed by vanilla's own honeycomb, which also stops their
 * ageing; their writing goes with them from step to step, and an axe takes the wax or a step back off.
 */
public final class GloamWeathering {
    private GloamWeathering() {
    }

    /** True when the sky reaches one of the block's faces, the bottom aside. */
    public static boolean outdoors(Level level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            if (d != Direction.DOWN && level.canSeeSky(pos.relative(d))
                    && !level.getBlockState(pos.relative(d)).isSolidRender(level, pos.relative(d))) {
                return true;
            }
        }
        return false;
    }

    public static class Planks extends WeatheringCopperFullBlock {
        public Planks(WeatheringCopper.WeatherState age, Properties properties) {
            super(age, properties);
        }

        @Override
        protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            if (outdoors(level, pos)) {
                super.randomTick(state, level, pos, random);
            }
        }
    }

    public static class Stairs extends WeatheringCopperStairBlock {
        public Stairs(WeatheringCopper.WeatherState age, BlockState base, Properties properties) {
            super(age, base, properties);
        }

        @Override
        protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            if (outdoors(level, pos)) {
                super.randomTick(state, level, pos, random);
            }
        }
    }

    public static class Slab extends WeatheringCopperSlabBlock {
        public Slab(WeatheringCopper.WeatherState age, Properties properties) {
            super(age, properties);
        }

        @Override
        protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            if (outdoors(level, pos)) {
                super.randomTick(state, level, pos, random);
            }
        }
    }

    public static class Door extends WeatheringCopperDoorBlock {
        public Door(WeatheringCopper.WeatherState age, BlockSetType set, Properties properties) {
            super(set, age, properties);
        }

        @Override
        protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            if (outdoors(level, pos)) {
                super.randomTick(state, level, pos, random);
            }
        }
    }

    public static class TrapDoor extends WeatheringCopperTrapDoorBlock {
        public TrapDoor(WeatheringCopper.WeatherState age, BlockSetType set, Properties properties) {
            super(set, age, properties);
        }

        @Override
        protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            if (outdoors(level, pos)) {
                super.randomTick(state, level, pos, random);
            }
        }
    }

    // Copper has no fence, gate, button or pressure plate, so these carry copper's ageing themselves.
    public static class Fence extends FenceBlock implements WeatheringCopper {
        private final WeatheringCopper.WeatherState age;

        public Fence(WeatheringCopper.WeatherState age, Properties properties) {
            super(properties);
            this.age = age;
        }

        @Override
        public WeatheringCopper.WeatherState getAge() {
            return age;
        }

        @Override
        protected boolean isRandomlyTicking(BlockState state) {
            return WeatheringCopper.getNext(state.getBlock()).isPresent();
        }

        @Override
        protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            if (outdoors(level, pos)) {
                changeOverTime(state, level, pos, random);
            }
        }
    }

    public static class Gate extends FenceGateBlock implements WeatheringCopper {
        private final WeatheringCopper.WeatherState age;

        public Gate(WeatheringCopper.WeatherState age, WoodType wood, Properties properties) {
            super(wood, properties);
            this.age = age;
        }

        @Override
        public WeatheringCopper.WeatherState getAge() {
            return age;
        }

        @Override
        protected boolean isRandomlyTicking(BlockState state) {
            return WeatheringCopper.getNext(state.getBlock()).isPresent();
        }

        @Override
        protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            if (outdoors(level, pos)) {
                changeOverTime(state, level, pos, random);
            }
        }
    }

    public static class Button extends ButtonBlock implements WeatheringCopper {
        private final WeatheringCopper.WeatherState age;

        public Button(WeatheringCopper.WeatherState age, BlockSetType set, int ticksHeld, Properties properties) {
            super(set, ticksHeld, properties);
            this.age = age;
        }

        @Override
        public WeatheringCopper.WeatherState getAge() {
            return age;
        }

        @Override
        protected boolean isRandomlyTicking(BlockState state) {
            return WeatheringCopper.getNext(state.getBlock()).isPresent();
        }

        @Override
        protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            if (outdoors(level, pos)) {
                changeOverTime(state, level, pos, random);
            }
        }
    }

    public static class Plate extends PressurePlateBlock implements WeatheringCopper {
        private final WeatheringCopper.WeatherState age;

        public Plate(WeatheringCopper.WeatherState age, BlockSetType set, Properties properties) {
            super(set, properties);
            this.age = age;
        }

        @Override
        public WeatheringCopper.WeatherState getAge() {
            return age;
        }

        @Override
        protected boolean isRandomlyTicking(BlockState state) {
            return WeatheringCopper.getNext(state.getBlock()).isPresent();
        }

        @Override
        protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            if (outdoors(level, pos)) {
                changeOverTime(state, level, pos, random);
            }
        }
    }

    /** A log, wood or stripped one. {@code stripped} is the same step's stripped twin, or null for stripped ones. */
    public static class Log extends RotatedPillarBlock implements WeatheringCopper {
        private final WeatheringCopper.WeatherState age;
        private final Supplier<? extends Block> stripped;

        public Log(WeatheringCopper.WeatherState age, Supplier<? extends Block> stripped, Properties properties) {
            super(properties);
            this.age = age;
            this.stripped = stripped;
        }

        @Override
        public WeatheringCopper.WeatherState getAge() {
            return age;
        }

        @Override
        public BlockState getToolModifiedState(BlockState state, UseOnContext context, ItemAbility ability, boolean simulate) {
            if (ability == ItemAbilities.AXE_STRIP && stripped != null) {
                return stripped.get().defaultBlockState().setValue(AXIS, state.getValue(AXIS));
            }
            return super.getToolModifiedState(state, context, ability, simulate);
        }

        @Override
        protected boolean isRandomlyTicking(BlockState state) {
            return WeatheringCopper.getNext(state.getBlock()).isPresent();
        }

        @Override
        protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            if (outdoors(level, pos)) {
                changeOverTime(state, level, pos, random);
            }
        }
    }

    /** Swaps a sign for another step, its writing and wax carried over. */
    static void swapSign(Level level, BlockPos pos, BlockState next) {
        BlockEntity old = level.getBlockEntity(pos);
        CompoundTag data = old == null ? null : old.saveWithoutMetadata(level.registryAccess());
        level.setBlock(pos, next, Block.UPDATE_ALL_IMMEDIATE);
        BlockEntity now = level.getBlockEntity(pos);
        if (data != null && now != null) {
            now.loadWithComponents(data, level.registryAccess());
            now.setChanged();
            level.sendBlockUpdated(pos, next, next, Block.UPDATE_ALL);
        }
    }

    /** A sign's random tick: out in the open and not waxed, it may go one step on. */
    static void ageSign(WeatheringCopper block, BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!outdoors(level, pos) || level.getBlockEntity(pos) instanceof SignBlockEntity sign && sign.isWaxed()) {
            return;
        }
        block.getNextState(state, level, pos, random).ifPresent(next -> swapSign(level, pos, next));
    }

    /** An axe on a sign takes the wax off, or else a step. Null when it does neither, so the sign acts as usual. */
    static ItemInteractionResult scrapeSign(BlockState state, ItemStack stack, Level level, BlockPos pos, Player player,
                                            InteractionHand hand) {
        if (!stack.canPerformAction(ItemAbilities.AXE_SCRAPE) || !(level.getBlockEntity(pos) instanceof SignBlockEntity sign)) {
            return null;
        }
        if (sign.isWaxed()) {
            if (!level.isClientSide) {
                sign.setWaxed(false);
                level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.AXE_WAX_OFF, SoundSource.BLOCKS, 1.0F, 1.0F);
                level.levelEvent(null, 3004, pos, 0);
            }
        } else {
            var previous = WeatheringCopper.getPrevious(state);
            if (previous.isEmpty()) {
                return null;
            }
            if (!level.isClientSide) {
                swapSign(level, pos, previous.get());
                level.playSound(null, pos, SoundEvents.AXE_SCRAPE, SoundSource.BLOCKS, 1.0F, 1.0F);
                level.levelEvent(null, 3005, pos, 0);
            }
        }
        if (!level.isClientSide) {
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, state));
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    public static class Sign extends StandingSignBlock implements WeatheringCopper {
        private final WeatheringCopper.WeatherState age;

        public Sign(WeatheringCopper.WeatherState age, WoodType wood, Properties properties) {
            super(wood, properties);
            this.age = age;
        }

        @Override
        public WeatheringCopper.WeatherState getAge() {
            return age;
        }

        @Override
        protected boolean isRandomlyTicking(BlockState state) {
            return WeatheringCopper.getNext(state.getBlock()).isPresent();
        }

        @Override
        protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            ageSign(this, state, level, pos, random);
        }

        @Override
        protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                                  InteractionHand hand, BlockHitResult hit) {
            ItemInteractionResult r = scrapeSign(state, stack, level, pos, player, hand);
            return r != null ? r : super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
    }

    public static class WallSign extends WallSignBlock implements WeatheringCopper {
        private final WeatheringCopper.WeatherState age;

        public WallSign(WeatheringCopper.WeatherState age, WoodType wood, Properties properties) {
            super(wood, properties);
            this.age = age;
        }

        @Override
        public WeatheringCopper.WeatherState getAge() {
            return age;
        }

        @Override
        protected boolean isRandomlyTicking(BlockState state) {
            return WeatheringCopper.getNext(state.getBlock()).isPresent();
        }

        @Override
        protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            ageSign(this, state, level, pos, random);
        }

        @Override
        protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                                  InteractionHand hand, BlockHitResult hit) {
            ItemInteractionResult r = scrapeSign(state, stack, level, pos, player, hand);
            return r != null ? r : super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
    }

    public static class HangingSign extends CeilingHangingSignBlock implements WeatheringCopper {
        private final WeatheringCopper.WeatherState age;

        public HangingSign(WeatheringCopper.WeatherState age, WoodType wood, Properties properties) {
            super(wood, properties);
            this.age = age;
        }

        @Override
        public WeatheringCopper.WeatherState getAge() {
            return age;
        }

        @Override
        protected boolean isRandomlyTicking(BlockState state) {
            return WeatheringCopper.getNext(state.getBlock()).isPresent();
        }

        @Override
        protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            ageSign(this, state, level, pos, random);
        }

        @Override
        protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                                  InteractionHand hand, BlockHitResult hit) {
            ItemInteractionResult r = scrapeSign(state, stack, level, pos, player, hand);
            return r != null ? r : super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
    }

    public static class WallHangingSign extends WallHangingSignBlock implements WeatheringCopper {
        private final WeatheringCopper.WeatherState age;

        public WallHangingSign(WeatheringCopper.WeatherState age, WoodType wood, Properties properties) {
            super(wood, properties);
            this.age = age;
        }

        @Override
        public WeatheringCopper.WeatherState getAge() {
            return age;
        }

        @Override
        protected boolean isRandomlyTicking(BlockState state) {
            return WeatheringCopper.getNext(state.getBlock()).isPresent();
        }

        @Override
        protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            ageSign(this, state, level, pos, random);
        }

        @Override
        protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                                  InteractionHand hand, BlockHitResult hit) {
            ItemInteractionResult r = scrapeSign(state, stack, level, pos, player, hand);
            return r != null ? r : super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
    }
}

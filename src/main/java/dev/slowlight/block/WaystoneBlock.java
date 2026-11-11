package dev.slowlight.block;

import com.mojang.serialization.MapCodec;
import dev.slowlight.SlowlightWays;
import dev.slowlight.item.HalfPearlItem;
import dev.slowlight.recipe.AgeingRecipe;
import dev.slowlight.recipe.TransitRecipe;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * An end stone obelisk with a pearl set in its top. Give it a Twin Pearl and it keeps one half; set the other half in
 * a second stone and the two are a pair. Then anything held up to one is sent to the other, a stack at a time, for a
 * Chorus Pearl: it arrives a day later in the same dimension, three days later in another. An empty hand takes what
 * has arrived, or asks how things stand.
 */
public class WaystoneBlock extends BaseEntityBlock {
    public static final MapCodec<WaystoneBlock> CODEC = simpleCodec(WaystoneBlock::new);
    public static final BooleanProperty LINKED = BooleanProperty.create("linked");
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public static final int SAME_DIMENSION_DAYS = 1;
    public static final int ACROSS_DAYS = 3;
    private static final int PULSE_TICKS = 40;

    /** The obelisk's footprint; the same numbers as tools/make_waystone.py. */
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 14, 15);

    public WaystoneBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LINKED, false).setValue(ACTIVE, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LINKED, ACTIVE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WaystoneBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, SlowlightWays.WAYSTONE_BE.get(), WaystoneBlockEntity::serverTick);
    }

    /** Lights the stone's teal pulse for a couple of seconds. */
    static void pulse(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state.setValue(ACTIVE, true), 3);
        level.scheduleTick(pos, state.getBlock(), PULSE_TICKS);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D,
                20, 0.2D, 0.3D, 0.2D, 0.04D);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(ACTIVE)) {
            level.setBlock(pos, state.setValue(ACTIVE, false), 3);
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty() || !(level.getBlockEntity(pos) instanceof WaystoneBlockEntity stone)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide || !(level instanceof ServerLevel server)) {
            return ItemInteractionResult.SUCCESS;
        }
        WaystoneNetwork net = WaystoneNetwork.get(server.getServer());
        GlobalPos here = stone.here();
        boolean pearl = stack.is(SlowlightWays.TWIN_PEARL.get()) || stack.is(SlowlightWays.HALF_PEARL.get());
        if (stone.pair() == null) {
            if (!pearl) {
                say(player, "block.slowlight.waystone.unlinked");
                return ItemInteractionResult.CONSUME;
            }
            UUID pair;
            if (stack.is(SlowlightWays.TWIN_PEARL.get())) {
                pair = UUID.randomUUID();
            } else {
                HalfPearlItem.Link link = stack.get(SlowlightWays.PEARL_LINK.get());
                if (link == null) {
                    say(player, "item.slowlight.half_pearl.lost");
                    return ItemInteractionResult.CONSUME;
                }
                pair = link.pair();
            }
            if (!net.join(pair, here)) {
                say(player, "block.slowlight.waystone.pair_full");
                return ItemInteractionResult.CONSUME;
            }
            stone.setPair(pair);
            boolean twin = stack.is(SlowlightWays.TWIN_PEARL.get());
            stack.consume(1, player);
            if (twin) {
                ItemStack half = HalfPearlItem.of(pair, Optional.of(here));
                if (!player.getInventory().add(half)) {
                    player.drop(half, false);
                }
                say(player, "block.slowlight.waystone.kept_half");
            } else {
                say(player, "block.slowlight.waystone.paired");
            }
            server.setBlock(pos, state.setValue(LINKED, true), 3);
            pulse(server, pos, server.getBlockState(pos));
            server.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.2F, 0.8F);
            return ItemInteractionResult.CONSUME;
        }
        if (pearl) {
            say(player, "block.slowlight.waystone.already_linked");
            return ItemInteractionResult.CONSUME;
        }
        Optional<GlobalPos> partner = net.partner(stone.pair(), here);
        if (partner.isEmpty()) {
            say(player, "block.slowlight.waystone.waiting_twin");
            return ItemInteractionResult.CONSUME;
        }
        long now = WaystoneNetwork.now(server.getServer());
        Optional<WaystoneNetwork.Shipment> out = net.outgoing(stone.pair(), here);
        if (out.isPresent()) {
            player.displayClientMessage(Component.translatable("block.slowlight.waystone.busy",
                    WaystoneBlockEntity.when(out.get().arriveAt() - now)), true);
            return ItemInteractionResult.CONSUME;
        }
        if (!player.hasInfiniteMaterials() && !payPearl(player, stack)) {
            say(player, "block.slowlight.waystone.needs_pearl");
            return ItemInteractionResult.CONSUME;
        }
        ItemStack sent = stack.copyAndClear();
        if (sent.isEmpty()) {
            return ItemInteractionResult.CONSUME;
        }
        GlobalPos to = partner.get();
        Optional<TransitRecipe> route = TransitRecipe.find(server.getRecipeManager(), sent, server, here.dimension(),
                to.dimension());
        List<ItemStack> items = new ArrayList<>();
        int days = here.dimension().equals(to.dimension()) ? SAME_DIMENSION_DAYS : ACROSS_DAYS;
        if (route.isPresent()) {
            ItemStack result = route.get().result();
            int total = result.getCount() * sent.getCount();
            while (total > 0) {
                int n = Math.min(total, result.getMaxStackSize());
                items.add(result.copyWithCount(n));
                total -= n;
            }
            if (route.get().days() > 0) {
                days = route.get().days();
            }
        } else {
            items.add(sent);
        }
        long ticks = (long) days * AgeingRecipe.TICKS_PER_DAY;
        net.send(new WaystoneNetwork.Shipment(stone.pair(), here, items, now + ticks));
        pulse(server, pos, state);
        server.playSound(null, pos, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.BLOCKS, 1.0F, 1.1F);
        player.displayClientMessage(Component.translatable("block.slowlight.waystone.sent",
                WaystoneBlockEntity.when(ticks)), true);
        return ItemInteractionResult.CONSUME;
    }

    /** Burns one Chorus Pearl from the inventory, taking it from the stack being sent only if there is no other. */
    private static boolean payPearl(Player player, ItemStack sending) {
        Item pearl = SlowlightWays.CHORUS_PEARL.get();
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s != sending && s.is(pearl)) {
                s.shrink(1);
                return true;
            }
        }
        if (sending.is(pearl) && sending.getCount() >= 2) {
            sending.shrink(1);
            return true;
        }
        return false;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof WaystoneBlockEntity stone)) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        if (stone.hasArrived()) {
            for (ItemStack item : stone.takeArrived()) {
                if (!player.getInventory().add(item)) {
                    player.drop(item, false);
                }
            }
            server.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.0F);
            return InteractionResult.CONSUME;
        }
        player.displayClientMessage(status(server, stone), true);
        return InteractionResult.CONSUME;
    }

    private static Component status(ServerLevel server, WaystoneBlockEntity stone) {
        if (stone.pair() == null) {
            return Component.translatable("block.slowlight.waystone.unlinked");
        }
        WaystoneNetwork net = WaystoneNetwork.get(server.getServer());
        GlobalPos here = stone.here();
        Optional<GlobalPos> partner = net.partner(stone.pair(), here);
        if (partner.isEmpty()) {
            return Component.translatable("block.slowlight.waystone.waiting_twin");
        }
        long now = WaystoneNetwork.now(server.getServer());
        GlobalPos p = partner.get();
        Component msg = Component.translatable("block.slowlight.waystone.linked_to", p.pos().getX(), p.pos().getY(),
                p.pos().getZ(), p.dimension().location().getPath());
        Optional<WaystoneNetwork.Shipment> in = net.incoming(stone.pair(), here);
        if (in.isPresent()) {
            msg = Component.translatable("block.slowlight.waystone.incoming", msg,
                    WaystoneBlockEntity.when(in.get().arriveAt() - now));
        }
        return msg;
    }

    private static void say(Player player, String key) {
        player.displayClientMessage(Component.translatable(key), true);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server
                && level.getBlockEntity(pos) instanceof WaystoneBlockEntity stone) {
            for (ItemStack item : stone.takeArrived()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), item);
            }
            UUID pair = stone.pair();
            if (pair != null) {
                // The stone gives its half back, still knowing where the other stone stands.
                WaystoneNetwork net = WaystoneNetwork.get(server.getServer());
                GlobalPos here = stone.here();
                net.leave(pair, here);
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(),
                        HalfPearlItem.of(pair, net.partner(pair, here)));
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("block.slowlight.waystone.tip").withStyle(ChatFormatting.GRAY));
    }
}

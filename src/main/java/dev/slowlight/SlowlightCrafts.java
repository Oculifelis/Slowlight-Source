package dev.slowlight;

import dev.slowlight.block.FlowerPressBlock;
import dev.slowlight.block.FlowerPressBlockEntity;
import dev.slowlight.block.PressedFlowersBlock;
import dev.slowlight.block.PressedFlowersBlockEntity;
import dev.slowlight.block.SlowCandleBlock;
import dev.slowlight.block.SlowCandleBlockEntity;
import dev.slowlight.item.PressedFlowerItem;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Things made by hand and left alone: the Flower Press and the Slow Candle.
 *
 * <p>The press takes anything in {@code #slowlight:pressable} (which holds {@code #minecraft:flowers} and, when
 * present, {@code #c:flowers}), so every modded flower presses too, with no list kept here. What comes out is one
 * item, Pressed Flower, that remembers which flower it was in a data component and draws that flower's own icon, pressed
 * flat, so it never needs art of its own per flower.
 */
public final class SlowlightCrafts {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Slowlight.MOD_ID);
    /** The flower a Pressed Flower was made from. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Holder<Item>>> PRESSED_FROM =
            COMPONENTS.registerComponentType("pressed_from", b -> b.persistent(BuiltInRegistries.ITEM.holderByNameCodec())
                    .networkSynchronized(ByteBufCodecs.holderRegistry(Registries.ITEM)));

    public static final TagKey<Item> PRESSABLE = ItemTags.create(ResourceLocation.fromNamespaceAndPath(Slowlight.MOD_ID, "pressable"));

    public static final DeferredBlock<Block> FLOWER_PRESS = SlowlightBlocks.BLOCKS.register("flower_press",
            () -> new FlowerPressBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS)
                    .mapColor(MapColor.TERRACOTTA_LIGHT_BLUE).noOcclusion()));
    public static final DeferredItem<BlockItem> FLOWER_PRESS_ITEM = SlowlightItems.ITEMS.registerSimpleBlockItem(FLOWER_PRESS);
    public static final DeferredItem<PressedFlowerItem> PRESSED_FLOWER = SlowlightItems.ITEMS.register("pressed_flower",
            () -> new PressedFlowerItem(new Item.Properties()));
    /** Pressed Flowers laid on the ground or a wall; no item of its own (the Pressed Flower places it). */
    public static final DeferredBlock<Block> PRESSED_FLOWERS = SlowlightBlocks.BLOCKS.register("pressed_flowers",
            () -> new PressedFlowersBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SAND).noCollission().instabreak()
                    .noOcclusion().sound(SoundType.PINK_PETALS).pushReaction(PushReaction.DESTROY).noLootTable()
                    .lightLevel(s -> s.getValue(PressedFlowersBlock.LIGHT))));

    public static final DeferredBlock<Block> SLOW_CANDLE = SlowlightBlocks.BLOCKS.register("slow_candle",
            () -> new SlowCandleBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CANDLE).mapColor(MapColor.SAND)
                    .lightLevel(SlowCandleBlock::light)));
    public static final DeferredItem<BlockItem> SLOW_CANDLE_ITEM = SlowlightItems.ITEMS.registerSimpleBlockItem(SLOW_CANDLE);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FlowerPressBlockEntity>> FLOWER_PRESS_BE =
            SlowlightBlockEntities.BLOCK_ENTITIES.register("flower_press", () -> BlockEntityType.Builder
                    .of(FlowerPressBlockEntity::new, FLOWER_PRESS.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SlowCandleBlockEntity>> SLOW_CANDLE_BE =
            SlowlightBlockEntities.BLOCK_ENTITIES.register("slow_candle", () -> BlockEntityType.Builder
                    .of(SlowCandleBlockEntity::new, SLOW_CANDLE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PressedFlowersBlockEntity>> PRESSED_FLOWERS_BE =
            SlowlightBlockEntities.BLOCK_ENTITIES.register("pressed_flowers", () -> BlockEntityType.Builder
                    .of(PressedFlowersBlockEntity::new, PRESSED_FLOWERS.get()).build(null));

    /** A Pressed Flower made from this flower. */
    public static ItemStack pressed(Holder<Item> flower) {
        ItemStack out = new ItemStack(PRESSED_FLOWER.get());
        out.set(PRESSED_FROM.get(), flower);
        return out;
    }

    static void fillTab(CreativeModeTab.Output output, CreativeModeTab.ItemDisplayParameters params) {
        output.accept(FLOWER_PRESS_ITEM.get());
        output.accept(SLOW_CANDLE_ITEM.get());
        params.holders().lookupOrThrow(Registries.ITEM).get(PRESSABLE)
                .ifPresent(set -> set.forEach(flower -> output.accept(pressed(flower))));
    }

    /** Loads the class, so everything above is registered before the registries close. */
    static void init() {
    }

    private SlowlightCrafts() {
    }
}

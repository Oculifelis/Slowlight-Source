package dev.slowlight;

import dev.slowlight.block.DisplayPedestalBlock;
import dev.slowlight.block.DisplayPedestalBlockEntity;
import dev.slowlight.block.PearlLanternBlock;
import dev.slowlight.block.StarwickCandleBlock;
import dev.slowlight.block.WaystoneBlock;
import dev.slowlight.block.WaystoneBlockEntity;
import dev.slowlight.item.ChorusPearlItem;
import dev.slowlight.item.HalfPearlItem;
import dev.slowlight.item.UnfallingFeatherItem;
import dev.slowlight.item.WanderingAppleItem;
import dev.slowlight.recipe.TransitRecipe;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Waystones and what travels between them.
 *
 * <p>Two Chorus Pearls left touching on the ground fuse into a Twin Pearl. Set it in a Waystone and the stone keeps one
 * half; set the Half Pearl in a second stone and the two are paired. A paired stone sends one stack at a time to its
 * twin, burning a Chorus Pearl a trip; it arrives about a day later in the same dimension and about three days later
 * across dimensions. Some things change on the way: {@code slowlight:transit} recipes name an item, where it leaves from
 * and where it arrives, and what it becomes. Most of the blocks here are what those routes make.
 */
public final class SlowlightWays {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, Slowlight.MOD_ID);
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> STARWICK_FLAME =
            PARTICLES.register("starwick_flame", () -> new SimpleParticleType(false));

    /** Which pair a Half Pearl belongs to, and where its twin sits. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<HalfPearlItem.Link>> PEARL_LINK =
            SlowlightCrafts.COMPONENTS.registerComponentType("pearl_link", b -> b.persistent(HalfPearlItem.Link.CODEC)
                    .networkSynchronized(HalfPearlItem.Link.STREAM_CODEC));

    public static final DeferredHolder<RecipeType<?>, RecipeType<TransitRecipe>> TRANSIT_TYPE =
            SlowlightRecipes.RECIPE_TYPES.register("transit", id -> RecipeType.simple(id));
    public static final DeferredHolder<RecipeSerializer<?>, TransitRecipe.Serializer> TRANSIT_SERIALIZER =
            SlowlightRecipes.RECIPE_SERIALIZERS.register("transit", () -> TransitRecipe.Serializer.INSTANCE);

    /** Every block item here, in creative-tab order. */
    private static final List<DeferredItem<? extends Item>> TAB = new ArrayList<>();

    public static final DeferredItem<Item> CHORUS_PEARL = item(SlowlightItems.ITEMS.register("chorus_pearl",
            () -> new ChorusPearlItem(new Item.Properties().stacksTo(16))));
    public static final DeferredItem<Item> TWIN_PEARL = item(SlowlightItems.ITEMS.registerSimpleItem("twin_pearl",
            new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<Item> HALF_PEARL = item(SlowlightItems.ITEMS.register("half_pearl",
            () -> new HalfPearlItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON))));

    public static final DeferredBlock<Block> WAYSTONE = block("waystone", () -> new WaystoneBlock(
            BlockBehaviour.Properties.ofFullCopy(Blocks.END_STONE_BRICKS).mapColor(MapColor.COLOR_PURPLE).noOcclusion()
                    .lightLevel(s -> s.getValue(WaystoneBlock.LINKED) ? 5 : 0)));
    public static final DeferredBlock<Block> DISPLAY_PEDESTAL = block("display_pedestal", () -> new DisplayPedestalBlock(
            BlockBehaviour.Properties.ofFullCopy(Blocks.POLISHED_DEEPSLATE).noOcclusion()));

    public static final DeferredItem<Item> UNFALLING_FEATHER = item(SlowlightItems.ITEMS.register("unfalling_feather",
            () -> new UnfallingFeatherItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON))));
    public static final DeferredItem<Item> WANDERING_APPLE = item(SlowlightItems.ITEMS.register("wandering_apple",
            () -> new WanderingAppleItem(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(4).saturationModifier(0.3F).alwaysEdible().build()))));

    public static final DeferredBlock<Block> STARWICK_CANDLE = block("starwick_candle", () -> new StarwickCandleBlock(
            BlockBehaviour.Properties.ofFullCopy(Blocks.CANDLE).mapColor(MapColor.COLOR_PURPLE)
                    .lightLevel(StarwickCandleBlock.LIGHT_EMISSION)));
    public static final DeferredBlock<Block> STARGLASS = block("starglass", () -> new TransparentBlock(
            BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS).mapColor(MapColor.COLOR_BLACK).lightLevel(s -> 3)));

    public static final DeferredBlock<Block> HOLLOWSTONE = block("hollowstone", () -> new Block(hollowstone()));
    public static final DeferredBlock<Block> HOLLOWSTONE_STAIRS = block("hollowstone_stairs",
            () -> new StairBlock(HOLLOWSTONE.get().defaultBlockState(), hollowstone()));
    public static final DeferredBlock<Block> HOLLOWSTONE_SLAB = block("hollowstone_slab", () -> new SlabBlock(hollowstone()));
    public static final DeferredBlock<Block> HOLLOWSTONE_WALL = block("hollowstone_wall",
            () -> new WallBlock(hollowstone().forceSolidOn()));

    public static final DeferredBlock<Block> PEARL_BLOCK = block("pearl_block", () -> new Block(
            BlockBehaviour.Properties.ofFullCopy(Blocks.AMETHYST_BLOCK).mapColor(MapColor.COLOR_PURPLE).lightLevel(s -> 5)));
    public static final DeferredBlock<Block> PEARL_LANTERN = block("pearl_lantern", () -> new PearlLanternBlock(
            BlockBehaviour.Properties.ofFullCopy(Blocks.LANTERN).mapColor(MapColor.COLOR_PURPLE).lightLevel(s -> 13)));
    public static final DeferredBlock<Block> PEARL_CHAIN = block("pearl_chain", () -> new ChainBlock(
            BlockBehaviour.Properties.ofFullCopy(Blocks.CHAIN).mapColor(MapColor.COLOR_PURPLE)));

    /** Nether glass: four colours, each in three styles a stonecutter switches between. */
    public static final List<String> NETHER_GLASS_COLOURS = List.of("crimson", "warped", "soul", "magma");
    public static final List<String> NETHER_GLASS_STYLES = List.of("mosaic", "rose", "lattice");
    public static final List<DeferredBlock<Block>> NETHER_GLASS = new ArrayList<>();
    static {
        for (String colour : NETHER_GLASS_COLOURS) {
            for (String style : NETHER_GLASS_STYLES) {
                NETHER_GLASS.add(block(colour + "_" + style + "_glass", () -> new TransparentBlock(
                        BlockBehaviour.Properties.ofFullCopy(Blocks.RED_STAINED_GLASS).mapColor(MapColor.NETHER))));
            }
        }
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WaystoneBlockEntity>> WAYSTONE_BE =
            SlowlightBlockEntities.BLOCK_ENTITIES.register("waystone", () -> BlockEntityType.Builder
                    .of(WaystoneBlockEntity::new, WAYSTONE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DisplayPedestalBlockEntity>> DISPLAY_PEDESTAL_BE =
            SlowlightBlockEntities.BLOCK_ENTITIES.register("display_pedestal", () -> BlockEntityType.Builder
                    .of(DisplayPedestalBlockEntity::new, DISPLAY_PEDESTAL.get()).build(null));

    private static BlockBehaviour.Properties hollowstone() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_BRICKS).mapColor(MapColor.COLOR_BLACK);
    }

    private static DeferredBlock<Block> block(String name, Supplier<Block> block) {
        DeferredBlock<Block> b = SlowlightBlocks.BLOCKS.register(name, block);
        TAB.add(SlowlightItems.ITEMS.registerSimpleBlockItem(b));
        return b;
    }

    private static <T extends Item> DeferredItem<T> item(DeferredItem<T> item) {
        TAB.add(item);
        return item;
    }

    static void fillTab(CreativeModeTab.Output output) {
        TAB.forEach(i -> output.accept(i.get()));
    }

    /** Loads the class, so everything above is registered before the registries close. */
    static void init() {
    }

    private SlowlightWays() {
    }
}

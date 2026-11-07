package dev.slowlight;

import dev.slowlight.block.LapseBlock;
import dev.slowlight.item.MeadItem;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * The decor batch: Slowlight and Still Shard blocks with the variants Rechiseled gives the diamond block, Gloamwood
 * plank variants matching Rechiseled's oak set (each variant with stairs and a slab), shard clusters, the Gloamwood
 * bookshelf, the Lapse Block, and kiln foods for Twilight Forest, the Undergarden and the Aether. The variant names
 * are Rechiseled's, so data/slowlight/chiseling_recipes/ can hand them to its chisel; without Rechiseled the
 * stonecutter makes them. Models, recipes and tags are written by tools/make_decor_data.py.
 */
public final class SlowlightDecor {
    public static final List<String> GEM_VARIANTS = List.of("chiseled", "chiseled_cubes", "diagonal_tiles", "grid",
            "jewel_block", "polished", "rhombuses", "shiny", "shiny_bordered", "small_tiles", "smooth");
    public static final List<String> PLANK_VARIANTS = List.of("beams", "brick_pattern", "brick_paving", "bricks", "crate",
            "diagonal_stripes", "diagonal_tiles", "dotted", "flooring", "large_tiles", "mosaic", "pattern",
            "rotated_bricks", "small_bricks", "small_tiles", "squares", "tiles", "wavy", "woven");

    /** Every decor block item, in creative-tab order. */
    public static final List<DeferredItem<BlockItem>> BLOCK_ITEMS = new ArrayList<>();

    public static final DeferredBlock<Block> SLOWLIGHT_SHARD_BLOCK = solid("slowlight_shard_block", SlowlightDecor::slowlightGem);
    static {
        variants("slowlight_shard_block", GEM_VARIANTS, SlowlightDecor::slowlightGem);
    }
    public static final DeferredBlock<Block> SLOWLIGHT_SHARD_CLUSTER = item(SlowlightBlocks.BLOCKS.register("slowlight_shard_cluster",
            () -> new AmethystClusterBlock(7, 3, BlockBehaviour.Properties.ofFullCopy(Blocks.AMETHYST_CLUSTER)
                    .mapColor(MapColor.COLOR_BLUE).lightLevel(s -> 6))));

    public static final DeferredBlock<Block> STILL_SHARD_BLOCK = solid("still_shard_block", SlowlightDecor::stillGem);
    static {
        variants("still_shard_block", GEM_VARIANTS, SlowlightDecor::stillGem);
    }
    public static final DeferredBlock<Block> STILL_SHARD_CLUSTER = item(SlowlightBlocks.BLOCKS.register("still_shard_cluster",
            () -> new AmethystClusterBlock(7, 3, BlockBehaviour.Properties.ofFullCopy(Blocks.AMETHYST_CLUSTER)
                    .mapColor(MapColor.QUARTZ).lightLevel(s -> 2))));

    static {
        variants("gloamwood_planks", PLANK_VARIANTS, () -> BlockBehaviour.Properties.ofFullCopy(SlowlightBlocks.GLOAMWOOD_PLANKS.get()));
    }
    public static final DeferredBlock<Block> GLOAMWOOD_BOOKSHELF = solid("gloamwood_bookshelf",
            () -> BlockBehaviour.Properties.ofFullCopy(Blocks.BOOKSHELF).mapColor(MapColor.TERRACOTTA_LIGHT_BLUE));
    /** Chipped's bookshelf designs, each in Gloamwood. They join Chipped's own bookshelf group (its #chipped:bookshelf
     * tag) for the carpenter's table; without Chipped the stonecutter makes them from the Gloamwood Bookshelf. */
    public static final List<String> CHIPPED_SHELVES = List.of("ancient_cubed_oak_bookshelf", "ancient_oak_bookshelf",
            "ancient_oak_large_bookshelf", "ancient_vertical_oak_bookshelf", "botanical_oak_bookshelf",
            "botanical_oak_cubed_bookshelf", "botanical_oak_large_bookshelf", "cubed_oak_bookshelf", "cubed_oak_empty_bookshelf",
            "cubed_oak_spaced_bookshelf", "cubed_oak_webbed_bookshelf", "curvy_bookshelf", "dusty_bookshelf", "full_bookshelf",
            "historical_oak_bookshelf", "historical_oak_large_bookshelf", "mixed_cubed_oak_bookshelf", "mixed_oak_bookshelf",
            "mixed_oak_large_bookshelf", "oak_bookshelf", "oak_empty_bookshelf", "oak_large_bookshelf", "oak_lighted_bookshelf",
            "oak_lighted_large_bookshelf", "oak_spaced_bookshelf", "oak_spaced_large_bookshelf", "oak_webbed_bookshelf",
            "oak_webbed_large_bookshelf", "vertical_botanical_oak_bookshelf", "vertical_mixed_oak_bookshelf",
            "vertical_oak_bookshelf", "vertical_oak_lighted_bookshelf", "vertical_oak_webbed_bookshelf");
    static {
        for (String n : CHIPPED_SHELVES) {
            solid(gloamShelf(n), () -> BlockBehaviour.Properties.ofFullCopy(GLOAMWOOD_BOOKSHELF.get()));
        }
    }

    public static final DeferredBlock<Block> LAPSE_BLOCK = item(SlowlightBlocks.BLOCKS.register("lapse_block",
            () -> new LapseBlock(stillGem().noOcclusion()
                    .isViewBlocking((s, l, p) -> !s.getValue(LapseBlock.LAPSED))
                    .isSuffocating((s, l, p) -> !s.getValue(LapseBlock.LAPSED))
                    .isRedstoneConductor((s, l, p) -> false))));

    // Kiln foods for other mods' ingredients. Registered always, shown only when their mod is there; their recipes
    // carry a mod_loaded condition.
    public record CompatFood(String mod, DeferredItem<Item> item) {
    }

    public static final List<CompatFood> FOODS = List.of(
            food("twilightforest", "torchberry_preserve", 6, 0.6F),
            food("twilightforest", "cured_hydra_chop", 10, 1.0F),
            food("undergarden", "cured_gwibling", 6, 1.0F),
            food("undergarden", "ditchbulb_preserve", 6, 0.6F),
            new CompatFood("aether", SlowlightItems.ITEMS.register("white_apple_cider",
                    () -> new MeadItem(new Item.Properties().stacksTo(16).food(new FoodProperties.Builder()
                            .nutrition(4).saturationModifier(0.4F).alwaysEdible()
                            .effect(() -> new net.minecraft.world.effect.MobEffectInstance(
                                    net.minecraft.world.effect.MobEffects.REGENERATION, 5 * 20), 1.0F)
                            .build())))),
            food("aether", "blue_berry_preserve", 4, 0.5F),
            food("twilightforest", "raspberry_preserve", 6, 0.6F),
            food("twilightforest", "twilight_blueberry_preserve", 6, 0.6F),
            food("twilightforest", "blackberry_preserve", 6, 0.6F),
            food("twilightforest", "maloberry_preserve", 6, 0.6F),
            food("twilightforest", "blightberry_preserve", 6, 0.6F),
            food("twilightforest", "duskberry_preserve", 6, 0.6F),
            food("twilightforest", "skyberry_preserve", 6, 0.6F),
            food("twilightforest", "stingberry_preserve", 6, 0.6F),
            food("undergarden", "cured_dweller_steak", 10, 1.0F),
            food("undergarden", "cured_gloomper_leg", 8, 1.0F),
            food("undergarden", "candied_droopfruit", 5, 0.6F),
            food("undergarden", "dried_glitterkelp", 2, 0.5F),
            food("aether", "enchanted_berry_preserve", 8, 0.8F),
            food("aerialhell", "aerial_berry_preserve", 6, 0.6F),
            food("aerialhell", "ruby_aerial_berry_preserve", 6, 0.6F),
            food("aerialhell", "volucite_aerial_berry_preserve", 6, 0.6F),
            food("aerialhell", "vibrant_aerial_berry_preserve", 8, 0.8F),
            food("aerialhell", "shadow_fruit_preserve", 6, 0.6F),
            food("aerialhell", "candied_glowing_stick_fruit", 5, 0.6F),
            food("aerialhell", "stellar_rock_sugar", 2, 0.2F),
            food("aerialhell", "cured_phantom_meat", 7, 0.9F),
            food("aerialhell", "cured_turtle_meat", 7, 0.9F),
            food("aerialhell", "stellar_rusk", 5, 0.6F),
            // Aechor petals steeped two days: a light tea, and a moment of the Aether's slow fall.
            new CompatFood("aether", SlowlightItems.ITEMS.register("aechor_petal_tea",
                    () -> new MeadItem(new Item.Properties().stacksTo(16).food(new FoodProperties.Builder()
                            .nutrition(1).saturationModifier(0.2F).alwaysEdible()
                            .effect(() -> new net.minecraft.world.effect.MobEffectInstance(
                                    net.minecraft.world.effect.MobEffects.SLOW_FALLING, 30 * 20), 1.0F)
                            .build())))));

    private static BlockBehaviour.Properties slowlightGem() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.AMETHYST_BLOCK).mapColor(MapColor.COLOR_BLUE).lightLevel(s -> 6);
    }

    private static BlockBehaviour.Properties stillGem() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.AMETHYST_BLOCK).mapColor(MapColor.QUARTZ);
    }

    private static DeferredBlock<Block> solid(String name, Supplier<BlockBehaviour.Properties> props) {
        return item(SlowlightBlocks.BLOCKS.register(name, () -> new Block(props.get())));
    }

    /** Each variant: the block, its stairs and its slab, named the way Rechiseled names its own. */
    private static void variants(String base, List<String> names, Supplier<BlockBehaviour.Properties> props) {
        for (String v : names) {
            DeferredBlock<Block> block = solid(base + "_" + v, props);
            item(SlowlightBlocks.BLOCKS.register(base + "_" + v + "_stairs",
                    () -> new StairBlock(block.get().defaultBlockState(), props.get())));
            item(SlowlightBlocks.BLOCKS.register(base + "_" + v + "_slab", () -> new SlabBlock(props.get())));
        }
    }

    /** Chipped's name with Gloamwood for oak: oak_large_bookshelf -> gloamwood_large_bookshelf. Its plain oak_bookshelf
     * would be ours, so it is the plain one. tools/make_decor_data.py names them the same way. */
    public static String gloamShelf(String chipped) {
        if (chipped.equals("oak_bookshelf")) {
            return "plain_gloamwood_bookshelf";
        }
        return chipped.contains("oak") ? chipped.replace("oak", "gloamwood") : chipped.replace("bookshelf", "gloamwood_bookshelf");
    }

    private static DeferredBlock<Block> item(DeferredBlock<Block> block) {
        BLOCK_ITEMS.add(SlowlightItems.ITEMS.registerSimpleBlockItem(block));
        return block;
    }

    private static CompatFood food(String mod, String name, int nutrition, float saturation) {
        return new CompatFood(mod, SlowlightItems.ITEMS.registerSimpleItem(name, new Item.Properties()
                .food(new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build())));
    }

    static void fillTab(CreativeModeTab.Output output) {
        BLOCK_ITEMS.forEach(i -> output.accept(i.get()));
        for (CompatFood f : FOODS) {
            if (ModList.get().isLoaded(f.mod())) {
                output.accept(f.item().get());
            }
        }
    }

    /** Loads the class, so everything above is registered before the registries close. */
    static void init() {
    }

    private SlowlightDecor() {
    }
}

package dev.slowlight;

import dev.slowlight.block.GloamWeathering;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WeatheringCopper.WeatherState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Weathered Gloamwood: planks, stairs and slabs left out under the sky go Faded, then Weathered, then Silvered, as
 * copper goes Exposed, Weathered, Oxidized. Each step also comes Waxed, which stops it where it is. The steps and the
 * waxing live in data/neoforge/data_maps/block/{oxidizables,waxables}.json, written by tools/make_weathering.py.
 */
public final class SlowlightWeathering {
    /** The steps after plain Gloamwood, with the colour each shows on a map. */
    public static final List<String> PHASES = List.of("faded", "weathered", "silvered");
    private static final List<WeatherState> AGES = List.of(WeatherState.EXPOSED, WeatherState.WEATHERED, WeatherState.OXIDIZED);
    private static final List<MapColor> COLOURS = List.of(MapColor.COLOR_LIGHT_BLUE, MapColor.CLAY, MapColor.WOOL);

    public static final List<DeferredItem<BlockItem>> BLOCK_ITEMS = new ArrayList<>();
    private static final List<DeferredBlock<Block>> FLAMMABLE = new ArrayList<>();

    static {
        for (int i = 0; i < PHASES.size(); i++) {
            WeatherState age = AGES.get(i);
            BlockBehaviour.Properties props = BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).mapColor(COLOURS.get(i));
            String p = PHASES.get(i) + "_gloamwood";
            DeferredBlock<Block> planks = add(SlowlightBlocks.BLOCKS.register(p + "_planks", () -> new GloamWeathering.Planks(age, props)));
            add(SlowlightBlocks.BLOCKS.register(p + "_stairs", () -> new GloamWeathering.Stairs(age, planks.get().defaultBlockState(), props)));
            add(SlowlightBlocks.BLOCKS.register(p + "_slab", () -> new GloamWeathering.Slab(age, props)));
        }
        // Waxed: the same four looks, held. Plain Gloamwood waxed too, for builders who want it never to change.
        for (int i = 0; i <= PHASES.size(); i++) {
            String p = "waxed_" + (i == 0 ? "" : PHASES.get(i - 1) + "_") + "gloamwood";
            BlockBehaviour.Properties props = BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS)
                    .mapColor(i == 0 ? MapColor.TERRACOTTA_LIGHT_BLUE : COLOURS.get(i - 1));
            DeferredBlock<Block> planks = add(SlowlightBlocks.BLOCKS.register(p + "_planks", () -> new Block(props)));
            add(SlowlightBlocks.BLOCKS.register(p + "_stairs", () -> new StairBlock(planks.get().defaultBlockState(), props)));
            add(SlowlightBlocks.BLOCKS.register(p + "_slab", () -> new SlabBlock(props)));
        }
    }

    private static DeferredBlock<Block> add(DeferredBlock<Block> block) {
        BLOCK_ITEMS.add(SlowlightItems.ITEMS.registerSimpleBlockItem(block));
        FLAMMABLE.add(block);
        return block;
    }

    /** Burns like the planks they were. Called from Gloamwood.setup. */
    static void setFlammable(FireBlock fire) {
        FLAMMABLE.forEach(b -> fire.setFlammable(b.get(), 5, 20));
    }

    static void fillTab(CreativeModeTab.Output output) {
        BLOCK_ITEMS.forEach(i -> output.accept(i.get()));
    }

    /** Loads the class, so everything above is registered before the registries close. */
    static void init() {
    }

    private SlowlightWeathering() {
    }
}

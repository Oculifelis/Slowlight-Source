package dev.slowlight;

import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/**
 * Gloamwood, the sniffer's tree: short and spreading, pale silver-blue bark under dark leaves, and a sapling that
 * only grows at night. Its wood is a whole vanilla set; this class holds what the set shares (the block set and wood
 * types the doors, signs and gates take their sounds and textures from, the tree grower). The boat type is in {@link GloamwoodBoat}.
 */
public final class Gloamwood {
    public static final String NAME = Slowlight.MOD_ID + ":gloamwood";

    public static final BlockSetType SET = BlockSetType.register(new BlockSetType(NAME));
    public static final WoodType WOOD = WoodType.register(new WoodType(NAME, SET));

    /** The tree itself is data: data/slowlight/worldgen/configured_feature/gloamwood.json. */
    public static final ResourceKey<ConfiguredFeature<?, ?>> TREE = ResourceKey.create(Registries.CONFIGURED_FEATURE,
            ResourceLocation.fromNamespaceAndPath(Slowlight.MOD_ID, "gloamwood"));
    public static final TreeGrower GROWER = new TreeGrower(NAME, Optional.empty(), Optional.of(TREE), Optional.empty());

    /** Fire and flower pots: vanilla keeps both as tables, filled once the blocks exist. */
    public static void setup() {
        FireBlock fire = (FireBlock) Blocks.FIRE;
        for (Supplier<? extends Block> b : java.util.List.of(SlowlightBlocks.GLOAMWOOD_LOG, SlowlightBlocks.GLOAMWOOD_WOOD,
                SlowlightBlocks.STRIPPED_GLOAMWOOD_LOG, SlowlightBlocks.STRIPPED_GLOAMWOOD_WOOD)) {
            fire.setFlammable(b.get(), 5, 5);
        }
        for (Supplier<? extends Block> b : java.util.List.of(SlowlightBlocks.GLOAMWOOD_PLANKS, SlowlightBlocks.GLOAMWOOD_STAIRS,
                SlowlightBlocks.GLOAMWOOD_SLAB, SlowlightBlocks.GLOAMWOOD_FENCE, SlowlightBlocks.GLOAMWOOD_FENCE_GATE)) {
            fire.setFlammable(b.get(), 5, 20);
        }
        fire.setFlammable(SlowlightBlocks.GLOAMWOOD_LEAVES.get(), 30, 60);
        SlowlightWeathering.setFlammable(fire);
        ((FlowerPotBlock) Blocks.FLOWER_POT).addPlant(SlowlightBlocks.GLOAMWOOD_SAPLING.getId(),
                SlowlightBlocks.POTTED_GLOAMWOOD_SAPLING);
    }

    private Gloamwood() {
    }
}

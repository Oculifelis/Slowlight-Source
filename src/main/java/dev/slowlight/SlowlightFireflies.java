package dev.slowlight;

import dev.slowlight.item.MeadItem;
import java.util.List;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Colorfly compat: Gloamwood Firefly Lanterns around a greenfly or bluefly bottle, and Firefly Mead. Both lanterns sit in
 * #slowlight:night_lights, so one hung just above a kiln keeps night-only recipes ageing by day (AgeingCondition).
 * Registered always, listed in the creative tab only when Colorfly ("color") is there; their recipes carry a
 * mod_loaded condition.
 */
public final class SlowlightFireflies {
    public static final String COLORFLY = "color";

    public static final DeferredBlock<Block> GREENFLY_LANTERN = lantern("greenfly_gloamwood_lantern", MapColor.COLOR_LIGHT_GREEN);
    public static final DeferredBlock<Block> BLUEFLY_LANTERN = lantern("bluefly_gloamwood_lantern", MapColor.COLOR_LIGHT_BLUE);
    public static final DeferredItem<BlockItem> GREENFLY_LANTERN_ITEM = SlowlightItems.ITEMS.registerSimpleBlockItem(GREENFLY_LANTERN);
    public static final DeferredItem<BlockItem> BLUEFLY_LANTERN_ITEM = SlowlightItems.ITEMS.registerSimpleBlockItem(BLUEFLY_LANTERN);

    /** Mead aged a few nights beside a Colorfly bottle: Night Vision for three minutes and a short glow. */
    public static final DeferredItem<Item> FIREFLY_MEAD = SlowlightItems.ITEMS.register("firefly_mead",
            () -> new MeadItem(new Item.Properties().stacksTo(16).food(new FoodProperties.Builder()
                    .nutrition(6).saturationModifier(0.6F).alwaysEdible()
                    .effect(() -> new MobEffectInstance(MobEffects.NIGHT_VISION, 180 * 20), 1.0F)
                    .effect(() -> new MobEffectInstance(MobEffects.GLOWING, 30 * 20), 1.0F)
                    .build())));

    private static DeferredBlock<Block> lantern(String name, MapColor color) {
        return SlowlightBlocks.BLOCKS.register(name, () -> new LanternBlock(BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(1.5F)
                .sound(SoundType.LANTERN)
                .lightLevel(s -> 12)
                .noOcclusion()
                .pushReaction(PushReaction.DESTROY)));
    }

    static void fillTab(CreativeModeTab.Output output) {
        if (ModList.get().isLoaded(COLORFLY)) {
            for (var i : List.of(GREENFLY_LANTERN_ITEM, BLUEFLY_LANTERN_ITEM, FIREFLY_MEAD)) {
                output.accept(i.get());
            }
        }
    }

    /** Loads the class, so everything above is registered before the registries close. */
    static void init() {
    }

    private SlowlightFireflies() {
    }
}

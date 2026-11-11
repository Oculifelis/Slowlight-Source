package dev.slowlight;

import dev.slowlight.item.MeadItem;
import dev.slowlight.item.SlowlightDiscItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.HangingSignItem;
import net.minecraft.world.item.SignItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SmithingTemplateItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SlowlightItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Slowlight.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Slowlight.MOD_ID);

    public static final DeferredItem<BlockItem> PATIENT_KILN =
            ITEMS.registerSimpleBlockItem(SlowlightBlocks.PATIENT_KILN);
    public static final DeferredItem<BlockItem> LONG_KILN =
            ITEMS.registerSimpleBlockItem(SlowlightBlocks.LONG_KILN);
    public static final DeferredItem<BlockItem> SLOWLIGHT_LANTERN =
            ITEMS.registerSimpleBlockItem(SlowlightBlocks.SLOWLIGHT_LANTERN);

    public static final DeferredItem<Item> DUSKBLOOM_SEEDS = ITEMS.register("duskbloom_seeds",
            () -> new ItemNameBlockItem(SlowlightBlocks.DUSKBLOOM_CROP.get(), new Item.Properties()));
    public static final DeferredItem<Item> DUSKBLOOM_PETALS = ITEMS.registerSimpleItem("duskbloom_petals");
    public static final DeferredItem<Item> DRIED_DUSKBLOOM_PETALS =
            ITEMS.registerSimpleItem("dried_duskbloom_petals");

    // Dried petals steeped in water: a minute of night sight, and the bottle back.
    public static final DeferredItem<Item> DUSKBLOOM_TEA = ITEMS.register("duskbloom_tea",
            () -> new MeadItem(new Item.Properties()
                    .stacksTo(16)
                    .food(new FoodProperties.Builder()
                            .nutrition(1)
                            .saturationModifier(0.2F)
                            .alwaysEdible()
                            .effect(() -> new MobEffectInstance(MobEffects.NIGHT_VISION, 60 * 20), 1.0F)
                            .build())));

    public static final DeferredItem<Item> DUSKBLOOM_JAM = ITEMS.registerSimpleItem("duskbloom_jam",
            new Item.Properties()
                    .food(new FoodProperties.Builder()
                            .nutrition(6)
                            .saturationModifier(0.6F)
                            .build()));

    public static final DeferredItem<Item> SLOWLIGHT_SHARD = ITEMS.registerSimpleItem("slowlight_shard",
            new Item.Properties().rarity(Rarity.UNCOMMON));

    // The Patience armour trim's template. The pattern and the shard's trim material are data, in
    // data/slowlight/trim_pattern/ and trim_material/; this is only the item that carries the pattern.
    public static final DeferredItem<Item> PATIENCE_TRIM_TEMPLATE = ITEMS.register(
            "patience_armor_trim_smithing_template",
            () -> SmithingTemplateItem.createArmorTrimTemplate(
                    ResourceLocation.fromNamespaceAndPath(Slowlight.MOD_ID, "patience")));

    // Found in kiln dig sites and, rarely, in trail ruins and desert wells; stamps an hourglass on a decorated pot.
    public static final DeferredItem<Item> PATIENCE_POTTERY_SHERD = ITEMS.registerSimpleItem("patience_pottery_sherd",
            new Item.Properties().rarity(Rarity.UNCOMMON));

    // ---- branch rest: Sandglass, Keepsake Urn, Ember Kiln, Still Shard (begin) ----
    public static final DeferredItem<BlockItem> SANDGLASS = ITEMS.registerSimpleBlockItem(SlowlightBlocks.SANDGLASS);
    public static final DeferredItem<BlockItem> KEEPSAKE_URN = ITEMS.registerSimpleBlockItem(SlowlightBlocks.KEEPSAKE_URN);
    public static final DeferredItem<BlockItem> EMBER_KILN = ITEMS.registerSimpleBlockItem(SlowlightBlocks.EMBER_KILN);
    public static final DeferredItem<BlockItem> SUNDIAL = ITEMS.registerSimpleBlockItem(SlowlightBlocks.SUNDIAL);

    // An ender pearl left ten days under the End's empty sky goes quiet and still: a pale pearl trim material.
    public static final DeferredItem<Item> STILL_SHARD = ITEMS.registerSimpleItem("still_shard",
            new Item.Properties().rarity(Rarity.UNCOMMON));
    // ---- branch rest (end) ----

    public static final DeferredItem<Item> MEAD = ITEMS.register("mead",
            () -> new MeadItem(new Item.Properties()
                    .stacksTo(16)
                    .food(new FoodProperties.Builder()
                            .nutrition(6)
                            .saturationModifier(0.6F)
                            .alwaysEdible()
                            .build())));

    public static final DeferredItem<Item> PRESERVED_BERRIES = ITEMS.registerSimpleItem("preserved_berries",
            new Item.Properties()
                    .food(new FoodProperties.Builder()
                            .nutrition(4)
                            .saturationModifier(0.5F)
                            .build()));

    // Apples left four days: cider. A little warmth that lingers.
    public static final DeferredItem<Item> CIDER = ITEMS.register("cider",
            () -> new MeadItem(new Item.Properties()
                    .stacksTo(16)
                    .food(new FoodProperties.Builder()
                            .nutrition(4)
                            .saturationModifier(0.4F)
                            .alwaysEdible()
                            .effect(() -> new MobEffectInstance(MobEffects.REGENERATION, 5 * 20), 1.0F)
                            .build())));

    // Cider with dried Duskbloom petals in it: the warmth, and two minutes of night sight.
    public static final DeferredItem<Item> DUSK_CIDER = ITEMS.register("dusk_cider",
            () -> new MeadItem(new Item.Properties()
                    .stacksTo(16)
                    .food(new FoodProperties.Builder()
                            .nutrition(4)
                            .saturationModifier(0.4F)
                            .alwaysEdible()
                            .effect(() -> new MobEffectInstance(MobEffects.REGENERATION, 5 * 20), 1.0F)
                            .effect(() -> new MobEffectInstance(MobEffects.NIGHT_VISION, 120 * 20), 1.0F)
                            .build())));

    // Cooked fish cured for three days keeps you fed longer than fresh.
    public static final DeferredItem<Item> CURED_COD = ITEMS.registerSimpleItem("cured_cod",
            new Item.Properties().food(new FoodProperties.Builder().nutrition(5).saturationModifier(1.0F).build()));
    public static final DeferredItem<Item> CURED_SALMON = ITEMS.registerSimpleItem("cured_salmon",
            new Item.Properties().food(new FoodProperties.Builder().nutrition(6).saturationModifier(1.0F).build()));

    // Gloamwood and its wood set.
    public static final DeferredItem<BlockItem> GLOAMWOOD_SAPLING = ITEMS.registerSimpleBlockItem(SlowlightBlocks.GLOAMWOOD_SAPLING);
    public static final DeferredItem<BlockItem> GLOAMWOOD_LEAVES = ITEMS.registerSimpleBlockItem(SlowlightBlocks.GLOAMWOOD_LEAVES);
    public static final DeferredItem<BlockItem> GLOAMWOOD_LOG = ITEMS.registerSimpleBlockItem(SlowlightBlocks.GLOAMWOOD_LOG);
    public static final DeferredItem<BlockItem> GLOAMWOOD_WOOD = ITEMS.registerSimpleBlockItem(SlowlightBlocks.GLOAMWOOD_WOOD);
    public static final DeferredItem<BlockItem> STRIPPED_GLOAMWOOD_LOG =
            ITEMS.registerSimpleBlockItem(SlowlightBlocks.STRIPPED_GLOAMWOOD_LOG);
    public static final DeferredItem<BlockItem> STRIPPED_GLOAMWOOD_WOOD =
            ITEMS.registerSimpleBlockItem(SlowlightBlocks.STRIPPED_GLOAMWOOD_WOOD);
    public static final DeferredItem<BlockItem> GLOAMWOOD_PLANKS = ITEMS.registerSimpleBlockItem(SlowlightBlocks.GLOAMWOOD_PLANKS);
    public static final DeferredItem<BlockItem> GLOAMWOOD_STAIRS = ITEMS.registerSimpleBlockItem(SlowlightBlocks.GLOAMWOOD_STAIRS);
    public static final DeferredItem<BlockItem> GLOAMWOOD_SLAB = ITEMS.registerSimpleBlockItem(SlowlightBlocks.GLOAMWOOD_SLAB);
    public static final DeferredItem<BlockItem> GLOAMWOOD_FENCE = ITEMS.registerSimpleBlockItem(SlowlightBlocks.GLOAMWOOD_FENCE);
    public static final DeferredItem<BlockItem> GLOAMWOOD_FENCE_GATE =
            ITEMS.registerSimpleBlockItem(SlowlightBlocks.GLOAMWOOD_FENCE_GATE);
    public static final DeferredItem<Item> GLOAMWOOD_DOOR = ITEMS.register("gloamwood_door",
            () -> new DoubleHighBlockItem(SlowlightBlocks.GLOAMWOOD_DOOR.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> GLOAMWOOD_TRAPDOOR = ITEMS.registerSimpleBlockItem(SlowlightBlocks.GLOAMWOOD_TRAPDOOR);
    public static final DeferredItem<BlockItem> GLOAMWOOD_BUTTON = ITEMS.registerSimpleBlockItem(SlowlightBlocks.GLOAMWOOD_BUTTON);
    public static final DeferredItem<BlockItem> GLOAMWOOD_PRESSURE_PLATE =
            ITEMS.registerSimpleBlockItem(SlowlightBlocks.GLOAMWOOD_PRESSURE_PLATE);
    public static final DeferredItem<Item> GLOAMWOOD_SIGN = ITEMS.register("gloamwood_sign",
            () -> new SignItem(new Item.Properties().stacksTo(16), SlowlightBlocks.GLOAMWOOD_SIGN.get(),
                    SlowlightBlocks.GLOAMWOOD_WALL_SIGN.get()));
    public static final DeferredItem<Item> GLOAMWOOD_HANGING_SIGN = ITEMS.register("gloamwood_hanging_sign",
            () -> new HangingSignItem(SlowlightBlocks.GLOAMWOOD_HANGING_SIGN.get(),
                    SlowlightBlocks.GLOAMWOOD_WALL_HANGING_SIGN.get(), new Item.Properties().stacksTo(16)));
    public static final DeferredItem<Item> GLOAMWOOD_BOAT = ITEMS.register("gloamwood_boat",
            () -> new BoatItem(false, GloamwoodBoat.BOAT.getValue(), new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> GLOAMWOOD_CHEST_BOAT = ITEMS.register("gloamwood_chest_boat",
            () -> new BoatItem(true, GloamwoodBoat.BOAT.getValue(), new Item.Properties().stacksTo(1)));

    // Lanternfruit. Its peel is what is left in the crafting grid whenever a fruit is used there (taking its seeds,
    // say), so the peel is registered before the fruit.
    public static final DeferredItem<Item> LANTERNFRUIT_PEEL = ITEMS.registerSimpleItem("lanternfruit_peel");
    public static final DeferredItem<Item> LANTERNFRUIT = ITEMS.register("lanternfruit",
            () -> new ItemNameBlockItem(SlowlightBlocks.LANTERNFRUIT.get(), new Item.Properties()
                    .craftRemainder(LANTERNFRUIT_PEEL.get())
                    .food(new FoodProperties.Builder().nutrition(3).saturationModifier(0.1F).build())));
    public static final DeferredItem<Item> LANTERNFRUIT_SEEDS = ITEMS.registerSimpleItem("lanternfruit_seeds");
    public static final DeferredItem<Item> CANDIED_LANTERNFRUIT = ITEMS.registerSimpleItem("candied_lanternfruit",
            new Item.Properties().food(new FoodProperties.Builder().nutrition(5).saturationModifier(0.6F).build()));
    public static final DeferredItem<Item> LANTERNFRUIT_PRESERVE = ITEMS.registerSimpleItem("lanternfruit_preserve",
            new Item.Properties().food(new FoodProperties.Builder().nutrition(7).saturationModifier(0.7F).build()));
    // Crushed fruit and sugar in a bottle, waiting to become wine.
    public static final DeferredItem<Item> LANTERNFRUIT_MUST = ITEMS.registerSimpleItem("lanternfruit_must",
            new Item.Properties().stacksTo(16));
    // Fifteen days in a Long Kiln: three minutes of night sight, and a moment of heavy legs.
    public static final DeferredItem<Item> LANTERNWINE = ITEMS.register("lanternwine",
            () -> new MeadItem(new Item.Properties()
                    .stacksTo(16)
                    .food(new FoodProperties.Builder()
                            .nutrition(3)
                            .saturationModifier(0.4F)
                            .alwaysEdible()
                            .effect(() -> new MobEffectInstance(MobEffects.NIGHT_VISION, 180 * 20), 1.0F)
                            .effect(() -> new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10 * 20), 1.0F)
                            .build())));
    public static final DeferredItem<BlockItem> LANTERNFRUIT_LAMP = ITEMS.registerSimpleBlockItem(SlowlightBlocks.LANTERNFRUIT_LAMP);

    // Three records, each slowly pressed out of a vanilla one in the Patient Kiln. The songs themselves
    // live in data/slowlight/jukebox_song/; these are only the discs that point at them.
    public static final DeferredItem<Item> MUSIC_DISC_PATIENT_LIGHT = disc("patient_light");
    public static final DeferredItem<Item> MUSIC_DISC_A_WEEK_IN = disc("a_week_in");
    public static final DeferredItem<Item> MUSIC_DISC_LANTERN_HOURS = disc("lantern_hours");

    private static DeferredItem<Item> disc(String name) {
        ResourceKey<JukeboxSong> song = ResourceKey.create(Registries.JUKEBOX_SONG,
                ResourceLocation.fromNamespaceAndPath(Slowlight.MOD_ID, name));
        return ITEMS.register("music_disc_" + name, () -> new SlowlightDiscItem(new Item.Properties()
                .stacksTo(1)
                .rarity(Rarity.RARE)
                .jukeboxPlayable(song)));
    }

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("slowlight",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.slowlight"))
                    .icon(() -> SLOWLIGHT_LANTERN.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        output.accept(PATIENT_KILN.get());
                        output.accept(LONG_KILN.get());
                        output.accept(SLOWLIGHT_LANTERN.get());
                        output.accept(SLOWLIGHT_SHARD.get());
                        output.accept(PATIENCE_TRIM_TEMPLATE.get());
                        output.accept(PATIENCE_POTTERY_SHERD.get());
                        output.accept(DUSKBLOOM_SEEDS.get());
                        output.accept(DUSKBLOOM_PETALS.get());
                        output.accept(DRIED_DUSKBLOOM_PETALS.get());
                        output.accept(DUSKBLOOM_TEA.get());
                        output.accept(DUSKBLOOM_JAM.get());
                        output.accept(GLOAMWOOD_SAPLING.get());
                        output.accept(GLOAMWOOD_LEAVES.get());
                        output.accept(GLOAMWOOD_LOG.get());
                        output.accept(GLOAMWOOD_WOOD.get());
                        output.accept(STRIPPED_GLOAMWOOD_LOG.get());
                        output.accept(STRIPPED_GLOAMWOOD_WOOD.get());
                        output.accept(GLOAMWOOD_PLANKS.get());
                        output.accept(GLOAMWOOD_STAIRS.get());
                        output.accept(GLOAMWOOD_SLAB.get());
                        SlowlightWeathering.fillTab(output);
                        output.accept(GLOAMWOOD_FENCE.get());
                        output.accept(GLOAMWOOD_FENCE_GATE.get());
                        output.accept(GLOAMWOOD_DOOR.get());
                        output.accept(GLOAMWOOD_TRAPDOOR.get());
                        output.accept(GLOAMWOOD_PRESSURE_PLATE.get());
                        output.accept(GLOAMWOOD_BUTTON.get());
                        output.accept(GLOAMWOOD_SIGN.get());
                        output.accept(GLOAMWOOD_HANGING_SIGN.get());
                        output.accept(GLOAMWOOD_BOAT.get());
                        output.accept(GLOAMWOOD_CHEST_BOAT.get());
                        output.accept(LANTERNFRUIT.get());
                        output.accept(LANTERNFRUIT_SEEDS.get());
                        output.accept(LANTERNFRUIT_PEEL.get());
                        output.accept(LANTERNFRUIT_LAMP.get());
                        output.accept(CANDIED_LANTERNFRUIT.get());
                        output.accept(LANTERNFRUIT_PRESERVE.get());
                        output.accept(LANTERNFRUIT_MUST.get());
                        output.accept(LANTERNWINE.get());
                        output.accept(MEAD.get());
                        output.accept(PRESERVED_BERRIES.get());
                        output.accept(CIDER.get());
                        output.accept(DUSK_CIDER.get());
                        output.accept(CURED_COD.get());
                        output.accept(CURED_SALMON.get());
                        // branch rest
                        output.accept(EMBER_KILN.get());
                        output.accept(SUNDIAL.get());
                        output.accept(SANDGLASS.get());
                        output.accept(KEEPSAKE_URN.get());
                        output.accept(STILL_SHARD.get());
                        // branch rest (end)
                        SlowlightDecor.fillTab(output);
                        SlowlightCrafts.fillTab(output, params);
                        SlowlightWays.fillTab(output);
                        SlowlightNights.fillTab(output);
                        output.accept(MUSIC_DISC_PATIENT_LIGHT.get());
                        output.accept(MUSIC_DISC_A_WEEK_IN.get());
                        output.accept(MUSIC_DISC_LANTERN_HOURS.get());
                    })
                    .build());

    private SlowlightItems() {
    }
}

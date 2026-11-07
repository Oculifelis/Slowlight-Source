package dev.slowlight;

import com.google.common.collect.ImmutableSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.neoforge.event.village.WandererTradesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The Potter, a villager who works at a Patient Kiln. Clay and bricks first, then terracotta and aged goods, and at
 * the top the things only patience makes: the Patience sherd and its trim template.
 */
@EventBusSubscriber(modid = Slowlight.MOD_ID)
public final class SlowlightVillagers {
    public static final DeferredRegister<PoiType> POI_TYPES = DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, Slowlight.MOD_ID);
    public static final DeferredRegister<VillagerProfession> PROFESSIONS =
            DeferredRegister.create(Registries.VILLAGER_PROFESSION, Slowlight.MOD_ID);

    public static final ResourceKey<PoiType> POTTER_POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE,
            ResourceLocation.fromNamespaceAndPath(Slowlight.MOD_ID, "potter"));

    public static final DeferredHolder<PoiType, PoiType> POTTER_POI_TYPE = POI_TYPES.register("potter", () -> {
        // Every state of the Patient Kiln, open or sealed, is the Potter's workstation.
        Set<BlockState> states = new HashSet<>(SlowlightBlocks.PATIENT_KILN.get().getStateDefinition().getPossibleStates());
        return new PoiType(ImmutableSet.copyOf(states), 1, 1);
    });

    public static final DeferredHolder<VillagerProfession, VillagerProfession> POTTER = PROFESSIONS.register("potter",
            () -> new VillagerProfession("potter", holder -> holder.is(POTTER_POI), holder -> holder.is(POTTER_POI),
                    ImmutableSet.of(), ImmutableSet.of(), SoundEvents.VILLAGER_WORK_MASON));

    private SlowlightVillagers() {
    }

    @SubscribeEvent
    static void trades(VillagerTradesEvent event) {
        if (event.getType() != POTTER.get()) {
            return;
        }
        var t = event.getTrades();
        // Novice: buys clay, sells bricks.
        t.get(1).add(buy(Items.CLAY_BALL, 10, 16, 2));
        t.get(1).add(sell(Items.BRICK, 10, 1, 16, 1));
        // Apprentice: terracotta in a few colours, and buys honey for the Amber Draught.
        t.get(2).add(sell(Items.TERRACOTTA, 4, 1, 16, 5));
        t.get(2).add(sell(Items.ORANGE_TERRACOTTA, 4, 1, 16, 5));
        t.get(2).add(buy(Items.HONEY_BOTTLE, 4, 12, 10));
        // Journeyman: the first aged goods.
        t.get(3).add(sell(SlowlightItems.MEAD.get(), 1, 3, 12, 10));
        t.get(3).add(sell(SlowlightItems.PRESERVED_BERRIES.get(), 1, 2, 12, 10));
        t.get(3).add(buy(SlowlightItems.DUSKBLOOM_PETALS.get(), 8, 12, 20));
        // Expert: an old sherd from somewhere, and cider.
        t.get(4).add((trader, random) -> {
            List<Item> sherds = List.of(Items.ARMS_UP_POTTERY_SHERD, Items.BREWER_POTTERY_SHERD, Items.HEART_POTTERY_SHERD,
                    Items.HOWL_POTTERY_SHERD, Items.SHEAF_POTTERY_SHERD, Items.FRIEND_POTTERY_SHERD);
            return new MerchantOffer(new ItemCost(Items.EMERALD, 6), new ItemStack(sherds.get(random.nextInt(sherds.size()))), 4, 15, 0.05F);
        });
        t.get(4).add(sell(SlowlightItems.CIDER.get(), 1, 3, 12, 15));
        t.get(4).add(sell(SlowlightItems.CURED_SALMON.get(), 3, 2, 12, 15));
        // Master: what only patience makes.
        t.get(5).add(sell(SlowlightItems.PATIENCE_POTTERY_SHERD.get(), 1, 12, 3, 30));
        t.get(5).add((trader, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, 24),
                Optional.of(new ItemCost(SlowlightItems.SLOWLIGHT_SHARD.get(), 1)),
                new ItemStack(SlowlightItems.PATIENCE_TRIM_TEMPLATE.get()), 2, 30, 0.05F));
    }

    /** The wandering trader carries a little of Slowlight too: seeds and a seedling, rarely a sherd or a shard. */
    @SubscribeEvent
    static void wandererTrades(WandererTradesEvent event) {
        event.getGenericTrades().add(sell(SlowlightItems.DUSKBLOOM_SEEDS.get(), 1, 1, 12, 1));
        event.getGenericTrades().add(sell(SlowlightItems.GLOAMWOOD_SAPLING.get(), 1, 5, 8, 1));
        event.getRareTrades().add(sell(SlowlightItems.PATIENCE_POTTERY_SHERD.get(), 1, 8, 3, 1));
        event.getRareTrades().add(sell(SlowlightItems.SLOWLIGHT_SHARD.get(), 1, 6, 4, 1));
    }

    /** The villager buys {@code count} of an item for one emerald. */
    private static VillagerTrades.ItemListing buy(ItemLike item, int count, int uses, int xp) {
        return (trader, random) -> new MerchantOffer(new ItemCost(item, count), new ItemStack(Items.EMERALD), uses, xp, 0.05F);
    }

    /** The villager sells {@code count} of an item for {@code emeralds}. */
    private static VillagerTrades.ItemListing sell(ItemLike item, int count, int emeralds, int uses, int xp) {
        return (trader, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, emeralds), new ItemStack(item, count), uses, xp, 0.05F);
    }
}

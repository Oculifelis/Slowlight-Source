package dev.slowlight;

import com.mojang.serialization.Codec;
import dev.slowlight.locker.KeyItem;
import dev.slowlight.locker.KeyedRecipe;
import dev.slowlight.locker.LockItem;
import dev.slowlight.locker.LockerBlock;
import dev.slowlight.locker.LockerBlockEntity;
import dev.slowlight.locker.LockerMenu;
import dev.slowlight.locker.LockerSize;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/**
 * Lockers, after the BetterStorage lockers by copygirl (MIT), written fresh for Slowlight.
 *
 * <p>A locker is two blocks tall and opens with a door. Two lockers side by side facing the same way join into one
 * wide locker, as chests do. The Reinforced Locker holds more and takes a Lock; a locked locker opens only for the
 * matching Key, cannot be broken by hand, and lets no hopper or pipe in or out. How much each locker holds is a data
 * map ({@code data/slowlight/data_maps/block/locker_size.json}), so a pack can change it.
 */
public final class SlowlightLockers {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Slowlight.MOD_ID);

    /** The lock a key opens, or the lock a lock fits; one number shared by a key and every lock made from it. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> LOCK_ID =
            SlowlightCrafts.COMPONENTS.registerComponentType("lock_id", b -> b.persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** Columns and rows of a locker's inventory, read from data. */
    public static final DataMapType<Block, LockerSize> LOCKER_SIZE = DataMapType.builder(
            ResourceLocation.fromNamespaceAndPath(Slowlight.MOD_ID, "locker_size"), Registries.BLOCK, LockerSize.CODEC).build();

    public static final DeferredBlock<LockerBlock> LOCKER = SlowlightBlocks.BLOCKS.register("locker",
            () -> new LockerBlock(false, BlockBehaviour.Properties.ofFullCopy(Blocks.CHEST)
                    .mapColor(MapColor.TERRACOTTA_BLUE).strength(2.5F).sound(SoundType.WOOD)
                    .noOcclusion().pushReaction(PushReaction.BLOCK)));
    public static final DeferredBlock<LockerBlock> REINFORCED_LOCKER = SlowlightBlocks.BLOCKS.register("reinforced_locker",
            () -> new LockerBlock(true, BlockBehaviour.Properties.ofFullCopy(Blocks.CHEST)
                    .mapColor(MapColor.TERRACOTTA_BLUE).strength(8.0F, 20.0F).sound(SoundType.WOOD)
                    .noOcclusion().pushReaction(PushReaction.BLOCK)));
    public static final DeferredItem<BlockItem> LOCKER_ITEM = SlowlightItems.ITEMS.registerSimpleBlockItem(LOCKER);
    public static final DeferredItem<BlockItem> REINFORCED_LOCKER_ITEM = SlowlightItems.ITEMS.registerSimpleBlockItem(REINFORCED_LOCKER);

    public static final DeferredItem<KeyItem> KEY = SlowlightItems.ITEMS.register("key",
            () -> new KeyItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<LockItem> LOCK = SlowlightItems.ITEMS.register("lock",
            () -> new LockItem(new Item.Properties().stacksTo(16)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LockerBlockEntity>> LOCKER_BE =
            SlowlightBlockEntities.BLOCK_ENTITIES.register("locker", () -> BlockEntityType.Builder
                    .of(LockerBlockEntity::new, LOCKER.get(), REINFORCED_LOCKER.get()).build(null));

    public static final DeferredHolder<MenuType<?>, MenuType<LockerMenu>> LOCKER_MENU = MENUS.register("locker",
            () -> IMenuTypeExtension.create(LockerMenu::client));

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<KeyedRecipe>> KEYED =
            SlowlightRecipes.RECIPE_SERIALIZERS.register("keyed", KeyedRecipe.Serializer::new);

    private SlowlightLockers() {
    }

    /** Loads this class so its entries join the registers before they fire. */
    static void init() {
    }

    static void registerDataMaps(RegisterDataMapTypesEvent event) {
        event.register(LOCKER_SIZE);
    }

    static void fillTab(CreativeModeTab.Output output) {
        output.accept(LOCKER_ITEM.get());
        output.accept(REINFORCED_LOCKER_ITEM.get());
        output.accept(KEY.get());
        output.accept(LOCK.get());
    }
}

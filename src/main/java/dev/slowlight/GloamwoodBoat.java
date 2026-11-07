package dev.slowlight;

import java.util.function.Supplier;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;

/**
 * Gloamwood's boat type, added to vanilla's Boat.Type by NeoForge's enum extension (META-INF/enumextensions.json
 * names this field). The renderer then finds textures/entity/boat/gloamwood.png and chest_boat/gloamwood.png by
 * itself. Kept apart from {@link Gloamwood} because Boat.Type loads this class while it is still being built, and
 * this class should pull in nothing but suppliers.
 */
public final class GloamwoodBoat {
    public static final EnumProxy<Boat.Type> BOAT = new EnumProxy<>(Boat.Type.class,
            (Supplier<Block>) () -> SlowlightBlocks.GLOAMWOOD_PLANKS.get(),
            Gloamwood.NAME,
            (Supplier<Item>) () -> SlowlightItems.GLOAMWOOD_BOAT.get(),
            (Supplier<Item>) () -> SlowlightItems.GLOAMWOOD_CHEST_BOAT.get(),
            (Supplier<Item>) () -> Items.STICK,
            false);

    private GloamwoodBoat() {
    }
}

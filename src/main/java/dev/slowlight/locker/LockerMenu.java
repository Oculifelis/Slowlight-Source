package dev.slowlight.locker;

import dev.slowlight.SlowlightLockers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** A chest-style menu of any width and height: the locker's grid on top, the player's inventory below. */
public class LockerMenu extends AbstractContainerMenu {
    private final Container container;
    private final int columns;
    private final int rows;
    private final int width;
    private final int height;

    public LockerMenu(int id, Inventory inventory, Container container, int columns, int rows) {
        super(SlowlightLockers.LOCKER_MENU.get(), id);
        checkContainerSize(container, columns * rows);
        this.container = container;
        this.columns = columns;
        this.rows = rows;
        this.width = 14 + 18 * Math.max(columns, 9);
        this.height = 114 + rows * 18;
        container.startOpen(inventory.player);
        int x0 = (width - columns * 18) / 2 + 1;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < columns; c++) {
                addSlot(new Slot(container, c + r * columns, x0 + c * 18, 18 + r * 18));
            }
        }
        int px = (width - 162) / 2 + 1;
        int py = 31 + rows * 18;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new Slot(inventory, c + r * 9 + 9, px + c * 18, py + r * 18));
            }
        }
        for (int c = 0; c < 9; c++) {
            addSlot(new Slot(inventory, c, px + c * 18, py + 58));
        }
    }

    /** The client's copy, sized from the numbers the server sent with the open request. */
    public static LockerMenu client(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        int columns = buf.readVarInt();
        int rows = buf.readVarInt();
        return new LockerMenu(id, inventory, new SimpleContainer(columns * rows), columns, rows);
    }

    public Container container() {
        return container;
    }

    public int columns() {
        return columns;
    }

    public int rows() {
        return rows;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            int own = columns * rows;
            if (index < own) {
                if (!moveItemStackTo(stack, own, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, 0, own, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }
}

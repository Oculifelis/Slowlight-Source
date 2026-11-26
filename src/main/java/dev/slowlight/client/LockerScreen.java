package dev.slowlight.client;

import dev.slowlight.locker.LockerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * The locker's screen. Lockers come in any width and height, so the panel is drawn in the vanilla container
 * colours rather than from a fixed background picture.
 */
public class LockerScreen extends AbstractContainerScreen<LockerMenu> {
    private static final int FILL = 0xFFC6C6C6;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int SHADE = 0xFF555555;
    private static final int EDGE = 0xFF000000;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int SLOT_DARK = 0xFF373737;

    public LockerScreen(LockerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = menu.width();
        this.imageHeight = menu.height();
        this.inventoryLabelY = imageHeight - 94;
        this.inventoryLabelX = (imageWidth - 162) / 2;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        int w = imageWidth;
        int h = imageHeight;
        // Outline with the corners cut, as on vanilla panels.
        g.fill(x + 2, y, x + w - 3, y + 1, EDGE);
        g.fill(x + 2, y + h - 1, x + w - 3, y + h, EDGE);
        g.fill(x, y + 2, x + 1, y + h - 3, EDGE);
        g.fill(x + w - 1, y + 3, x + w, y + h - 2, EDGE);
        g.fill(x + 1, y + 1, x + 2, y + 2, EDGE);
        g.fill(x + w - 3, y + 1, x + w - 2, y + 2, EDGE);
        g.fill(x + w - 2, y + 2, x + w - 1, y + 3, EDGE);
        g.fill(x + 1, y + h - 3, x + 2, y + h - 2, EDGE);
        g.fill(x + 2, y + h - 2, x + 3, y + h - 1, EDGE);
        g.fill(x + w - 3, y + h - 3, x + w - 2, y + h - 2, EDGE);
        g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, EDGE);
        // Face, lit from the top left.
        g.fill(x + 2, y + 1, x + w - 3, y + 3, LIGHT);
        g.fill(x + 1, y + 2, x + 3, y + h - 3, LIGHT);
        g.fill(x + 3, y + h - 3, x + w - 2, y + h - 1, SHADE);
        g.fill(x + w - 3, y + 3, x + w - 1, y + h - 2, SHADE);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, FILL);
        for (Slot slot : menu.slots) {
            int sx = x + slot.x - 1;
            int sy = y + slot.y - 1;
            g.fill(sx, sy, sx + 18, sy + 18, SLOT);
            g.fill(sx, sy, sx + 17, sy + 1, SLOT_DARK);
            g.fill(sx, sy, sx + 1, sy + 17, SLOT_DARK);
            g.fill(sx + 1, sy + 17, sx + 18, sy + 18, LIGHT);
            g.fill(sx + 17, sy + 1, sx + 18, sy + 18, LIGHT);
        }
    }
}

package dev.slowlight.autotest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Every Slowlight item with a written ".tip" line must show it in its hover text (SL4 found three that never did). */
final class TipChecks {
    private TipChecks() {
    }

    static void run(Minecraft mc) {
        int checked = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            var id = BuiltInRegistries.ITEM.getKey(item);
            if (!"slowlight".equals(id.getNamespace())) {
                continue;
            }
            String key = item.getDescriptionId() + ".tip";
            if (!I18n.exists(key)) {
                continue;
            }
            checked++;
            String want = I18n.get(key);
            boolean shown = false;
            for (Component line : Screen.getTooltipFromItem(mc, new ItemStack(item))) {
                if (line.getString().contains(want)) {
                    shown = true;
                    break;
                }
            }
            if (shown) {
                ServerChecks.pass("tip." + id.getPath(), "hover text shows the tip");
            } else {
                ServerChecks.fail("tip." + id.getPath(), "tip written but not in the hover text: " + key);
            }
        }
        if (checked == 0) {
            ServerChecks.fail("tip.any", "no item has a .tip line");
        }
    }
}

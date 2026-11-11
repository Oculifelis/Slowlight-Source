package dev.slowlight.item;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * A feather that came back from the End lighter than it left. Carried, it lets you drift down instead of falling;
 * sneak to drop as usual. With Curios installed it has to be worn in the charm slot instead.
 */
public class UnfallingFeatherItem extends Item {
    private static Method curiosInventory;
    private static Method findFirstCurio;
    private static boolean curiosTried;

    public UnfallingFeatherItem(Properties properties) {
        super(properties);
    }

    /** Hooked to the game bus from {@link dev.slowlight.SlowlightWays}. */
    public static void tick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || player.onGround() || player.isCrouching() || player.isFallFlying()
                || player.getAbilities().flying || player.getDeltaMovement().y > -0.2D) {
            return;
        }
        if (!carries(player)) {
            return;
        }
        MobEffectInstance current = player.getEffect(MobEffects.SLOW_FALLING);
        if (current == null || current.getDuration() < 5) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 15, 0, true, false, false));
        }
    }

    private static boolean carries(Player player) {
        Item feather = dev.slowlight.SlowlightWays.UNFALLING_FEATHER.get();
        if (ModList.get().isLoaded("curios")) {
            Boolean worn = wornAsCurio(player, feather);
            if (worn != null) {
                return worn;
            }
        }
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(feather)) {
                return true;
            }
        }
        return false;
    }

    /** Asks Curios without depending on it; null when its API isn't what we expect, so the inventory counts instead. */
    private static Boolean wornAsCurio(LivingEntity entity, Item item) {
        try {
            if (!curiosTried) {
                curiosTried = true;
                Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
                Class<?> handler = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
                curiosInventory = api.getMethod("getCuriosInventory", LivingEntity.class);
                findFirstCurio = handler.getMethod("findFirstCurio", Item.class);
            }
            if (curiosInventory == null) {
                return null;
            }
            Optional<?> inv = (Optional<?>) curiosInventory.invoke(null, entity);
            return inv.isPresent() && ((Optional<?>) findFirstCurio.invoke(inv.get(), item)).isPresent();
        } catch (ReflectiveOperationException | ClassCastException | LinkageError e) {
            curiosInventory = null;
            return null;
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(ModList.get().isLoaded("curios")
                ? "item.slowlight.unfalling_feather.tip_curios" : "item.slowlight.unfalling_feather.tip")
                .withStyle(ChatFormatting.GRAY));
    }
}

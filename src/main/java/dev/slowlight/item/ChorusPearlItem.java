package dev.slowlight.item;

import dev.slowlight.SlowlightWays;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A popped chorus fruit, aged in a kiln in the End. A Waystone burns one per trip. Two left lying together on the
 * ground slowly fuse into a Twin Pearl, sparking as they go.
 */
public class ChorusPearlItem extends Item {
    /** A minute on the ground. */
    public static final int FUSE_TICKS = 1200;
    private static final String FUSE = "slowlight_fuse";

    public ChorusPearlItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return false;
        }
        CompoundTag data = entity.getPersistentData();
        if (stack.getCount() < 2) {
            data.remove(FUSE);
            return false;
        }
        int t = data.getInt(FUSE) + 1;
        if (t % 20 == 0) {
            level.sendParticles(t > FUSE_TICKS * 3 / 4 ? ParticleTypes.END_ROD : ParticleTypes.PORTAL,
                    entity.getX(), entity.getY() + 0.25D, entity.getZ(), 3, 0.1D, 0.1D, 0.1D, 0.02D);
        }
        if (t < FUSE_TICKS) {
            data.putInt(FUSE, t);
            return false;
        }
        data.remove(FUSE);
        stack.shrink(2);
        entity.setItem(stack.isEmpty() ? ItemStack.EMPTY : stack);
        ItemEntity twin = new ItemEntity(level, entity.getX(), entity.getY(), entity.getZ(),
                new ItemStack(SlowlightWays.TWIN_PEARL.get()));
        twin.setDeltaMovement(0.0D, 0.15D, 0.0D);
        level.addFreshEntity(twin);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, entity.getX(), entity.getY() + 0.3D, entity.getZ(),
                16, 0.15D, 0.15D, 0.15D, 0.05D);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.NEUTRAL, 1.2F, 0.7F);
        if (stack.isEmpty()) {
            entity.discard();
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.slowlight.chorus_pearl.tip").withStyle(ChatFormatting.GRAY));
    }
}

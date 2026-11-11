package dev.slowlight.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * An apple that came back from the End restless. Eat it and you come up for air: straight up to the open sky, or,
 * under a ceiling like the Nether's, up into the next room above with floor to stand on.
 */
public class WanderingAppleItem extends Item {
    public WanderingAppleItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (level instanceof ServerLevel server) {
            BlockPos to = surface(server, entity.blockPosition());
            if (to != null) {
                server.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.CHORUS_FRUIT_TELEPORT,
                        SoundSource.PLAYERS, 1.0F, 1.0F);
                entity.stopRiding();
                entity.teleportTo(to.getX() + 0.5D, to.getY(), to.getZ() + 0.5D);
                entity.resetFallDistance();
                server.playSound(null, to, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.2F);
            }
            if (entity instanceof Player player) {
                player.getCooldowns().addCooldown(this, 20);
            }
        }
        return super.finishUsingItem(stack, level, entity);
    }

    /** Where to come up, or null when there's nowhere higher to stand. */
    static BlockPos surface(ServerLevel level, BlockPos from) {
        if (!level.dimensionType().hasCeiling()) {
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, from.getX(), from.getZ());
            return top > level.getMinBuildHeight() && top > from.getY() ? new BlockPos(from.getX(), top, from.getZ()) : null;
        }
        // Under a ceiling: the first spot above that is two blocks of air over something solid.
        int ceiling = level.getMinBuildHeight() + level.dimensionType().logicalHeight() - 2;
        boolean leftStart = false;
        for (int y = from.getY() + 1; y < ceiling; y++) {
            BlockPos at = new BlockPos(from.getX(), y, from.getZ());
            boolean open = level.isEmptyBlock(at) && level.isEmptyBlock(at.above());
            if (!open) {
                leftStart = true;
            } else if (leftStart && level.getBlockState(at.below()).isFaceSturdy(level, at.below(), net.minecraft.core.Direction.UP)) {
                return at;
            }
        }
        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.slowlight.wandering_apple.tip").withStyle(ChatFormatting.GRAY));
    }
}

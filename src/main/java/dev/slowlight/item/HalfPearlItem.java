package dev.slowlight.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.slowlight.SlowlightWays;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * The half of a Twin Pearl a Waystone didn't keep. Set it in a second stone to pair the two. It remembers where its
 * twin is, and a stone that is broken gives its half back, pointing at the one still standing.
 */
public class HalfPearlItem extends Item {
    /** @param twin where the other stone of the pair stands, if one does */
    public record Link(UUID pair, Optional<GlobalPos> twin) {
        public static final Codec<Link> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("pair").forGetter(Link::pair),
                GlobalPos.CODEC.optionalFieldOf("twin").forGetter(Link::twin)
        ).apply(i, Link::new));
        public static final StreamCodec<ByteBuf, Link> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Link::pair,
                ByteBufCodecs.optional(GlobalPos.STREAM_CODEC), Link::twin,
                Link::new);
    }

    public HalfPearlItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(UUID pair, Optional<GlobalPos> twin) {
        ItemStack stack = new ItemStack(SlowlightWays.HALF_PEARL.get());
        stack.set(SlowlightWays.PEARL_LINK.get(), new Link(pair, twin));
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Link link = stack.get(SlowlightWays.PEARL_LINK.get());
        if (link == null) {
            tooltip.add(Component.translatable("item.slowlight.half_pearl.lost").withStyle(ChatFormatting.GRAY));
        } else if (link.twin().isPresent()) {
            GlobalPos t = link.twin().get();
            tooltip.add(Component.translatable("item.slowlight.half_pearl.twin", t.pos().getX(), t.pos().getY(),
                    t.pos().getZ(), t.dimension().location().getPath()).withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("item.slowlight.half_pearl.alone").withStyle(ChatFormatting.GRAY));
        }
    }
}

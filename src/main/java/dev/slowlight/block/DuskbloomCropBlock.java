package dev.slowlight.block;

import com.mojang.serialization.MapCodec;
import dev.slowlight.SlowlightItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Duskbloom, an old night crop the sniffer remembers. Its seeds grow in farmland only at night (or anywhere
 * there is no sky to keep time by), four stages, and the ripe plant opens and gives off a faint light. Harvested,
 * it gives Duskbloom Petals and seeds. Bone meal hurries it as it does any crop.
 */
public class DuskbloomCropBlock extends CropBlock {
    public static final MapCodec<DuskbloomCropBlock> CODEC = simpleCodec(DuskbloomCropBlock::new);
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
    public static final int MAX_AGE = 3;
    /** Light of the ripe, open plant: a glow to find the field by, nothing more. */
    public static final int GLOW = 3;
    /** About one stage a night at the default random tick speed. */
    private static final int GROW_ONE_IN = 5;

    private static final VoxelShape[] SHAPES = {Block.box(0, 0, 0, 16, 3, 16), Block.box(0, 0, 0, 16, 6, 16),
            Block.box(0, 0, 0, 16, 9, 16), Block.box(0, 0, 0, 16, 12, 16)};

    public DuskbloomCropBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends CropBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    protected IntegerProperty getAgeProperty() {
        return AGE;
    }

    @Override
    public int getMaxAge() {
        return MAX_AGE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[getAge(state)];
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return SlowlightItems.DUSKBLOOM_SEEDS.get();
    }

    /** Vanilla crops grow in light; this one waits for the dark. */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.isAreaLoaded(pos, 1) || isMaxAge(state) || !isNight(level)) {
            return;
        }
        if (random.nextInt(GROW_ONE_IN) == 0) {
            level.setBlock(pos, getStateForAge(getAge(state) + 1), 2);
        }
    }

    /** It grows at night, so the vanilla rule that a crop needs light to stay planted does not apply. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return mayPlaceOn(level.getBlockState(pos.below()), level, pos.below());
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!isMaxAge(state) || random.nextInt(10) != 0) {
            return;
        }
        // A mote of pollen, rising slowly off the open bloom.
        level.addParticle(ParticleTypes.END_ROD,
                pos.getX() + 0.3D + random.nextDouble() * 0.4D,
                pos.getY() + 0.75D,
                pos.getZ() + 0.3D + random.nextDouble() * 0.4D,
                0.0D, 0.006D, 0.0D);
    }

    public static boolean isNight(LevelAccessor level) {
        if (!(level instanceof Level real)) {
            return false;
        }
        return !real.dimensionType().hasSkyLight() || !real.isDay();
    }
}

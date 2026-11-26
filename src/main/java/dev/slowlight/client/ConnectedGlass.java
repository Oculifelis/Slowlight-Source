package dev.slowlight.client;

import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Connected-texture rule for the nether glass, shared by every loader's model.
 *
 * <p>Each face is split into four quadrants. A quadrant looks at the block beside it along the face's U axis, the
 * one beside it along the V axis and the one on the diagonal, and picks one of five sprites: isolated, h (joined
 * along U only), v (joined along V only), all (joined both ways and on the diagonal) or inner (joined both ways but
 * not on the diagonal, so the inner corner keeps its frame pixel). A neighbour only counts when the face is not
 * covered on its side, so a glass block behind another does not join the front face.
 */
public final class ConnectedGlass {
    public static final int ISOLATED = 0, ALL = 1, H = 2, V = 3, INNER = 4;
    public static final String[] VARIANTS = {"isolated", "all", "h", "v", "inner"};

    private ConnectedGlass() {
    }

    /** The face's horizontal texture axis in the world: X for north, south, up and down, Z for east and west. */
    public static Direction.Axis uAxis(Direction face) {
        return face.getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
    }

    /** The face's vertical texture axis in the world: Y for the four sides, Z for up and down. */
    public static Direction.Axis vAxis(Direction face) {
        return face.getAxis() == Direction.Axis.Y ? Direction.Axis.Z : Direction.Axis.Y;
    }

    /**
     * Sprite index for one quadrant. {@code su} and {@code sv} are -1 or +1: the half of the face along U and V
     * (in world directions) the quadrant sits in.
     */
    public static int pick(Function<BlockPos, BlockState> level, BlockPos pos, BlockState self, Direction face, int su, int sv) {
        Direction du = Direction.fromAxisAndDirection(uAxis(face), su > 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
        Direction dv = Direction.fromAxisAndDirection(vAxis(face), sv > 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
        boolean u = joins(level, pos.relative(du), self, face);
        boolean v = joins(level, pos.relative(dv), self, face);
        if (u && v) {
            return joins(level, pos.relative(du).relative(dv), self, face) ? ALL : INNER;
        }
        return u ? H : v ? V : ISOLATED;
    }

    private static boolean joins(Function<BlockPos, BlockState> level, BlockPos at, BlockState self, Direction face) {
        return level.apply(at).getBlock() == self.getBlock() && level.apply(at.relative(face)).getBlock() != self.getBlock();
    }

    /** Index into a [face][quadrant] table: quadrant = (su &gt; 0 ? 1 : 0) + (sv &gt; 0 ? 2 : 0). */
    public static int quadrant(int su, int sv) {
        return (su > 0 ? 1 : 0) + (sv > 0 ? 2 : 0);
    }

    /** The quadrant's box in model units (0-16): {minX, minY, minZ, maxX, maxY, maxZ}. */
    public static float[] box(Direction face, int su, int sv) {
        float[] from = {0, 0, 0}, to = {16, 16, 16};
        int u = uAxis(face).ordinal(), v = vAxis(face).ordinal();
        from[u] = su > 0 ? 8 : 0;
        to[u] = su > 0 ? 16 : 8;
        from[v] = sv > 0 ? 8 : 0;
        to[v] = sv > 0 ? 16 : 8;
        return new float[] {from[0], from[1], from[2], to[0], to[1], to[2]};
    }

    /**
     * Which tile of an n x n picture this block shows on a face, as tu + n * tv, so a picture spans n x n blocks in
     * the world. Follows the same texture directions as {@link #uv}.
     */
    public static int tile(BlockPos pos, Direction face, int n) {
        if (n <= 1) {
            return 0;
        }
        int iu = switch (face) {
            case NORTH -> -pos.getX() - 1;
            case EAST -> -pos.getZ() - 1;
            case WEST -> pos.getZ();
            default -> pos.getX();
        };
        int iv = switch (face) {
            case UP -> pos.getZ();
            case DOWN -> -pos.getZ() - 1;
            default -> -pos.getY() - 1;
        };
        return Math.floorMod(iu, n) + n * Math.floorMod(iv, n);
    }

    /** {@link #uv} narrowed to one tile of an n x n picture. */
    public static float[] uv(Direction face, float[] b, int n, int tile) {
        float[] uv = uv(face, b);
        if (n <= 1) {
            return uv;
        }
        float tu = (tile % n) * 16, tv = (tile / n) * 16;
        return new float[] {(tu + uv[0]) / n, (tv + uv[1]) / n, (tu + uv[2]) / n, (tv + uv[3]) / n};
    }

    /** The sprite area a box face shows, as vanilla fills in a face with no "uv": {u0, v0, u1, v1}. */
    public static float[] uv(Direction face, float[] b) {
        return switch (face) {
            case DOWN -> new float[] {b[0], 16 - b[5], b[3], 16 - b[2]};
            case UP -> new float[] {b[0], b[2], b[3], b[5]};
            case NORTH -> new float[] {16 - b[3], 16 - b[4], 16 - b[0], 16 - b[1]};
            case SOUTH -> new float[] {b[0], 16 - b[4], b[3], 16 - b[1]};
            case WEST -> new float[] {b[2], 16 - b[4], b[5], 16 - b[1]};
            case EAST -> new float[] {16 - b[5], 16 - b[4], 16 - b[2], 16 - b[1]};
        };
    }
}

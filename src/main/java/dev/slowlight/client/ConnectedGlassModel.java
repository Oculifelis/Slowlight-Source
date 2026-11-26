package dev.slowlight.client;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import dev.slowlight.Slowlight;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import org.joml.Vector3f;

/**
 * Connected glass, a block model with {@code "loader": "slowlight:connected"} and the five sprites of
 * {@link ConnectedGlass#VARIANTS} as its textures. Every quadrant of every face is baked once per sprite; drawing a
 * block only picks from them.
 */
public final class ConnectedGlassModel {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Slowlight.MOD_ID, "connected");
    /** Quad per face quadrant, index face * 4 + quadrant; the value is sprite + 5 * tile. */
    static final ModelProperty<byte[]> PICKS = new ModelProperty<>();

    private ConnectedGlassModel() {
    }

    public static void register(ModelEvent.RegisterGeometryLoaders event) {
        event.register(ID, Loader.INSTANCE);
    }

    enum Loader implements IGeometryLoader<Geometry> {
        INSTANCE;

        @Override
        public Geometry read(JsonObject json, JsonDeserializationContext context) {
            return new Geometry(json.has("pattern") ? json.get("pattern").getAsInt() : 1);
        }
    }

    /** {@code n}: mosaic and rose are one picture over n x n blocks, each block showing its own tile. */
    record Geometry(int n) implements IUnbakedGeometry<Geometry> {
        @Override
        public BakedModel bake(IGeometryBakingContext context, ModelBaker baker, Function<Material, TextureAtlasSprite> sprites,
                               ModelState state, ItemOverrides overrides) {
            TextureAtlasSprite[] mats = new TextureAtlasSprite[ConnectedGlass.VARIANTS.length];
            for (int i = 0; i < mats.length; i++) {
                mats[i] = sprites.apply(context.getMaterial(ConnectedGlass.VARIANTS[i]));
            }
            FaceBakery bakery = new FaceBakery();
            int tiles = n * n;
            BakedQuad[][][] quads = new BakedQuad[6][4][mats.length * tiles];
            for (Direction face : Direction.values()) {
                for (int sv = -1; sv <= 1; sv += 2) {
                    for (int su = -1; su <= 1; su += 2) {
                        float[] b = ConnectedGlass.box(face, su, sv);
                        Vector3f from = new Vector3f(b[0], b[1], b[2]);
                        Vector3f to = new Vector3f(b[3], b[4], b[5]);
                        for (int t = 0; t < tiles; t++) {
                            BlockElementFace ef = new BlockElementFace(face, -1, "", new BlockFaceUV(ConnectedGlass.uv(face, b, n, t), 0));
                            for (int s = 0; s < mats.length; s++) {
                                quads[face.get3DDataValue()][ConnectedGlass.quadrant(su, sv)][s + mats.length * t] =
                                        bakery.bakeQuad(from, to, ef, mats[s], face, BlockModelRotation.X0_Y0, null, true);
                            }
                        }
                    }
                }
            }
            TextureAtlasSprite particle = context.hasMaterial("particle") ? sprites.apply(context.getMaterial("particle")) : mats[ConnectedGlass.ISOLATED];
            return new Baked(quads, n, particle, context.getTransforms());
        }
    }

    record Baked(BakedQuad[][][] quads, int n, TextureAtlasSprite particle, ItemTransforms transforms) implements IDynamicBakedModel {
        private static final ChunkRenderTypeSet TRANSLUCENT = ChunkRenderTypeSet.of(RenderType.translucent());

        @Override
        public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
            byte[] picks = new byte[24];
            for (Direction face : Direction.values()) {
                int tile = ConnectedGlass.VARIANTS.length * ConnectedGlass.tile(pos, face, n);
                for (int sv = -1; sv <= 1; sv += 2) {
                    for (int su = -1; su <= 1; su += 2) {
                        picks[face.get3DDataValue() * 4 + ConnectedGlass.quadrant(su, sv)] =
                                (byte) (tile + ConnectedGlass.pick(level::getBlockState, pos, state, face, su, sv));
                    }
                }
            }
            return data.derive().with(PICKS, picks).build();
        }

        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data, RenderType type) {
            if (side == null) {
                return List.of();
            }
            byte[] picks = data.get(PICKS);
            BakedQuad[][] face = quads[side.get3DDataValue()];
            BakedQuad[] out = new BakedQuad[4];
            for (int q = 0; q < 4; q++) {
                out[q] = face[q][picks == null ? ConnectedGlass.ISOLATED : picks[side.get3DDataValue() * 4 + q]];
            }
            return List.of(out);
        }

        @Override
        public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
            return TRANSLUCENT;
        }

        @Override
        public boolean useAmbientOcclusion() {
            return true;
        }

        @Override
        public boolean isGui3d() {
            return true;
        }

        @Override
        public boolean usesBlockLight() {
            return true;
        }

        @Override
        public boolean isCustomRenderer() {
            return false;
        }

        @Override
        public TextureAtlasSprite getParticleIcon() {
            return particle;
        }

        @Override
        public ItemTransforms getTransforms() {
            return transforms;
        }

        @Override
        public ItemOverrides getOverrides() {
            return ItemOverrides.EMPTY;
        }
    }
}

package dev.slowlight.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * A small moth that circles the light it came to for a few seconds, wings beating (two frames), and drifts off.
 * It orbits the point it was spawned at, a little higher and lower as it goes.
 */
public class MothParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final double cx;
    private final double cy;
    private final double cz;
    private final double radius;
    private final float speed;
    private float angle;

    protected MothParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.cx = x;
        this.cy = y;
        this.cz = z;
        this.radius = 0.35D + random.nextDouble() * 0.45D;
        this.speed = (0.10F + random.nextFloat() * 0.08F) * (random.nextBoolean() ? 1 : -1);
        this.angle = random.nextFloat() * Mth.TWO_PI;
        this.lifetime = 80 + random.nextInt(60);
        this.quadSize = 0.06F + random.nextFloat() * 0.02F;
        this.gravity = 0.0F;
        this.hasPhysics = false;
        place();
        xo = this.x;
        yo = this.y;
        zo = this.z;
        setSprite(sprites.get(0, 1));
    }

    private void place() {
        // Wander outward in the last second, as if it had lost interest.
        double r = radius + Math.max(0, age - (lifetime - 20)) * 0.04D;
        setPos(cx + Mth.cos(angle) * r, cy + Mth.sin(angle * 2.3F) * 0.15D + age * 0.002D, cz + Mth.sin(angle) * r);
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        angle += speed + Mth.sin(age * 0.7F) * 0.04F;
        place();
        setSprite(sprites.get((age / 2) % 2, 1));
        if (age > lifetime - 10) {
            alpha = (lifetime - age) / 10.0F;
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz) {
            return new MothParticle(level, x, y, z, sprites);
        }
    }
}

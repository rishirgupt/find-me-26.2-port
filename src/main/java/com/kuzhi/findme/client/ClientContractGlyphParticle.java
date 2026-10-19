package com.kuzhi.findme.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

public class ClientContractGlyphParticle extends SingleQuadParticle {
   private final SpriteSet sprites;
   private final float baseSize;

   private ClientContractGlyphParticle(
      ClientLevel level, double x, double y, double z, SpriteSet sprites, float red, float green, float blue, RandomSource random
   ) {
      super(level, x, y, z, sprites.get(random));
      this.sprites = sprites;
      this.lifetime = 26 + random.nextInt(12);
      this.gravity = 0.0F;
      this.hasPhysics = false;
      this.xd = 0.0;
      this.yd = 0.006 + random.nextDouble() * 0.012;
      this.zd = 0.0;
      this.baseSize = 0.34F + random.nextFloat() * 0.22F;
      this.quadSize = this.baseSize;
      this.alpha = 0.0F;
      this.rCol = colorJitter(red, random);
      this.gCol = colorJitter(green, random);
      this.bCol = colorJitter(blue, random);
   }

   @Override
   protected SingleQuadParticle.Layer getLayer() {
      return SingleQuadParticle.Layer.TRANSLUCENT;
   }

   @Override
   public void tick() {
      super.tick();
      float progress = (float)this.age / this.lifetime;
      float fadeIn = Math.min(1.0F, progress * 5.0F);
      float fadeOut = Math.min(1.0F, (1.0F - progress) * 3.0F);
      this.alpha = Math.max(0.0F, Math.min(fadeIn, fadeOut)) * 0.88F;
      this.quadSize = this.baseSize * (0.85F + progress * 0.55F);
      this.setSpriteFromAge(this.sprites);
   }

   @Override
   protected int getLightCoords(float partialTick) {
      return 15728880;
   }

   private static float colorJitter(float value, RandomSource random) {
      return Math.min(1.0F, value + random.nextFloat() * 0.08F);
   }

   public static class Provider implements ParticleProvider<SimpleParticleType> {
      private final SpriteSet sprites;
      private final float red;
      private final float green;
      private final float blue;

      public Provider(SpriteSet sprites, float red, float green, float blue) {
         this.sprites = sprites;
         this.red = red;
         this.green = green;
         this.blue = blue;
      }

      @Override
      public Particle createParticle(
         SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, RandomSource random
      ) {
         return new ClientContractGlyphParticle(level, x, y, z, this.sprites, this.red, this.green, this.blue, random);
      }
   }
}

package com.kuzhi.findme.common;

import com.kuzhi.findme.compat.Registered;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

public final class ModParticles {
   public static final Registered<SimpleParticleType> CONTRACT_GLYPH = register("contract_glyph");
   public static final Registered<SimpleParticleType> CONTRACT_GLYPH_RESPONSE = register("contract_glyph_response");
   public static final Registered<SimpleParticleType> CONTRACT_GLYPH_VOW = register("contract_glyph_vow");

   private ModParticles() {
   }

   private static Registered<SimpleParticleType> register(String name) {
      return new Registered<>(
         Registry.register(BuiltInRegistries.PARTICLE_TYPE, Identifier.fromNamespaceAndPath("find_me", name), FabricParticleTypes.simple(false))
      );
   }

   public static void register() {
   }
}

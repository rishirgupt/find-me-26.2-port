package com.kuzhi.findme.compat;

import java.util.function.Supplier;

/** A registered game object; {@link #get()} keeps the call sites written for deferred holders unchanged. */
public record Registered<T>(T value) implements Supplier<T> {
   @Override
   public T get() {
      return this.value;
   }
}

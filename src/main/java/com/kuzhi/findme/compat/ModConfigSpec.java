package com.kuzhi.findme.compat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A small stand-in for the NeoForge config spec the mod was written against: the same builder calls, with the
 * values kept in one JSON file in the config folder ({@code config/find_me.json}).
 */
public final class ModConfigSpec {
   private static final Logger LOGGER = LoggerFactory.getLogger("find_me/config");
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
   private final Map<String, ConfigValue<?>> values;
   private Path file;

   private ModConfigSpec(Map<String, ConfigValue<?>> values) {
      this.values = values;
      for (ConfigValue<?> value : values.values()) {
         value.spec = this;
      }
   }

   /** Reads the file (creating or completing it with defaults) and keeps it as the place to save to. */
   public void load(Path file) {
      this.file = file;
      JsonObject root = new JsonObject();
      if (Files.isRegularFile(file)) {
         try (Reader reader = Files.newBufferedReader(file)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (parsed.isJsonObject()) {
               root = parsed.getAsJsonObject();
            }
         } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Could not read {}, using defaults", file, exception);
         }
      }

      for (ConfigValue<?> value : this.values.values()) {
         value.read(root.get(value.name));
      }

      this.save();
   }

   public void save() {
      if (this.file == null) {
         return;
      }

      JsonObject root = new JsonObject();
      for (ConfigValue<?> value : this.values.values()) {
         if (value.comment != null && !value.comment.isEmpty()) {
            root.addProperty("_comment_" + value.name, value.comment);
         }

         root.add(value.name, GSON.toJsonTree(value.get()));
      }

      try {
         Files.createDirectories(this.file.getParent());
         try (Writer writer = Files.newBufferedWriter(this.file)) {
            GSON.toJson(root, writer);
         }
      } catch (IOException exception) {
         LOGGER.warn("Could not write {}", this.file, exception);
      }
   }

   public static class Builder {
      private final Map<String, ConfigValue<?>> values = new LinkedHashMap<>();
      private String pendingComment;

      public Builder comment(String... comment) {
         this.pendingComment = String.join(" ", comment);
         return this;
      }

      public Builder translation(String key) {
         return this;
      }

      public Builder push(String path) {
         this.pendingComment = null;
         return this;
      }

      public Builder pop() {
         return this;
      }

      public BooleanValue define(String name, boolean defaultValue) {
         return this.add(new BooleanValue(name, defaultValue));
      }

      public IntValue defineInRange(String name, int defaultValue, int min, int max) {
         return this.add(new IntValue(name, defaultValue, min, max));
      }

      public DoubleValue defineInRange(String name, double defaultValue, double min, double max) {
         return this.add(new DoubleValue(name, defaultValue, min, max));
      }

      public <T> ConfigValue<List<? extends T>> defineListAllowEmpty(String name, List<? extends T> defaultValue, Predicate<Object> validator) {
         return this.add(new ListValue<T>(name, defaultValue, validator));
      }

      public ModConfigSpec build() {
         return new ModConfigSpec(this.values);
      }

      private <V extends ConfigValue<?>> V add(V value) {
         value.comment = this.pendingComment;
         this.pendingComment = null;
         this.values.put(value.name, value);
         return value;
      }
   }

   public abstract static class ConfigValue<T> {
      final String name;
      private final T defaultValue;
      private T value;
      String comment;
      ModConfigSpec spec;

      ConfigValue(String name, T defaultValue) {
         this.name = name;
         this.defaultValue = defaultValue;
         this.value = defaultValue;
      }

      public T get() {
         return this.value;
      }

      public T getDefault() {
         return this.defaultValue;
      }

      public void set(T value) {
         this.value = value == null ? this.defaultValue : value;
      }

      public void save() {
         if (this.spec != null) {
            this.spec.save();
         }
      }

      final void read(JsonElement element) {
         T parsed = null;
         if (element != null && !element.isJsonNull()) {
            try {
               parsed = this.parse(element);
            } catch (RuntimeException exception) {
               parsed = null;
            }
         }

         this.value = parsed == null ? this.defaultValue : parsed;
      }

      abstract T parse(JsonElement element);
   }

   public static final class BooleanValue extends ConfigValue<Boolean> {
      BooleanValue(String name, boolean defaultValue) {
         super(name, defaultValue);
      }

      @Override
      Boolean parse(JsonElement element) {
         return element.getAsBoolean();
      }
   }

   public static final class IntValue extends ConfigValue<Integer> {
      private final int min;
      private final int max;

      IntValue(String name, int defaultValue, int min, int max) {
         super(name, defaultValue);
         this.min = min;
         this.max = max;
      }

      @Override
      Integer parse(JsonElement element) {
         return Math.max(this.min, Math.min(this.max, element.getAsInt()));
      }
   }

   public static final class DoubleValue extends ConfigValue<Double> {
      private final double min;
      private final double max;

      DoubleValue(String name, double defaultValue, double min, double max) {
         super(name, defaultValue);
         this.min = min;
         this.max = max;
      }

      @Override
      Double parse(JsonElement element) {
         return Math.max(this.min, Math.min(this.max, element.getAsDouble()));
      }
   }

   static final class ListValue<T> extends ConfigValue<List<? extends T>> {
      private final Predicate<Object> validator;

      ListValue(String name, List<? extends T> defaultValue, Predicate<Object> validator) {
         super(name, defaultValue);
         this.validator = validator;
      }

      @Override
      @SuppressWarnings("unchecked")
      List<? extends T> parse(JsonElement element) {
         JsonArray array = element.getAsJsonArray();
         List<T> result = new ArrayList<>();
         for (JsonElement entry : array) {
            if (entry.isJsonPrimitive()) {
               Object item = entry.getAsString();
               if (this.validator == null || this.validator.test(item)) {
                  result.add((T)item);
               }
            }
         }

         return result;
      }
   }
}

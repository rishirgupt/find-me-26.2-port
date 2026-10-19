package com.kuzhi.findme.server;

import com.kuzhi.findme.common.CompanionKind;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public final class CompanionCommands {
   private CompanionCommands() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(root("findme"));
   }

   private static LiteralArgumentBuilder<CommandSourceStack> root(String name) {
      LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name);
      root.then(vault());
      root.then(backup());
      root.then(forceTame("tame"));
      root.then(forceTame("forcetame"));
      root.then(kind("mount", CompanionKind.MOUNT));
      root.then(kind("companion", CompanionKind.COMPANION));
      return root;
   }

   private static LiteralArgumentBuilder<CommandSourceStack> vault() {
      LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("vault");
      root.then(Commands.literal("list").executes(ctx -> CompanionManager.listVault(player((CommandSourceStack)ctx.getSource()))));
      root.then(
         Commands.literal("restore")
            .then(
               Commands.argument("index", IntegerArgumentType.integer(0))
                  .executes(ctx -> CompanionManager.restoreVault(player((CommandSourceStack)ctx.getSource()), IntegerArgumentType.getInteger(ctx, "index")))
            )
      );
      return root;
   }

   private static LiteralArgumentBuilder<CommandSourceStack> backup() {
      LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("backup");
      root.then(Commands.literal("list").executes(ctx -> CompanionManager.listBackups(player((CommandSourceStack)ctx.getSource()))));
      root.then(Commands.literal("create").executes(ctx -> CompanionManager.createBackup(player((CommandSourceStack)ctx.getSource()), "manual")));
      root.then(
         Commands.literal("restore")
            .then(
               Commands.argument("index", IntegerArgumentType.integer(0))
                  .executes(ctx -> CompanionManager.restoreBackup(player((CommandSourceStack)ctx.getSource()), IntegerArgumentType.getInteger(ctx, "index")))
            )
      );
      return root;
   }

   private static LiteralArgumentBuilder<CommandSourceStack> forceTame(String name) {
      LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name);
      root.then(Commands.literal("mount").executes(ctx -> CompanionManager.tameTarget(player((CommandSourceStack)ctx.getSource()), CompanionKind.MOUNT)));
      root.then(
         Commands.literal("companion").executes(ctx -> CompanionManager.tameTarget(player((CommandSourceStack)ctx.getSource()), CompanionKind.COMPANION))
      );
      return root;
   }

   private static LiteralArgumentBuilder<CommandSourceStack> kind(String name, CompanionKind kind) {
      LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name);
      root.then(Commands.literal("list").executes(ctx -> CompanionManager.list(player((CommandSourceStack)ctx.getSource()), kind)));
      root.then(Commands.literal("summon").executes(ctx -> CompanionManager.summon(player((CommandSourceStack)ctx.getSource()), kind)));
      root.then(Commands.literal("return").executes(ctx -> CompanionManager.returnHome(player((CommandSourceStack)ctx.getSource()), kind)));
      root.then(home(kind));
      root.then(
         Commands.literal("remove")
            .then(
               Commands.argument("index", IntegerArgumentType.integer(0))
                  .executes(ctx -> CompanionManager.remove(player((CommandSourceStack)ctx.getSource()), kind, IntegerArgumentType.getInteger(ctx, "index")))
            )
      );
      return root;
   }

   private static LiteralArgumentBuilder<CommandSourceStack> home(CompanionKind kind) {
      LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("home");
      root.then(Commands.literal("set").executes(ctx -> CompanionManager.setHome(player((CommandSourceStack)ctx.getSource()), kind)));
      root.then(Commands.literal("go").executes(ctx -> CompanionManager.goHome(player((CommandSourceStack)ctx.getSource()), kind)));
      root.then(Commands.literal("clear").executes(ctx -> CompanionManager.clearHome(player((CommandSourceStack)ctx.getSource()), kind)));
      return root;
   }

   private static ServerPlayer player(CommandSourceStack source) throws CommandSyntaxException {
      return source.getPlayerOrException();
   }
}

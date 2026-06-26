package com.rslover521.createInstantSchematics.core;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.rslover521.createInstantSchematics.CreateInstantSchematics;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class ModCommands {
    private static final ResourceLocation EMPTY_SCHEMATIC_ID = new ResourceLocation("create", "empty_schematic");
    private static final ResourceLocation SCHEMATIC_ID = new ResourceLocation("create", "schematic");

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        registerRoot(dispatcher, "cischematic");
        registerRoot(dispatcher, "cis");
    }

    private static void registerRoot(CommandDispatcher<CommandSourceStack> dispatcher, String name) {
        dispatcher.register(Commands.literal(name)
                .then(Commands.literal("load")
                        .then(Commands.argument("filename", StringArgumentType.greedyString())
                                .executes(context -> load(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "filename")))))
                .then(Commands.literal("unload")
                        .executes(context -> unload(context.getSource()))));
    }

    private static int load(CommandSourceStack source, String requestedFilename) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception ignored) {
            source.sendFailure(Component.literal("This command must be executed by a player."));
            return 0;
        }

        ItemStack held = player.getMainHandItem();
        Item emptySchematic = ForgeRegistries.ITEMS.getValue(EMPTY_SCHEMATIC_ID);
        if (emptySchematic == null || !held.is(emptySchematic)) {
            player.sendSystemMessage(Component.literal("Hold a Create Empty Schematic to load a schematic."));
            return 0;
        }

        String normalizedRequest = unquote(requestedFilename.trim());
        String filename = normalizedRequest.endsWith(".nbt") ? normalizedRequest : normalizedRequest + ".nbt";
        String owner = player.getGameProfile().getName();
        Path playerDirectory;
        Path schematicPath;
        try {
            playerDirectory = Paths.get("schematics", "uploaded", owner).toAbsolutePath().normalize();
            schematicPath = playerDirectory.resolve(filename).normalize();
        } catch (InvalidPathException exception) {
            player.sendSystemMessage(Component.literal("Invalid schematic path."));
            return 0;
        }

        if (!schematicPath.startsWith(playerDirectory)) {
            player.sendSystemMessage(Component.literal("Invalid schematic path."));
            return 0;
        }

        Path schematicsDirectory = playerDirectory.getParent().getParent();
        if (!Files.isRegularFile(schematicPath)) {
            Path rootSchematicPath = schematicsDirectory.resolve(filename).normalize();
            if (rootSchematicPath.startsWith(schematicsDirectory)
                    && Files.isRegularFile(rootSchematicPath)) {
                player.sendSystemMessage(Component.literal(
                        "Found that file in schematics/, but Create written schematics must be in: "
                                + Paths.get("schematics", "uploaded", owner, filename)));
                player.sendSystemMessage(Component.literal("Move or copy it to: " + schematicPath));
                return 0;
            }
            player.sendSystemMessage(Component.literal("Schematic file does not exist: " + filename));
            return 0;
        }

        Path clientSchematicPath = schematicsDirectory.resolve(filename).normalize();
        if (!clientSchematicPath.startsWith(schematicsDirectory)) {
            player.sendSystemMessage(Component.literal("Invalid schematic path."));
            return 0;
        }
        try {
            Path parent = clientSchematicPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(schematicPath, clientSchematicPath, StandardCopyOption.REPLACE_EXISTING);
            ModNetwork.sendSchematicToClient(player, filename, schematicPath);
        } catch (IOException exception) {
            CreateInstantSchematics.LOGGER.warn("Failed to prepare schematic for Create's client preview", exception);
            player.sendSystemMessage(Component.literal("Failed to prepare schematic preview file. Check the server log for details."));
            return 0;
        }

        Item schematicItem = ForgeRegistries.ITEMS.getValue(SCHEMATIC_ID);
        if (schematicItem == null) {
            player.sendSystemMessage(Component.literal("Create's Schematic item is not available."));
            return 0;
        }

        ItemStack schematic = new ItemStack(schematicItem);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Deployed", false);
        tag.putString("Owner", owner);
        tag.putString("File", filename);
        tag.put("Anchor", NbtUtils.writeBlockPos(BlockPos.ZERO));
        tag.putString("Rotation", Rotation.NONE.name());
        tag.putString("Mirror", Mirror.NONE.name());
        schematic.setTag(tag);

        try {
            writeCreateSchematicSize(player, schematic);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            CreateInstantSchematics.LOGGER.warn("Failed to prepare Create schematic item", exception);
            player.sendSystemMessage(Component.literal("Failed to load schematic. Check the server log for details."));
            return 0;
        }

        player.setItemInHand(InteractionHand.MAIN_HAND, schematic);
        player.sendSystemMessage(Component.literal("Loaded schematic: " + filename));
        return 1;
    }

    private static int unload(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception ignored) {
            source.sendFailure(Component.literal("This command must be executed by a player."));
            return 0;
        }

        ItemStack held = player.getMainHandItem();
        Item schematicItem = ForgeRegistries.ITEMS.getValue(SCHEMATIC_ID);
        if (schematicItem == null || !held.is(schematicItem) || !isWrittenSchematic(held)) {
            player.sendSystemMessage(Component.literal("Hold a written Create Schematic to unload it."));
            return 0;
        }

        Item emptySchematic = ForgeRegistries.ITEMS.getValue(EMPTY_SCHEMATIC_ID);
        if (emptySchematic == null) {
            player.sendSystemMessage(Component.literal("Create's Empty Schematic item is not available."));
            return 0;
        }

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(emptySchematic));
        player.sendSystemMessage(Component.literal("Unloaded schematic. The .nbt file was not deleted."));
        return 1;
    }

    private static boolean isWrittenSchematic(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains("Owner") && tag.contains("File");
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static void writeCreateSchematicSize(ServerPlayer player, ItemStack schematic)
            throws ReflectiveOperationException {
        Class<?> schematicItemClass = Class.forName("com.simibubi.create.content.schematics.SchematicItem");
        Method writeSize = schematicItemClass.getMethod("writeSize", HolderGetter.class, ItemStack.class);
        try {
            writeSize.invoke(null, player.level().holderLookup(Registries.BLOCK), schematic);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }
    }
}

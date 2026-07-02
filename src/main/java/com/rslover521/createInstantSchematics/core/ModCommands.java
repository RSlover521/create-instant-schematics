package com.rslover521.createInstantSchematics.core;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.rslover521.createInstantSchematics.CreateInstantSchematics;
import net.minecraft.commands.arguments.EntityArgument;
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
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

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
                                .suggests(ModCommands::suggestSchematics)
                                .executes(context -> load(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "filename")))))
                .then(Commands.literal("list")
                        .executes(context -> list(context.getSource())))
                .then(Commands.literal("unload")
                        .executes(context -> unload(context.getSource())))
                .then(Commands.literal("share")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> share(
                                        context.getSource(),
                                        EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("sharedlist")
                        .executes(context -> sharedList(context.getSource())))
                .then(Commands.literal("clearshared")
                        .executes(context -> clearShared(context.getSource()))));
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
        Path sourceDirectory;
        Path schematicPath;
        try {
            sourceDirectory = schematicSourceDirectory(player);
            schematicPath = sourceDirectory.resolve(filename).normalize();
        } catch (InvalidPathException exception) {
            player.sendSystemMessage(Component.literal("Invalid schematic path."));
            return 0;
        }

        if (!schematicPath.startsWith(sourceDirectory)) {
            player.sendSystemMessage(Component.literal("Invalid schematic path."));
            return 0;
        }

        Path schematicsDirectory = rootSchematicDirectory();
        if (!Files.isRegularFile(schematicPath)) {
            Path localSchematicPath = schematicsDirectory.resolve(filename).normalize();
            if (!isSingleplayer(player)
                    && localSchematicPath.startsWith(schematicsDirectory)
                    && Files.isRegularFile(localSchematicPath)) {
                player.sendSystemMessage(Component.literal(
                        "Found that file in schematics/, but Create written schematics must be in: "
                                + Paths.get("schematics", "uploaded", owner, filename)));
                player.sendSystemMessage(Component.literal("Move or copy it to: "
                        + uploadedSchematicDirectory(player).resolve(filename).normalize()));
                return 0;
            }
            player.sendSystemMessage(Component.literal("Schematic file does not exist: " + filename));
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

        Path serverSchematicPath;
        try {
            serverSchematicPath = prepareServerSchematicFile(player, filename, schematicPath);
        } catch (IOException exception) {
            CreateInstantSchematics.LOGGER.warn("Failed to prepare schematic for Create's server reader", exception);
            player.sendSystemMessage(Component.literal("Failed to prepare schematic file. Check the server log for details."));
            return 0;
        }

        try {
            writeCreateSchematicSize(player, schematic);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (isTruncatedCompressedNbt(exception)) {
                CreateInstantSchematics.LOGGER.warn("Schematic file is incomplete or corrupt: {}", serverSchematicPath, exception);
                player.sendSystemMessage(Component.literal(
                        "Schematic file is incomplete or corrupt: " + filename
                                + ". Re-copy the .nbt file to the server and try again."));
                return 0;
            }
            CreateInstantSchematics.LOGGER.warn("Failed to prepare Create schematic item", exception);
            player.sendSystemMessage(Component.literal("Failed to load schematic. Check the server log for details."));
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
            if (!Files.exists(clientSchematicPath) || !Files.isSameFile(serverSchematicPath, clientSchematicPath)) {
                Files.copy(serverSchematicPath, clientSchematicPath, StandardCopyOption.REPLACE_EXISTING);
            }
            ModNetwork.sendSchematicToClient(player, filename, serverSchematicPath);
        } catch (IOException exception) {
            CreateInstantSchematics.LOGGER.warn("Failed to prepare schematic for Create's client preview", exception);
            player.sendSystemMessage(Component.literal("Failed to prepare schematic preview file. Check the server log for details."));
            return 0;
        }

        player.setItemInHand(InteractionHand.MAIN_HAND, schematic);
        player.sendSystemMessage(Component.literal("Loaded schematic: " + filename));
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestSchematics(CommandContext<CommandSourceStack> context,
                                                                    SuggestionsBuilder builder) {
        ServerPlayer player;
        try {
            player = context.getSource().getPlayerOrException();
        } catch (Exception ignored) {
            return builder.buildFuture();
        }

        String remaining = unquote(builder.getRemaining().trim()).toLowerCase(Locale.ROOT);
        try {
            findAvailableSchematics(player).stream()
                    .filter(filename -> filename.toLowerCase(Locale.ROOT).startsWith(remaining))
                    .forEach(builder::suggest);
        } catch (IOException exception) {
            CreateInstantSchematics.LOGGER.warn("Failed to list schematic suggestions", exception);
        }

        return builder.buildFuture();
    }

    private static int list(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception ignored) {
            source.sendFailure(Component.literal("This command must be executed by a player."));
            return 0;
        }

        List<String> schematics;
        try {
            schematics = findAvailableSchematics(player);
        } catch (IOException exception) {
            CreateInstantSchematics.LOGGER.warn("Failed to list schematics for {}", player.getGameProfile().getName(), exception);
            player.sendSystemMessage(Component.literal("Failed to list schematics. Check the server log for details."));
            return 0;
        }

        if (schematics.isEmpty()) {
            player.sendSystemMessage(Component.literal("No schematics found in "
                    + displaySchematicDirectory(player) + "."));
            return 0;
        }

        player.sendSystemMessage(Component.literal("Available schematics (" + schematics.size() + "):"));
        schematics.forEach(filename -> player.sendSystemMessage(Component.literal("- " + filename)));
        return schematics.size();
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

    private static int share(CommandSourceStack source, ServerPlayer target) {
        ServerPlayer sender;
        try {
            sender = source.getPlayerOrException();
        } catch (Exception ignored) {
            source.sendFailure(Component.literal("This command must be executed by a player."));
            return 0;
        }

        if (sender.getUUID().equals(target.getUUID())) {
            sender.sendSystemMessage(Component.literal("Choose another player to share with."));
            return 0;
        }

        ItemStack held = sender.getMainHandItem();
        Item schematicItem = ForgeRegistries.ITEMS.getValue(SCHEMATIC_ID);
        if (schematicItem == null || !held.is(schematicItem) || !isWrittenSchematic(held)) {
            sender.sendSystemMessage(Component.literal("Hold a loaded Create Schematic to share it."));
            return 0;
        }

        CompoundTag tag = held.getTag();
        String filename = tag.getString("File");
        if (filename.isBlank()) {
            sender.sendSystemMessage(Component.literal("The held schematic has no file name."));
            return 0;
        }

        Path sourceDirectory;
        Path schematicPath;
        try {
            sourceDirectory = schematicSourceDirectory(sender);
            schematicPath = sourceDirectory.resolve(filename).normalize();
        } catch (InvalidPathException exception) {
            sender.sendSystemMessage(Component.literal("Invalid schematic path."));
            return 0;
        }

        if (!schematicPath.startsWith(sourceDirectory) || !Files.isRegularFile(schematicPath)) {
            sender.sendSystemMessage(Component.literal("Server could not find the loaded schematic file: " + filename));
            return 0;
        }

        try {
            long fileSize = Files.size(schematicPath);
            if (fileSize > ModNetwork.MAX_SHARED_SCHEMATIC_SIZE) {
                sender.sendSystemMessage(Component.literal("Schematic is too large to share. Max size is "
                        + (ModNetwork.MAX_SHARED_SCHEMATIC_SIZE / 1024 / 1024) + " MiB."));
                return 0;
            }

            String hash = sha256(schematicPath);
            BlockPos anchor = tag.contains("Anchor") ? NbtUtils.readBlockPos(tag.getCompound("Anchor")) : BlockPos.ZERO;
            Rotation rotation = readEnum(tag.getString("Rotation"), Rotation.NONE, Rotation.class);
            Mirror mirror = readEnum(tag.getString("Mirror"), Mirror.NONE, Mirror.class);
            BlockPos bounds = readBounds(tag);
            int transferId = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);

            ModNetwork.sendSharedSchematicToClient(target, transferId, filename, hash, schematicPath, anchor,
                    rotation, mirror, bounds);
            sender.sendSystemMessage(Component.literal("Shared schematic with " + target.getGameProfile().getName()
                    + ": " + filename + " (" + hash.substring(0, 8) + ")"));
            target.sendSystemMessage(Component.literal(sender.getGameProfile().getName()
                    + " shared a schematic with you: " + filename));
            return 1;
        } catch (IOException | NoSuchAlgorithmException exception) {
            CreateInstantSchematics.LOGGER.warn("Failed to share schematic {} from {}", filename,
                    sender.getGameProfile().getName(), exception);
            sender.sendSystemMessage(Component.literal("Failed to share schematic. Check the server log for details."));
            return 0;
        }
    }

    private static int sharedList(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception ignored) {
            source.sendFailure(Component.literal("This command must be executed by a player."));
            return 0;
        }

        ModNetwork.sendSharedSchematicManageCommand(player, false);
        return 1;
    }

    private static int clearShared(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception ignored) {
            source.sendFailure(Component.literal("This command must be executed by a player."));
            return 0;
        }

        ModNetwork.sendSharedSchematicManageCommand(player, true);
        return 1;
    }

    private static boolean isWrittenSchematic(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains("Owner") && tag.contains("File");
    }

    private static List<String> findAvailableSchematics(ServerPlayer player) throws IOException {
        Path directory = schematicSourceDirectory(player);
        if (!Files.isDirectory(directory)) {
            return List.of();
        }

        boolean singleplayer = isSingleplayer(player);
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile)
                    .map(directory::relativize)
                    .filter(path -> !singleplayer || !startsWithUploadedDirectory(path))
                    .map(path -> path.toString().replace('\\', '/'))
                    .filter(filename -> filename.endsWith(".nbt"))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }
    }

    private static boolean startsWithUploadedDirectory(Path path) {
        return path.getNameCount() > 0 && path.getName(0).toString().equals("uploaded");
    }

    private static Path prepareServerSchematicFile(ServerPlayer player, String filename, Path sourcePath)
            throws IOException {
        Path uploadedDirectory = uploadedSchematicDirectory(player);
        Path serverSchematicPath = uploadedDirectory.resolve(filename).normalize();
        if (!serverSchematicPath.startsWith(uploadedDirectory)) {
            throw new InvalidPathException(filename, "Invalid schematic path");
        }

        if (isSingleplayer(player)
                && (!Files.exists(serverSchematicPath) || !Files.isSameFile(sourcePath, serverSchematicPath))) {
            Path parent = serverSchematicPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(sourcePath, serverSchematicPath, StandardCopyOption.REPLACE_EXISTING);
        }

        return serverSchematicPath;
    }

    private static Path schematicSourceDirectory(ServerPlayer player) throws InvalidPathException {
        return isSingleplayer(player) ? rootSchematicDirectory() : uploadedSchematicDirectory(player);
    }

    private static Path uploadedSchematicDirectory(ServerPlayer player) throws InvalidPathException {
        return Paths.get("schematics", "uploaded", player.getGameProfile().getName())
                .toAbsolutePath()
                .normalize();
    }

    private static Path rootSchematicDirectory() throws InvalidPathException {
        return Paths.get("schematics").toAbsolutePath().normalize();
    }

    private static Path displaySchematicDirectory(ServerPlayer player) {
        return isSingleplayer(player)
                ? Paths.get("schematics")
                : Paths.get("schematics", "uploaded", player.getGameProfile().getName());
    }

    private static boolean isSingleplayer(ServerPlayer player) {
        return player.getServer() != null && player.getServer().isSingleplayer();
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static boolean isTruncatedCompressedNbt(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof EOFException) {
                return true;
            }

            String message = current.getMessage();
            if (message != null && message.contains("Unexpected end of ZLIB input stream")) {
                return true;
            }
        }
        return false;
    }

    private static BlockPos readBounds(CompoundTag tag) {
        if (!tag.contains("Bounds")) {
            return BlockPos.ZERO;
        }

        try {
            var bounds = tag.getList("Bounds", 3);
            if (bounds.size() < 3) {
                return BlockPos.ZERO;
            }
            return new BlockPos(bounds.getInt(0), bounds.getInt(1), bounds.getInt(2));
        } catch (RuntimeException exception) {
            return BlockPos.ZERO;
        }
    }

    private static <T extends Enum<T>> T readEnum(String name, T fallback, Class<T> enumClass) {
        if (name == null || name.isBlank()) {
            return fallback;
        }

        try {
            return Enum.valueOf(enumClass, name);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private static String sha256(Path path) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
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

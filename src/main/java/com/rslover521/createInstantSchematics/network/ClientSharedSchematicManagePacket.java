package com.rslover521.createInstantSchematics.network;

import com.rslover521.createInstantSchematics.CreateInstantSchematics;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class ClientSharedSchematicManagePacket {
    private final boolean clear;

    public ClientSharedSchematicManagePacket(boolean clear) {
        this.clear = clear;
    }

    public ClientSharedSchematicManagePacket(FriendlyByteBuf buffer) {
        this.clear = buffer.readBoolean();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(clear);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(this::handleClient);
        context.setPacketHandled(true);
    }

    private void handleClient() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        try {
            Path schematicsDirectory = Paths.get("schematics").toAbsolutePath().normalize();
            if (!Files.isDirectory(schematicsDirectory)) {
                minecraft.player.displayClientMessage(Component.literal("No CIS shared schematics found in schematics/."),
                        false);
                return;
            }

            List<Path> files = findSharedFiles(schematicsDirectory);
            if (clear) {
                int deleted = 0;
                for (Path file : files) {
                    Files.delete(file);
                    deleted++;
                }
                minecraft.player.displayClientMessage(
                        Component.literal("Deleted " + deleted + " CIS shared schematic file(s)."), false);
                return;
            }

            if (files.isEmpty()) {
                minecraft.player.displayClientMessage(Component.literal("No CIS shared schematics found in schematics/."),
                        false);
                return;
            }

            minecraft.player.displayClientMessage(Component.literal("CIS shared schematics (" + files.size() + "):"),
                    false);
            for (Path file : files) {
                minecraft.player.displayClientMessage(Component.literal("- " + file.getFileName()), false);
            }
        } catch (IOException exception) {
            CreateInstantSchematics.LOGGER.warn("Failed to manage shared schematics", exception);
            minecraft.player.displayClientMessage(
                    Component.literal("Failed to manage shared schematics. Check the client log for details."), false);
        }
    }

    private static List<Path> findSharedFiles(Path schematicsDirectory) throws IOException {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> paths = Files.newDirectoryStream(schematicsDirectory, "cis_shared_*.nbt")) {
            for (Path path : paths) {
                Path normalized = path.toAbsolutePath().normalize();
                if (normalized.startsWith(schematicsDirectory)
                        && normalized.getParent() != null
                        && normalized.getParent().equals(schematicsDirectory)
                        && Files.isRegularFile(normalized)) {
                    files.add(normalized);
                }
            }
        }
        files.sort((left, right) -> left.getFileName().toString()
                .compareToIgnoreCase(right.getFileName().toString()));
        return files;
    }
}

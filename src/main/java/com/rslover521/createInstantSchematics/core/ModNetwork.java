package com.rslover521.createInstantSchematics.core;

import com.rslover521.createInstantSchematics.ClientSchematicTransferPacket;
import com.rslover521.createInstantSchematics.CreateInstantSchematics;
import com.rslover521.createInstantSchematics.network.ClientSharedSchematicChunkPacket;
import com.rslover521.createInstantSchematics.network.ClientSharedSchematicCompletePacket;
import com.rslover521.createInstantSchematics.network.ClientSharedSchematicManagePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class ModNetwork {
    private static final String PROTOCOL_VERSION = "1";
    private static final int SCHEMATIC_CHUNK_SIZE = 512 * 1024;
    public static final long MAX_SHARED_SCHEMATIC_SIZE = 64L * 1024L * 1024L;
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(CreateInstantSchematics.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    public static void register() {
        int index = 0;
        CHANNEL.messageBuilder(ClientSchematicTransferPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ClientSchematicTransferPacket::encode)
                .decoder(ClientSchematicTransferPacket::new)
                .consumerMainThread(ClientSchematicTransferPacket::handle)
                .add();
        index++;
        CHANNEL.messageBuilder(ClientSharedSchematicChunkPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ClientSharedSchematicChunkPacket::encode)
                .decoder(ClientSharedSchematicChunkPacket::new)
                .consumerMainThread(ClientSharedSchematicChunkPacket::handle)
                .add();
        CHANNEL.messageBuilder(ClientSharedSchematicCompletePacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ClientSharedSchematicCompletePacket::encode)
                .decoder(ClientSharedSchematicCompletePacket::new)
                .consumerMainThread(ClientSharedSchematicCompletePacket::handle)
                .add();
        CHANNEL.messageBuilder(ClientSharedSchematicManagePacket.class, index, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ClientSharedSchematicManagePacket::encode)
                .decoder(ClientSharedSchematicManagePacket::new)
                .consumerMainThread(ClientSharedSchematicManagePacket::handle)
                .add();
    }

    public static void sendSchematicToClient(ServerPlayer player, String filename, Path schematicPath) throws IOException {
        long fileSize = Files.size(schematicPath);
        int chunkCount = Math.max(1, (int) ((fileSize + SCHEMATIC_CHUNK_SIZE - 1) / SCHEMATIC_CHUNK_SIZE));

        try (InputStream input = Files.newInputStream(schematicPath)) {
            byte[] buffer = new byte[SCHEMATIC_CHUNK_SIZE];
            for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
                int offset = 0;
                while (offset < buffer.length) {
                    int read = input.read(buffer, offset, buffer.length - offset);
                    if (read == -1) {
                        break;
                    }
                    offset += read;
                }

                byte[] chunk = offset == buffer.length ? buffer.clone() : Arrays.copyOf(buffer, offset);
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new ClientSchematicTransferPacket(filename, chunkIndex, chunkCount, chunk));
            }
        }
    }

    public static void sendSharedSchematicToClient(ServerPlayer player, int transferId, String originalFilename,
                                                   String fullHash, Path schematicPath, BlockPos anchor,
                                                   Rotation rotation, Mirror mirror, BlockPos bounds)
            throws IOException {
        long fileSize = Files.size(schematicPath);
        int chunkCount = Math.max(1, (int) ((fileSize + SCHEMATIC_CHUNK_SIZE - 1) / SCHEMATIC_CHUNK_SIZE));

        try (InputStream input = Files.newInputStream(schematicPath)) {
            byte[] buffer = new byte[SCHEMATIC_CHUNK_SIZE];
            for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
                int offset = 0;
                while (offset < buffer.length) {
                    int read = input.read(buffer, offset, buffer.length - offset);
                    if (read == -1) {
                        break;
                    }
                    offset += read;
                }

                byte[] chunk = offset == buffer.length ? buffer.clone() : Arrays.copyOf(buffer, offset);
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new ClientSharedSchematicChunkPacket(transferId, originalFilename, fullHash, chunkIndex,
                                chunkCount, fileSize, chunk));
            }
        }

        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ClientSharedSchematicCompletePacket(transferId, originalFilename, fullHash, anchor,
                        rotation, mirror, bounds));
    }

    public static void sendSharedSchematicManageCommand(ServerPlayer player, boolean clear) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ClientSharedSchematicManagePacket(clear));
    }
}

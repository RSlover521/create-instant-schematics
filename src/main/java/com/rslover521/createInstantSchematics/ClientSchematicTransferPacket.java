package com.rslover521.createInstantSchematics;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class ClientSchematicTransferPacket {
    private static final Map<String, Transfer> TRANSFERS = new HashMap<>();

    private final String filename;
    private final int chunkIndex;
    private final int chunkCount;
    private final byte[] data;

    public ClientSchematicTransferPacket(String filename, int chunkIndex, int chunkCount, byte[] data) {
        this.filename = filename;
        this.chunkIndex = chunkIndex;
        this.chunkCount = chunkCount;
        this.data = data;
    }

    public ClientSchematicTransferPacket(FriendlyByteBuf buffer) {
        this.filename = buffer.readUtf(32767);
        this.chunkIndex = buffer.readVarInt();
        this.chunkCount = buffer.readVarInt();
        this.data = buffer.readByteArray();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(filename);
        buffer.writeVarInt(chunkIndex);
        buffer.writeVarInt(chunkCount);
        buffer.writeByteArray(data);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(this::handleClient);
        context.setPacketHandled(true);
    }

    private void handleClient() {
        if (chunkIndex < 0 || chunkIndex >= chunkCount || chunkCount <= 0) {
            return;
        }

        Transfer transfer = TRANSFERS.computeIfAbsent(filename, ignored -> new Transfer(chunkCount));
        if (transfer.chunkCount != chunkCount) {
            TRANSFERS.put(filename, transfer = new Transfer(chunkCount));
        }

        if (transfer.chunks[chunkIndex] == null) {
            transfer.received++;
        }
        transfer.chunks[chunkIndex] = data;
        if (transfer.received < transfer.chunkCount) {
            return;
        }

        TRANSFERS.remove(filename);
        try {
            Path schematicsDirectory = Paths.get("schematics").toAbsolutePath().normalize();
            Path schematicPath = schematicsDirectory.resolve(filename).normalize();
            if (!schematicPath.startsWith(schematicsDirectory)) {
                return;
            }

            Path parent = schematicPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            try (var output = Files.newOutputStream(schematicPath, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                for (byte[] chunk : transfer.chunks) {
                    if (chunk == null) {
                        return;
                    }
                    output.write(chunk);
                }
            }
        } catch (InvalidPathException | IOException exception) {
            CreateInstantSchematics.LOGGER.warn("Failed to write client schematic cache file", exception);
        }
    }

    private static class Transfer {
        private final int chunkCount;
        private final byte[][] chunks;
        private int received;

        private Transfer(int chunkCount) {
            this.chunkCount = chunkCount;
            this.chunks = new byte[chunkCount][];
        }
    }
}

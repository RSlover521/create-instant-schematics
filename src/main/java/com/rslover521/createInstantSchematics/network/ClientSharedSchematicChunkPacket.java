package com.rslover521.createInstantSchematics.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ClientSharedSchematicChunkPacket {
    private final int transferId;
    private final String originalFilename;
    private final String hash;
    private final int chunkIndex;
    private final int chunkCount;
    private final long totalSize;
    private final byte[] data;

    public ClientSharedSchematicChunkPacket(int transferId, String originalFilename, String hash, int chunkIndex,
                                            int chunkCount, long totalSize, byte[] data) {
        this.transferId = transferId;
        this.originalFilename = originalFilename;
        this.hash = hash;
        this.chunkIndex = chunkIndex;
        this.chunkCount = chunkCount;
        this.totalSize = totalSize;
        this.data = data;
    }

    public ClientSharedSchematicChunkPacket(FriendlyByteBuf buffer) {
        this.transferId = buffer.readVarInt();
        this.originalFilename = buffer.readUtf(255);
        this.hash = buffer.readUtf(64);
        this.chunkIndex = buffer.readVarInt();
        this.chunkCount = buffer.readVarInt();
        this.totalSize = buffer.readVarLong();
        this.data = buffer.readByteArray();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(transferId);
        buffer.writeUtf(originalFilename);
        buffer.writeUtf(hash);
        buffer.writeVarInt(chunkIndex);
        buffer.writeVarInt(chunkCount);
        buffer.writeVarLong(totalSize);
        buffer.writeByteArray(data);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientSharedSchematicReceiver.receiveChunk(transferId, originalFilename, hash,
                chunkIndex, chunkCount, totalSize, data));
        context.setPacketHandled(true);
    }
}

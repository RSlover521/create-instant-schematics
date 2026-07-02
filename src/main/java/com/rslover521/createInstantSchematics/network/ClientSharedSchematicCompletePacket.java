package com.rslover521.createInstantSchematics.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ClientSharedSchematicCompletePacket {
    private final int transferId;
    private final String originalFilename;
    private final String hash;
    private final BlockPos anchor;
    private final Rotation rotation;
    private final Mirror mirror;
    private final BlockPos bounds;

    public ClientSharedSchematicCompletePacket(int transferId, String originalFilename, String hash, BlockPos anchor,
                                               Rotation rotation, Mirror mirror, BlockPos bounds) {
        this.transferId = transferId;
        this.originalFilename = originalFilename;
        this.hash = hash;
        this.anchor = anchor;
        this.rotation = rotation;
        this.mirror = mirror;
        this.bounds = bounds;
    }

    public ClientSharedSchematicCompletePacket(FriendlyByteBuf buffer) {
        this.transferId = buffer.readVarInt();
        this.originalFilename = buffer.readUtf(255);
        this.hash = buffer.readUtf(64);
        this.anchor = buffer.readBlockPos();
        this.rotation = buffer.readEnum(Rotation.class);
        this.mirror = buffer.readEnum(Mirror.class);
        this.bounds = buffer.readBlockPos();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(transferId);
        buffer.writeUtf(originalFilename);
        buffer.writeUtf(hash);
        buffer.writeBlockPos(anchor);
        buffer.writeEnum(rotation);
        buffer.writeEnum(mirror);
        buffer.writeBlockPos(bounds);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientSharedSchematicReceiver.complete(transferId, originalFilename, hash, anchor,
                rotation, mirror, bounds));
        context.setPacketHandled(true);
    }
}

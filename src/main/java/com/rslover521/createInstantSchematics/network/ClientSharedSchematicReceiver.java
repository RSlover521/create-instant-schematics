package com.rslover521.createInstantSchematics.network;

import com.rslover521.createInstantSchematics.CreateInstantSchematics;
import com.rslover521.createInstantSchematics.core.ModNetwork;
import com.rslover521.createInstantSchematics.client.SharedSchematicPreview;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

public class ClientSharedSchematicReceiver {
    private static final Map<Integer, Transfer> TRANSFERS = new HashMap<>();

    public static void receiveChunk(int transferId, String originalFilename, String hash, int chunkIndex,
                                    int chunkCount, long totalSize, byte[] data) {
        if (!isValidTransferMetadata(hash, chunkIndex, chunkCount, totalSize, data)) {
            return;
        }

        Transfer transfer = TRANSFERS.computeIfAbsent(transferId,
                ignored -> new Transfer(originalFilename, hash, chunkCount, totalSize));
        if (!transfer.matches(originalFilename, hash, chunkCount, totalSize)) {
            TRANSFERS.put(transferId, transfer = new Transfer(originalFilename, hash, chunkCount, totalSize));
        }

        if (transfer.chunks[chunkIndex] == null) {
            transfer.received++;
            transfer.receivedBytes += data.length;
        }
        transfer.chunks[chunkIndex] = data;
    }

    public static void complete(int transferId, String originalFilename, String hash, BlockPos anchor,
                                Rotation rotation, Mirror mirror, BlockPos bounds) {
        Transfer transfer = TRANSFERS.remove(transferId);
        if (transfer == null || !transfer.hash.equals(hash) || !transfer.originalFilename.equals(originalFilename)
                || transfer.received != transfer.chunkCount || transfer.receivedBytes != transfer.totalSize) {
            return;
        }

        try {
            byte[] bytes = transfer.join();
            String actualHash = sha256(bytes);
            if (!actualHash.equalsIgnoreCase(hash)) {
                CreateInstantSchematics.LOGGER.warn("Rejected shared schematic {}: hash mismatch", originalFilename);
                return;
            }

            String sharedFilename = sharedFilename(originalFilename, hash);
            Path schematicsDirectory = Paths.get("schematics").toAbsolutePath().normalize();
            Path target = schematicsDirectory.resolve(sharedFilename).normalize();
            if (!target.startsWith(schematicsDirectory) || target.getParent() == null
                    || !target.getParent().equals(schematicsDirectory)) {
                return;
            }

            Files.createDirectories(schematicsDirectory);
            if (Files.exists(target)) {
                byte[] existing = Files.readAllBytes(target);
                if (!sha256(existing).equalsIgnoreCase(hash)) {
                    CreateInstantSchematics.LOGGER.warn("Refusing to overwrite shared schematic with mismatched hash: {}",
                            target);
                    return;
                }
            } else {
                Files.write(target, bytes);
            }

            SharedSchematicPreview.show(sharedFilename, anchor, rotation, mirror, bounds);
        } catch (InvalidPathException | IOException | NoSuchAlgorithmException exception) {
            CreateInstantSchematics.LOGGER.warn("Failed to receive shared schematic {}", originalFilename, exception);
        }
    }

    public static void clearTransfers() {
        TRANSFERS.clear();
    }

    private static boolean isValidTransferMetadata(String hash, int chunkIndex, int chunkCount, long totalSize,
                                                   byte[] data) {
        return hash != null
                && hash.matches("[0-9a-fA-F]{64}")
                && chunkCount > 0
                && chunkCount <= 4096
                && chunkIndex >= 0
                && chunkIndex < chunkCount
                && totalSize >= 0
                && totalSize <= ModNetwork.MAX_SHARED_SCHEMATIC_SIZE
                && data != null
                && data.length <= 512 * 1024;
    }

    private static String sharedFilename(String originalFilename, String hash) {
        String baseName = originalFilename;
        int slash = Math.max(baseName.lastIndexOf('/'), baseName.lastIndexOf('\\'));
        if (slash >= 0) {
            baseName = baseName.substring(slash + 1);
        }
        if (baseName.toLowerCase(Locale.ROOT).endsWith(".nbt")) {
            baseName = baseName.substring(0, baseName.length() - 4);
        }

        String cleanName = baseName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        if (cleanName.isBlank()) {
            cleanName = "schematic";
        }
        if (cleanName.length() > 64) {
            cleanName = cleanName.substring(0, 64);
        }

        return "cis_shared_" + cleanName + "_" + hash.substring(0, 8).toLowerCase(Locale.ROOT) + ".nbt";
    }

    private static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static class Transfer {
        private final String originalFilename;
        private final String hash;
        private final int chunkCount;
        private final long totalSize;
        private final byte[][] chunks;
        private int received;
        private long receivedBytes;

        private Transfer(String originalFilename, String hash, int chunkCount, long totalSize) {
            this.originalFilename = originalFilename;
            this.hash = hash;
            this.chunkCount = chunkCount;
            this.totalSize = totalSize;
            this.chunks = new byte[chunkCount][];
        }

        private boolean matches(String originalFilename, String hash, int chunkCount, long totalSize) {
            return this.originalFilename.equals(originalFilename)
                    && this.hash.equals(hash)
                    && this.chunkCount == chunkCount
                    && this.totalSize == totalSize;
        }

        private byte[] join() throws IOException {
            ByteArrayOutputStream output = new ByteArrayOutputStream(Math.toIntExact(totalSize));
            for (byte[] chunk : chunks) {
                if (chunk == null) {
                    throw new IOException("Missing shared schematic chunk");
                }
                output.write(chunk);
            }
            byte[] bytes = output.toByteArray();
            if (bytes.length != totalSize) {
                throw new IOException("Shared schematic size mismatch");
            }
            Arrays.fill(chunks, null);
            return bytes;
        }
    }
}

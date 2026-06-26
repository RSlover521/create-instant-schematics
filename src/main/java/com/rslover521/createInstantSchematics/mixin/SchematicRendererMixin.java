package com.rslover521.createInstantSchematics.mixin;

import com.simibubi.create.content.schematics.SchematicWorld;
import com.simibubi.create.content.schematics.client.SchematicRenderer;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = SchematicRenderer.class, remap = false)
public class SchematicRendererMixin {
    @Shadow
    protected SchematicWorld schematic;

    @Redirect(
            method = "drawLayer",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/core/BlockPos;betweenClosed(IIIIII)Ljava/lang/Iterable;",
                    remap = true))
    private Iterable<BlockPos> createInstantSchematics$onlyRenderStoredBlocks(int minX, int minY, int minZ,
                                                                               int maxX, int maxY, int maxZ) {
        return schematic.getAllPositions();
    }
}

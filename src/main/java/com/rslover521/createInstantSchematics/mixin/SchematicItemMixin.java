package com.rslover521.createInstantSchematics.mixin;

import com.simibubi.create.content.schematics.SchematicItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(value = SchematicItem.class, remap = false)
public class SchematicItemMixin {
    @ModifyConstant(method = "loadSchematic", constant = @Constant(longValue = 536870912L), require = 0)
    private static long createInstantSchematics$increaseSchematicNbtLimit(long originalLimit) {
        return 1073741824L;
    }
}

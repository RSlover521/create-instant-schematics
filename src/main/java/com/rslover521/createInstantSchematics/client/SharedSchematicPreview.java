package com.rslover521.createInstantSchematics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rslover521.createInstantSchematics.CreateInstantSchematics;
import com.rslover521.createInstantSchematics.network.ClientSharedSchematicReceiver;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.schematics.SchematicItem;
import com.simibubi.create.content.schematics.SchematicWorld;
import com.simibubi.create.content.schematics.client.SchematicRenderer;
import com.simibubi.create.content.schematics.client.SchematicTransformation;
import com.simibubi.create.foundation.render.SuperRenderTypeBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = CreateInstantSchematics.MODID, value = Dist.CLIENT)
public class SharedSchematicPreview {
    private static final ResourceLocation SCHEMATIC_ID = new ResourceLocation("create", "schematic");
    private static Preview activePreview;

    public static void show(String filename, BlockPos anchor, Rotation rotation, Mirror mirror, BlockPos bounds) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        Item schematicItem = ForgeRegistries.ITEMS.getValue(SCHEMATIC_ID);
        if (schematicItem == null) {
            return;
        }

        ItemStack stack = new ItemStack(schematicItem);
        stack.setTag(createSchematicTag(filename, anchor, rotation, mirror, bounds));

        try {
            activePreview = Preview.create(stack, anchor, rotation, mirror, bounds);
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("Received shared schematic: " + filename),
                        false);
            }
        } catch (RuntimeException exception) {
            CreateInstantSchematics.LOGGER.warn("Failed to render shared schematic {}", filename, exception);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || activePreview == null) {
            return;
        }
        activePreview.tick();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (activePreview == null || event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        SuperRenderTypeBuffer buffer = SuperRenderTypeBuffer.getInstance();
        Vec3 camera = event.getCamera().getPosition();
        activePreview.render(poseStack, buffer, camera);
        buffer.draw();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        activePreview = null;
        ClientSharedSchematicReceiver.clearTransfers();
    }

    private static CompoundTag createSchematicTag(String filename, BlockPos anchor, Rotation rotation, Mirror mirror,
                                                  BlockPos bounds) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Deployed", true);
        tag.putString("Owner", "cis_shared");
        tag.putString("File", filename);
        tag.put("Anchor", NbtUtils.writeBlockPos(anchor));
        tag.putString("Rotation", rotation.name());
        tag.putString("Mirror", mirror.name());
        tag.put("Bounds", writeVec3i(bounds));
        return tag;
    }

    private static ListTag writeVec3i(Vec3i vec) {
        ListTag tag = new ListTag();
        tag.add(IntTag.valueOf(vec.getX()));
        tag.add(IntTag.valueOf(vec.getY()));
        tag.add(IntTag.valueOf(vec.getZ()));
        return tag;
    }

    private static class Preview {
        private final SchematicTransformation transformation;
        private final List<SchematicRenderer> renderers;

        private Preview(SchematicTransformation transformation, List<SchematicRenderer> renderers) {
            this.transformation = transformation;
            this.renderers = renderers;
        }

        private static Preview create(ItemStack stack, BlockPos anchor, Rotation rotation, Mirror mirror, BlockPos bounds) {
            Minecraft minecraft = Minecraft.getInstance();
            StructureTemplate template = SchematicItem.loadSchematic(
                    minecraft.level.holderLookup(Registries.BLOCK), stack);
            Vec3i size = template.getSize();
            if (Vec3i.ZERO.equals(size)) {
                throw new IllegalStateException("Shared schematic has no blocks");
            }

            StructurePlaceSettings settings = new StructurePlaceSettings()
                    .setRotation(rotation)
                    .setMirror(mirror);
            AABB previewBounds = new AABB(0, 0, 0, bounds.getX(), bounds.getY(), bounds.getZ());
            SchematicTransformation transformation = new SchematicTransformation();
            transformation.init(anchor, settings, previewBounds);

            List<SchematicRenderer> renderers = createRenderers(template, size);
            return new Preview(transformation, renderers);
        }

        private static List<SchematicRenderer> createRenderers(StructureTemplate template, Vec3i size) {
            Minecraft minecraft = Minecraft.getInstance();
            SchematicWorld normal = new SchematicWorld(minecraft.level);
            SchematicWorld frontBack = new SchematicWorld(minecraft.level);
            SchematicWorld leftRight = new SchematicWorld(minecraft.level);
            StructurePlaceSettings settings = new StructurePlaceSettings();

            template.placeInWorld(normal, BlockPos.ZERO, BlockPos.ZERO, settings, normal.getRandom(), 2);

            settings.setMirror(Mirror.FRONT_BACK);
            BlockPos frontBackAnchor = BlockPos.ZERO.east(size.getX() - 1);
            template.placeInWorld(frontBack, frontBackAnchor, frontBackAnchor, settings, frontBack.getRandom(), 2);
            transformBlockEntities(frontBack, settings);

            settings.setMirror(Mirror.LEFT_RIGHT);
            BlockPos leftRightAnchor = BlockPos.ZERO.south(size.getZ() - 1);
            template.placeInWorld(leftRight, leftRightAnchor, leftRightAnchor, settings, leftRight.getRandom(), 2);
            transformBlockEntities(leftRight, settings);

            List<SchematicRenderer> renderers = new ArrayList<>(3);
            renderers.add(display(normal));
            renderers.add(display(frontBack));
            renderers.add(display(leftRight));
            return renderers;
        }

        private static void transformBlockEntities(SchematicWorld world, StructurePlaceSettings settings) {
            StructureTransform transform = new StructureTransform(settings.getRotationPivot(), net.minecraft.core.Direction.Axis.Y,
                    Rotation.NONE, settings.getMirror());
            for (BlockEntity blockEntity : world.getRenderedBlockEntities()) {
                transform.apply(blockEntity);
            }
        }

        private static SchematicRenderer display(SchematicWorld world) {
            SchematicRenderer renderer = new SchematicRenderer();
            renderer.display(world);
            return renderer;
        }

        private void tick() {
            transformation.tick();
            renderers.forEach(SchematicRenderer::tick);
        }

        private void render(PoseStack poseStack, SuperRenderTypeBuffer buffer, Vec3 camera) {
            poseStack.pushPose();
            transformation.applyTransformations(poseStack, camera);
            rendererForCurrentMirror().render(poseStack, buffer);
            poseStack.popPose();
        }

        private SchematicRenderer rendererForCurrentMirror() {
            float partialTicks = com.simibubi.create.foundation.utility.AnimationTickHolder.getPartialTicks();
            boolean leftRightFlipped = transformation.getScaleLR().getValue(partialTicks) < 0;
            boolean frontBackFlipped = transformation.getScaleFB().getValue(partialTicks) < 0;
            if (leftRightFlipped && !frontBackFlipped) {
                return renderers.get(2);
            }
            if (frontBackFlipped && !leftRightFlipped) {
                return renderers.get(1);
            }
            return renderers.get(0);
        }
    }
}

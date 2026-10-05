package dungpipeaddon;

import dungpipeaddon.block.ToiletBlock;
import dungpipeaddon.tile.ToiletTileEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.block.state.IBlockState;
import net.minecraftforge.fluids.FluidStack;
import org.lwjgl.opengl.GL11;

/** Renders the last fluid transferred through an open toilet as a flat level. */
public class ToiletFluidRenderer extends TileEntitySpecialRenderer<ToiletTileEntity> {
    @Override
    public void render(ToiletTileEntity tile, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        FluidStack fluid = tile.getFluidVisual();
        if (fluid == null || fluid.getFluid() == null || tile.getWorld() == null) {
            return;
        }
        IBlockState state = tile.getWorld().getBlockState(tile.getPos());
        if (!(state.getBlock() instanceof ToiletBlock) || !state.getValue(ToiletBlock.OPEN)) {
            return;
        }

        net.minecraft.util.ResourceLocation still = fluid.getFluid().getStill(fluid);
        if (still == null) {
            return;
        }
        TextureAtlasSprite sprite = Minecraft.getMinecraft().getTextureMapBlocks()
                .getAtlasSprite(still.toString());
        if (sprite == null) {
            return;
        }

        bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        // The vertex tint is the fluid's ARGB color. Do not multiply it by a
        // second translucent GL color, otherwise colored fluids become too
        // dark and fluids with a transparent legacy alpha can disappear.
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        double min = 4.0D / 16.0D;
        double max = 12.0D / 16.0D;
        // One pixel below the previous level, matching the basin depth.
        double level = 6.08D / 16.0D;
        int fluidColor = fluid.getFluid().getColor(fluid);
        int red = (fluidColor >> 16) & 0xFF;
        int green = (fluidColor >> 8) & 0xFF;
        int blue = fluidColor & 0xFF;
        int colorAlpha = (fluidColor >>> 24) & 0xFF;
        // A few legacy fluids provide an RGB-only color. Keep those visible
        // while still respecting an explicitly supplied alpha channel.
        int vertexAlpha = colorAlpha == 0 ? 230 : colorAlpha;
        renderFluidQuad(sprite, min, max, level, red, green, blue, vertexAlpha);

        // Forge fluids can provide a separate overlay (for example a generic
        // liquid template with a fluid-specific tint). Render it over the
        // still texture so the basin shows the complete fluid appearance.
        net.minecraft.util.ResourceLocation overlay = fluid.getFluid().getOverlay();
        if (overlay != null) {
            TextureAtlasSprite overlaySprite = Minecraft.getMinecraft().getTextureMapBlocks()
                    .getAtlasSprite(overlay.toString());
            if (overlaySprite != null) {
                renderFluidQuad(overlaySprite, min, max, level + 0.001D, 255, 255, 255, 255);
            }
        }

        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    private void renderFluidQuad(TextureAtlasSprite sprite, double min, double max, double level,
                                 int red, int green, int blue, int alpha) {
        double u0 = sprite.getMinU();
        double u1 = sprite.getMaxU();
        double v0 = sprite.getMinV();
        double v1 = sprite.getMaxV();
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);
        buffer.pos(min, level, min).tex(u0, v0).color(red, green, blue, alpha).normal(0, 1, 0).endVertex();
        buffer.pos(min, level, max).tex(u0, v1).color(red, green, blue, alpha).normal(0, 1, 0).endVertex();
        buffer.pos(max, level, max).tex(u1, v1).color(red, green, blue, alpha).normal(0, 1, 0).endVertex();
        buffer.pos(max, level, min).tex(u1, v0).color(red, green, blue, alpha).normal(0, 1, 0).endVertex();
        Tessellator.getInstance().draw();
    }
}

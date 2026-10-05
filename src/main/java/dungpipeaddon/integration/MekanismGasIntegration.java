package dungpipeaddon.integration;

import dungpipeaddon.tile.ToiletTileEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.Loader;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nullable;

/**
 * Optional Mekanism bridge. Mekanism is detected through Forge's loader and
 * the API calls are isolated in {@link MekanismGasBridge}, which is only
 * reached when the mod is actually present.
 */
public final class MekanismGasIntegration {
    private MekanismGasIntegration() {
    }

    /** Returns Mekanism's gas capability, or null when Mekanism is unavailable. */
    @Nullable
    public static Capability<?> getGasCapability() {
        if (!Loader.isModLoaded("mekanism")) {
            return null;
        }
        try {
            return MekanismGasBridge.getGasCapability();
        } catch (LinkageError ignored) {
            return null;
        }
    }

    /**
     * Creates the gas channel handler lazily.  The returned object implements
     * Mekanism's IGasHandler only when that optional mod is present.
     */
    @Nullable
    public static Object createGasHandler(ToiletTileEntity owner) {
        if (!Loader.isModLoaded("mekanism")) {
            return null;
        }
        try {
            return MekanismGasBridge.createGasHandler(owner);
        } catch (LinkageError ignored) {
            return null;
        }
    }

    /**
     * Transfers one tick's gas quota between two adjacent handlers.  The
     * Mekanism API types stay inside the optional bridge so the addon still
     * loads when Mekanism is absent.
     */
    public static int transfer(TileEntity source, EnumFacing sourceSide,
                               TileEntity target, EnumFacing targetSide,
                               int maxAmount) {
        if (source == null || target == null || maxAmount <= 0
                || !Loader.isModLoaded("mekanism")) {
            return 0;
        }
        try {
            return MekanismGasBridge.transfer(source, sourceSide, target, targetSide, maxAmount);
        } catch (LinkageError ignored) {
            return 0;
        }
    }
}

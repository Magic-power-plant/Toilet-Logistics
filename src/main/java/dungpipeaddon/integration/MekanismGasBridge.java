package dungpipeaddon.integration;

import dungpipeaddon.tile.ToiletTileEntity;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IGasHandler;
import mekanism.common.capabilities.Capabilities;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

/**
 * Direct Mekanism API calls kept in a class that is only entered when Forge
 * reports Mekanism as installed. The addon itself keeps Mekanism compile-only.
 */
public final class MekanismGasBridge {
    private MekanismGasBridge() {
    }

    public static Capability<?> getGasCapability() {
        return Capabilities.GAS_HANDLER_CAPABILITY;
    }

    public static Object createGasHandler(ToiletTileEntity owner) {
        return new dungpipeaddon.capability.ToiletGasHandler(owner);
    }

    public static int transfer(TileEntity source, EnumFacing sourceSide,
                               TileEntity target, EnumFacing targetSide,
                               int maxAmount) {
        IGasHandler sourceHandler = source.getCapability(
                Capabilities.GAS_HANDLER_CAPABILITY, sourceSide
        );
        IGasHandler targetHandler = target.getCapability(
                Capabilities.GAS_HANDLER_CAPABILITY, targetSide
        );
        if (sourceHandler == null || targetHandler == null) {
            return 0;
        }

        GasStack available = sourceHandler.drawGas(sourceSide, maxAmount, false);
        if (available == null || available.amount <= 0 || available.getGas() == null) {
            return 0;
        }
        int accepted = targetHandler.receiveGas(targetSide, available.copy(), false);
        if (accepted <= 0) {
            return 0;
        }
        accepted = Math.min(accepted, available.amount);

        GasStack drawn = sourceHandler.drawGas(sourceSide, accepted, true);
        if (drawn == null || drawn.amount <= 0 || drawn.getGas() == null) {
            return 0;
        }
        int amount = Math.min(drawn.amount, accepted);
        drawn.amount = amount;
        int actualAccepted = targetHandler.receiveGas(targetSide, drawn.copy(), false);
        if (actualAccepted <= 0) {
            return 0;
        }
        actualAccepted = Math.min(actualAccepted, amount);
        drawn.amount = actualAccepted;
        return targetHandler.receiveGas(targetSide, drawn, true);
    }
}

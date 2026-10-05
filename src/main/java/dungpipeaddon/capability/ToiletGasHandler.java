package dungpipeaddon.capability;

import dungpipeaddon.data.ToiletPairData;
import dungpipeaddon.tile.ToiletTileEntity;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.GasTankInfo;
import mekanism.api.gas.IGasHandler;
import mekanism.common.capabilities.Capabilities;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Routes Mekanism legacy gas capability calls to peer toilets' top handlers. */
public class ToiletGasHandler implements IGasHandler {
    private final ToiletTileEntity owner;

    public ToiletGasHandler(ToiletTileEntity owner) {
        this.owner = owner;
    }

    @Override
    public int receiveGas(EnumFacing side, GasStack stack, boolean simulate) {
        if (stack == null || stack.amount <= 0) {
            return 0;
        }
        int received = 0;
        GasStack remaining = stack.copy();
        for (IGasHandler target : targetHandlers()) {
            int accepted = target.receiveGas(EnumFacing.DOWN, remaining, simulate);
            if (accepted <= 0) {
                continue;
            }
            received += accepted;
            if (accepted >= remaining.amount) {
                break;
            }
            remaining.amount -= accepted;
        }
        return received;
    }

    @Override
    public GasStack drawGas(EnumFacing side, int amount, boolean simulate) {
        if (amount <= 0) {
            return null;
        }
        for (IGasHandler target : targetHandlers()) {
            GasStack drawn = target.drawGas(EnumFacing.DOWN, amount, simulate);
            if (drawn != null && drawn.amount > 0) {
                return drawn;
            }
        }
        return null;
    }

    @Override
    public boolean canReceiveGas(EnumFacing side, Gas gas) {
        for (IGasHandler target : targetHandlers()) {
            if (target.canReceiveGas(EnumFacing.DOWN, gas)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canDrawGas(EnumFacing side, Gas gas) {
        for (IGasHandler target : targetHandlers()) {
            if (target.canDrawGas(EnumFacing.DOWN, gas)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public GasTankInfo[] getTankInfo() {
        List<GasTankInfo> infos = new ArrayList<>();
        for (IGasHandler target : targetHandlers()) {
            GasTankInfo[] targetInfos = target.getTankInfo();
            if (targetInfos != null) {
                for (GasTankInfo info : targetInfos) {
                    if (info != null) {
                        infos.add(info);
                    }
                }
            }
        }
        return infos.toArray(new GasTankInfo[infos.size()]);
    }

    private List<IGasHandler> targetHandlers() {
        List<IGasHandler> handlers = new ArrayList<>();
        if (owner.getWorld() == null || owner.getPairId() == null) {
            return handlers;
        }

        ToiletPairData data = ToiletPairData.get(owner.getWorld());
        UUID pairId = owner.getPairId();
        for (ToiletPairData.ToiletTarget target : data.findTargets(owner.getWorld(), owner.getPos(), pairId, false)) {
            BlockPos above = target.pos.up();
            TileEntity tile = target.world.getTileEntity(above);
            if (tile == null || tile instanceof ToiletTileEntity) {
                continue;
            }
            IGasHandler handler = tile.getCapability(Capabilities.GAS_HANDLER_CAPABILITY, EnumFacing.DOWN);
            if (handler != null) {
                handlers.add(handler);
            }
        }
        return handlers;
    }
}

package dungpipeaddon.capability;

import dungpipeaddon.data.ToiletPairData;
import dungpipeaddon.tile.ToiletTileEntity;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.fluids.capability.templates.EmptyFluidHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Routes Forge fluid capability calls to the rear-side handlers of peer toilets. */
public class ToiletFluidHandler implements IFluidHandler {
    private final ToiletTileEntity owner;

    public ToiletFluidHandler(ToiletTileEntity owner) {
        this.owner = owner;
    }

    @Override
    public IFluidTankProperties[] getTankProperties() {
        List<IFluidTankProperties> properties = new ArrayList<>();
        for (IFluidHandler handler : targetHandlers()) {
            IFluidTankProperties[] targetProperties = handler.getTankProperties();
            if (targetProperties != null) {
                for (IFluidTankProperties property : targetProperties) {
                    if (property != null) {
                        properties.add(property);
                    }
                }
            }
        }
        return properties.isEmpty()
                ? EmptyFluidHandler.EMPTY_TANK_PROPERTIES_ARRAY
                : properties.toArray(new IFluidTankProperties[properties.size()]);
    }

    @Override
    public int fill(FluidStack resource, boolean doFill) {
        if (resource == null || resource.amount <= 0) {
            return 0;
        }
        int filled = 0;
        FluidStack remaining = resource.copy();
        for (TargetHandler target : targetHandlersWithOwners()) {
            int accepted = target.handler.fill(remaining, doFill);
            if (accepted <= 0) {
                continue;
            }
            filled += accepted;
            if (doFill) {
                owner.recordFluidTransfer(target.toilet, new FluidStack(resource, accepted));
            }
            if (accepted >= remaining.amount) {
                break;
            }
            remaining.amount -= accepted;
        }
        return filled;
    }

    @Override
    public FluidStack drain(FluidStack resource, boolean doDrain) {
        if (resource == null || resource.amount <= 0) {
            return null;
        }
        for (TargetHandler target : targetHandlersWithOwners()) {
            FluidStack drained = target.handler.drain(resource, doDrain);
            if (drained != null && drained.amount > 0) {
                if (doDrain) {
                    owner.recordFluidTransfer(target.toilet, drained);
                }
                return drained;
            }
        }
        return null;
    }

    @Override
    public FluidStack drain(int maxDrain, boolean doDrain) {
        if (maxDrain <= 0) {
            return null;
        }
        for (TargetHandler target : targetHandlersWithOwners()) {
            FluidStack drained = target.handler.drain(maxDrain, doDrain);
            if (drained != null && drained.amount > 0) {
                if (doDrain) {
                    owner.recordFluidTransfer(target.toilet, drained);
                }
                return drained;
            }
        }
        return null;
    }

    private List<IFluidHandler> targetHandlers() {
        List<IFluidHandler> handlers = new ArrayList<>();
        for (TargetHandler target : targetHandlersWithOwners()) {
            handlers.add(target.handler);
        }
        return handlers;
    }

    private List<TargetHandler> targetHandlersWithOwners() {
        List<TargetHandler> handlers = new ArrayList<>();
        if (owner.getWorld() == null || owner.getPairId() == null) {
            return handlers;
        }

        ToiletPairData data = ToiletPairData.get(owner.getWorld());
        UUID pairId = owner.getPairId();
        for (ToiletPairData.ToiletTarget target : data.findTargets(owner.getWorld(), owner.getPos(), pairId, false)) {
            IBlockState state = target.world.getBlockState(target.pos);
            EnumFacing back = state.getValue(dungpipeaddon.block.ToiletBlock.FACING).getOpposite();
            BlockPos behind = target.pos.offset(back);
            TileEntity tile = target.world.getTileEntity(behind);
            if (tile == null || tile instanceof ToiletTileEntity) {
                continue;
            }
            IFluidHandler handler = tile.getCapability(
                    CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, back.getOpposite()
            );
            if (handler != null) {
                handlers.add(new TargetHandler((ToiletTileEntity) target.world.getTileEntity(target.pos), handler));
            }
        }
        return handlers;
    }

    private static final class TargetHandler {
        private final ToiletTileEntity toilet;
        private final IFluidHandler handler;

        private TargetHandler(ToiletTileEntity toilet, IFluidHandler handler) {
            this.toilet = toilet;
            this.handler = handler;
        }
    }
}

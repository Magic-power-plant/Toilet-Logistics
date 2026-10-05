package dungpipeaddon.tile;

import dungpipeaddon.block.ToiletBlock;
import dungpipeaddon.capability.ToiletFluidHandler;
import dungpipeaddon.capability.ToiletItemHandler;
import dungpipeaddon.data.ToiletPairData;
import dungpipeaddon.integration.MekanismGasIntegration;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityHopper;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraft.block.state.IBlockState;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Stores the UUID shared by a pair of colored toilets. */
public class ToiletTileEntity extends TileEntity implements net.minecraft.util.ITickable {
    @Nullable
    private UUID pairId;
    @Nullable
    private FluidStack fluidVisual;
    @Nullable
    private ToiletItemHandler itemHandler;
    @Nullable
    private ToiletFluidHandler fluidHandler;
    @Nullable
    private Object gasHandler;
    /** Prevents a newly launched item from being captured again immediately. */
    private final Map<UUID, Integer> incomingItemCooldowns = new HashMap<>();

    @Nullable
    public UUID getPairId() {
        return pairId;
    }

    public void setPairId(@Nullable UUID pairId) {
        if (this.pairId == null ? pairId == null : this.pairId.equals(pairId)) {
            return;
        }
        UUID oldPairId = this.pairId;
        this.pairId = pairId;
        markDirty();
        if (world != null && !world.isRemote) {
            ToiletPairData data = ToiletPairData.get(world);
            if (oldPairId != null) {
                data.unregister(world, pos, oldPairId);
            }
            if (pairId != null) {
                data.register(world, pos, pairId);
                setSharedFluidCache(data.getEffectiveFluid(pairId));
            }
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
        }
    }

    @Nullable
    public FluidStack getFluidVisual() {
        return fluidVisual == null ? null : fluidVisual.copy();
    }

    /** Returns only the fluid that a handheld container is allowed to drain. */
    @Nullable
    public FluidStack getManualFluidVisual() {
        if (world != null && world.isRemote) {
            // The client has only a rendering cache.  It must never expose
            // that cache as an extractable tank because the server owns the
            // UUID channel state and may reject the drain.
            return null;
        }
        if (pairId != null && world != null) {
            return ToiletPairData.get(world).getManualFluid(pairId);
        }
        return fluidVisual == null ? null : fluidVisual.copy();
    }

    public boolean hasFluidVisual() {
        return fluidVisual != null && fluidVisual.amount > 0 && fluidVisual.getFluid() != null;
    }

    public void setFluidVisual(@Nullable FluidStack fluid) {
        FluidStack next = fluid == null || fluid.getFluid() == null ? null : fluid.copy();
        if (next != null) {
            // The level is visual state, not a tank. Keep a full display level
            // regardless of the amount moved through the channel.
            next.amount = 1000;
        }
        if (sameFluid(fluidVisual, next)) {
            return;
        }
        fluidVisual = next;
        markDirty();
        if (world != null) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
            world.checkLight(pos);
        }
    }

    /** Updates the per-tile cache from the shared channel state without writing it back. */
    public void setSharedFluidCache(@Nullable FluidStack fluid) {
        FluidStack next = fluid == null || fluid.getFluid() == null ? null : fluid.copy();
        if (next != null) {
            next.amount = 1000;
        }
        if (sameFluid(fluidVisual, next)) {
            return;
        }
        fluidVisual = next;
        markDirty();
        if (world != null) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
            world.checkLight(pos);
        }
    }

    public void clearFluidVisual() {
        if (world != null && !world.isRemote && pairId != null) {
            ToiletPairData.get(world).clearChannelFluid(world, pairId);
        } else {
            setFluidVisual(null);
        }
    }

    /** Records a channel transfer on both the sending and receiving toilets. */
    public void recordFluidTransfer(ToiletTileEntity target, FluidStack fluid) {
        if (world != null && !world.isRemote && pairId != null) {
            ToiletPairData.get(world).setTransferFluid(world, pairId, fluid);
        } else {
            setFluidVisual(fluid);
            if (target != null) {
                target.setFluidVisual(fluid);
            }
        }
    }

    /** Used by a handheld container, which fills the display without a tank. */
    public void recordManualFluid(FluidStack fluid) {
        if (world != null && !world.isRemote && pairId != null) {
            ToiletPairData.get(world).setManualFluid(world, pairId, fluid);
        } else {
            setFluidVisual(fluid);
        }
    }

    /** Clears only the bucket/container fluid, restoring a transfer display when present. */
    public void clearManualFluid() {
        if (world != null && !world.isRemote && pairId != null) {
            ToiletPairData.get(world).clearManualFluid(world, pairId);
        } else {
            setFluidVisual(null);
        }
    }

    /** Lets a bucket or another Forge fluid container interact with the display. */
    public boolean interactWithFluidContainer(net.minecraft.entity.player.EntityPlayer player, EnumHand hand) {
        return FluidUtil.interactWithFluidHandler(player, hand, new VisualFluidHandler(this));
    }

    /** Expose only the three faces reserved for the corresponding transport. */
    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        // A number of 1.12 pipes first probe with a null side and then make a
        // concrete side query when they connect. Allow that discovery probe,
        // while concrete faces remain restricted to the dedicated ports.
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY
                && (facing == null || facing == EnumFacing.DOWN)) {
            return true;
        }
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY
                && (facing == null || isFluidSide(facing))) {
            return true;
        }
        Capability<?> gasCapability = MekanismGasIntegration.getGasCapability();
        if (gasCapability != null && capability == gasCapability
                && (facing == null || facing == EnumFacing.UP)) {
            return true;
        }
        return super.hasCapability(capability, facing);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY
                && (facing == null || facing == EnumFacing.DOWN)) {
            if (itemHandler == null) {
                itemHandler = new ToiletItemHandler(this);
            }
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(itemHandler);
        }
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY
                && (facing == null || isFluidSide(facing))) {
            if (fluidHandler == null) {
                fluidHandler = new ToiletFluidHandler(this);
            }
            return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(fluidHandler);
        }
        Capability<?> gasCapability = MekanismGasIntegration.getGasCapability();
        if (gasCapability != null && capability == gasCapability
                && (facing == null || facing == EnumFacing.UP)) {
            if (gasHandler == null) {
                gasHandler = MekanismGasIntegration.createGasHandler(this);
            }
            if (gasHandler != null) {
                return (T) gasHandler;
            }
        }
        return super.getCapability(capability, facing);
    }

    /** Send UUID/water changes immediately after a channel or teleport action. */
    @Override
    @Nullable
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 1, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        readFromNBT(packet.getNbtCompound());
    }

    private boolean isFluidSide(@Nullable EnumFacing facing) {
        if (facing == null || world == null) {
            return false;
        }
        IBlockState state = world.getBlockState(pos);
        return state.getBlock() instanceof ToiletBlock
                && facing == state.getValue(ToiletBlock.FACING).getOpposite();
    }

    public void unregisterPair() {
        if (world != null && !world.isRemote && pairId != null) {
            ToiletPairData.get(world).unregister(world, pos, pairId);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        pairId = compound.hasUniqueId("pairId") ? compound.getUniqueId("pairId") : null;
        fluidVisual = compound.hasKey("fluidVisual", 10)
                ? FluidStack.loadFluidStackFromNBT(compound.getCompoundTag("fluidVisual"))
                : null;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        if (pairId != null) {
            compound.setUniqueId("pairId", pairId);
        }
        if (fluidVisual != null && fluidVisual.getFluid() != null) {
            compound.setTag("fluidVisual", fluidVisual.writeToNBT(new NBTTagCompound()));
        }
        return compound;
    }

    /** Include addon fields in the chunk update tag as well as saved NBT. */
    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (world != null && !world.isRemote && pairId != null) {
            ToiletPairData data = ToiletPairData.get(world);
            data.register(world, pos, pairId);
            FluidStack shared = data.getEffectiveFluid(pairId);
            // Migrate the pre-channel per-tile display saved by older addon
            // versions into the UUID state once, then use the channel forever.
            if (shared == null && fluidVisual != null) {
                data.setTransferFluid(world, pairId, fluidVisual);
            } else {
                setSharedFluidCache(shared);
            }
        }
    }

    @Override
    public void update() {
        if (world == null || world.isRemote) {
            return;
        }
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof ToiletBlock) || !state.getValue(ToiletBlock.OPEN)) {
            return;
        }

        tickIncomingItemCooldowns();

        // Use the vanilla hopper helper so the X/Z footprint and vertical
        // reach stay exactly aligned with 1.12.2's item capture behavior.
        List<EntityItem> items = TileEntityHopper.getCaptureItems(
                world,
                pos.getX() + 0.5D,
                pos.getY() + 0.5D,
                pos.getZ() + 0.5D
        );
        for (EntityItem item : items) {
            if (item.isDead || item.getItem().isEmpty()) {
                continue;
            }
            if (incomingItemCooldowns.containsKey(item.getUniqueID())) {
                continue;
            }
            if (pairId != null) {
                ToiletPairData.ToiletTarget target = ToiletPairData.get(world)
                        .findRandomOpenTarget(world, pos, pairId, world.rand);
                if (target != null) {
                    moveItemToTarget(item, target);
                }
            } else {
                absorbIntoBelow(item);
            }
        }
    }

    private void absorbIntoBelow(EntityItem item) {
        TileEntity below = world.getTileEntity(pos.down());
        if (below == null || below instanceof ToiletTileEntity) {
            return;
        }
        IItemHandler handler = below.getCapability(
                CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.UP
        );
        if (handler == null) {
            return;
        }
        ItemStack remainder = item.getItem().copy();
        for (int slot = 0; slot < handler.getSlots() && !remainder.isEmpty(); slot++) {
            remainder = handler.insertItem(slot, remainder, false);
        }
        if (remainder.isEmpty()) {
            item.setDead();
        } else {
            item.setItem(remainder);
        }
    }

    private void moveItemToTarget(EntityItem item, ToiletPairData.ToiletTarget target) {
        // The receiving toilet is the mouth of the channel. Replace the
        // source entity with a fresh entity at the opening, then launch it so
        // the first visible position is the toilet itself rather than a point
        // several blocks above it.
        double x = target.pos.getX() + 0.5D;
        double y = target.pos.getY() + 1.01D;
        double z = target.pos.getZ() + 0.5D;
        // Vanilla EntityItem drag and gravity carry this arc back down to the
        // toilet's Y level. The horizontal speed keeps the landing inside the
        // requested 5x5 area around the target toilet.
        double horizontalX = (world.rand.nextDouble() * 2.0D - 1.0D) * 0.18D;
        double horizontalZ = (world.rand.nextDouble() * 2.0D - 1.0D) * 0.18D;
        double vertical = 0.34D + world.rand.nextDouble() * 0.04D;
        EntityItem launched = new EntityItem(target.world, x, y, z, item.getItem().copy());
        launched.setPickupDelay(10);
        TileEntity targetTile = target.world.getTileEntity(target.pos);
        if (targetTile instanceof ToiletTileEntity
                && !((ToiletTileEntity) targetTile).markIncomingItem(launched)) {
            return;
        }
        launched.motionX = horizontalX;
        launched.motionY = vertical;
        launched.motionZ = horizontalZ;
        if (target.world.spawnEntity(launched)) {
            item.setDead();
        }
    }

    private boolean markIncomingItem(EntityItem item) {
        if (incomingItemCooldowns.containsKey(item.getUniqueID())) {
            return false;
        }
        incomingItemCooldowns.put(item.getUniqueID(), 20);
        return true;
    }

    private void tickIncomingItemCooldowns() {
        if (incomingItemCooldowns.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Integer>> iterator = incomingItemCooldowns.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                iterator.remove();
            } else {
                entry.setValue(remaining);
            }
        }
    }

    private static boolean sameFluid(@Nullable FluidStack first, @Nullable FluidStack second) {
        if (first == null || second == null) {
            return first == second;
        }
        return first.isFluidStackIdentical(second);
    }

    private static final class VisualFluidHandler implements IFluidHandler {
        private final ToiletTileEntity owner;

        private VisualFluidHandler(ToiletTileEntity owner) {
            this.owner = owner;
        }

        @Override
        public IFluidTankProperties[] getTankProperties() {
            return new IFluidTankProperties[]{new IFluidTankProperties() {
                @Override
                public FluidStack getContents() {
                    return owner.getManualFluidVisual();
                }

                @Override
                public int getCapacity() {
                    return 1000;
                }

                @Override
                public boolean canFill() {
                    return true;
                }

                @Override
                public boolean canDrain() {
                    return owner.getManualFluidVisual() != null;
                }

                @Override
                public boolean canFillFluidType(FluidStack resource) {
                    return resource != null && resource.getFluid() != null;
                }

                @Override
                public boolean canDrainFluidType(FluidStack resource) {
                    FluidStack contents = owner.getManualFluidVisual();
                    return contents != null && resource != null && contents.isFluidEqual(resource);
                }
            }};
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            if (resource == null || resource.amount <= 0 || resource.getFluid() == null) {
                return 0;
            }
            if (doFill) {
                owner.recordManualFluid(resource);
            }
            return Math.min(1000, resource.amount);
        }

        @Override
        public FluidStack drain(FluidStack resource, boolean doDrain) {
            if (resource == null || resource.amount <= 0) {
                return null;
            }
            FluidStack contents = owner.getManualFluidVisual();
            if (contents == null || !contents.isFluidEqual(resource)) {
                return null;
            }
            FluidStack drained = new FluidStack(contents, Math.min(resource.amount, 1000));
            if (doDrain) {
                owner.clearManualFluid();
            }
            return drained;
        }

        @Override
        public FluidStack drain(int maxDrain, boolean doDrain) {
            if (maxDrain <= 0) {
                return null;
            }
            FluidStack contents = owner.getManualFluidVisual();
            if (contents == null) {
                return null;
            }
            FluidStack drained = new FluidStack(contents, Math.min(maxDrain, 1000));
            if (doDrain) {
                owner.clearManualFluid();
            }
            return drained;
        }
    }
}

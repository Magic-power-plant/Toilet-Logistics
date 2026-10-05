package dungpipeaddon.tile;

import dungpipeaddon.block.ExtractionPipeBlock;
import dungpipeaddon.integration.MekanismGasIntegration;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

/**
 * Moves at most one full stack (up to 64 items) per server tick.
 *
 * The block's facing is the copied Sewer Pipe model's input opening. Items
 * move from that side toward the opposite output side, including UP/DOWN.
 */
public class ExtractionPipeTileEntity extends TileEntity implements ITickable {
    private static final int TRANSFER_LIMIT = 64;
    private static final int FLUID_TRANSFER_LIMIT = 64000;
    private static final int GAS_TRANSFER_LIMIT = 64000;

    @Override
    public void update() {
        if (world == null || world.isRemote) {
            return;
        }

        // The base pipe is the output-style drain valve: FACING is its source
        // side and the opposite side is where the extracted item is sent.
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof ExtractionPipeBlock)) {
            return;
        }
        EnumFacing sourceSide = getSourceSide(state);
        EnumFacing targetSide = sourceSide.getOpposite();
        BlockPos sourcePos = pos.offset(sourceSide);
        BlockPos targetPos = pos.offset(targetSide);
        transferItems(sourcePos, targetPos, sourceSide, targetSide);
        transferFluids(sourcePos, targetPos, sourceSide, targetSide);
        transferGas(sourcePos, targetPos, sourceSide, targetSide);
    }

    private void transferItems(BlockPos sourcePos, BlockPos targetPos,
                               EnumFacing sourceSide, EnumFacing targetSide) {
        IItemHandler source = getItemHandler(sourcePos, targetSide);
        if (source == null) {
            return;
        }

        int sourceSlot = -1;
        ItemStack sample = ItemStack.EMPTY;
        for (int slot = 0; slot < source.getSlots(); slot++) {
            // Simulate the complete transfer first. This makes one tick move
            // one source slot's stack, capped at one normal Minecraft stack.
            ItemStack candidate = source.extractItem(slot, TRANSFER_LIMIT, true);
            if (!candidate.isEmpty()) {
                sourceSlot = slot;
                sample = candidate;
                break;
            }
        }
        if (sourceSlot < 0 || sample.isEmpty()) {
            return;
        }

        IItemHandler target = getItemHandler(targetPos, sourceSide);
        if (target == null) {
            if (!dropWhenNoContainer()) {
                return;
            }
            ItemStack extracted = source.extractItem(sourceSlot, sample.getCount(), false);
            if (!extracted.isEmpty()) {
                spawnDrop(pos, extracted);
            }
            return;
        }

        // Keep the source slot untouched unless the complete group can fit in
        // the destination. A full group may be distributed across multiple
        // destination slots, but it is still transferred during one tick.
        if (!canInsertWholeStack(target, sample)) {
            return;
        }

        ItemStack extracted = source.extractItem(sourceSlot, sample.getCount(), false);
        if (extracted.isEmpty()) {
            return;
        }
        ItemStack remainder = insertAcrossSlots(target, extracted);
        if (!remainder.isEmpty()) {
            // A container can change between simulation and insertion. Keep the
            // item visible instead of silently destroying it in that race.
            spawnDrop(pos, remainder);
        }
    }

    private void transferFluids(BlockPos sourcePos, BlockPos targetPos,
                                EnumFacing sourceSide, EnumFacing targetSide) {
        IFluidHandler source = getFluidHandler(sourcePos, targetSide);
        IFluidHandler target = getFluidHandler(targetPos, sourceSide);
        if (source == null || target == null) {
            return;
        }

        FluidStack available = source.drain(FLUID_TRANSFER_LIMIT, false);
        if (available == null || available.amount <= 0 || available.getFluid() == null) {
            return;
        }

        int accepted = target.fill(available, false);
        if (accepted <= 0) {
            return;
        }
        accepted = Math.min(accepted, available.amount);
        FluidStack drained = source.drain(accepted, true);
        if (drained == null || drained.amount <= 0 || drained.getFluid() == null) {
            return;
        }

        // The second simulation guards against handlers whose state changes
        // between the first probe and the actual transfer.
        int actualAccepted = target.fill(drained, false);
        if (actualAccepted <= 0) {
            return;
        }
        actualAccepted = Math.min(actualAccepted, drained.amount);
        if (actualAccepted < drained.amount) {
            drained.amount = actualAccepted;
        }
        target.fill(drained, true);
    }

    private void transferGas(BlockPos sourcePos, BlockPos targetPos,
                              EnumFacing sourceSide, EnumFacing targetSide) {
        MekanismGasIntegration.transfer(
                world.getTileEntity(sourcePos), targetSide,
                world.getTileEntity(targetPos), sourceSide,
                GAS_TRANSFER_LIMIT
        );
    }

    /**
     * Extraction pipes preserve their original drop behavior. Suction pipes
     * override this so they only transfer between two container handlers.
     */
    protected boolean dropWhenNoContainer() {
        return true;
    }

    /** Side of the pipe whose adjacent container provides the source stack. */
    protected EnumFacing getSourceSide(IBlockState state) {
        return state.getValue(ExtractionPipeBlock.FACING);
    }

    private boolean canInsertWholeStack(IItemHandler handler, ItemStack stack) {
        ItemStack remainder = stack;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            remainder = handler.insertItem(slot, remainder, true);
            if (remainder.isEmpty()) {
                return true;
            }
        }
        return remainder.isEmpty();
    }

    private ItemStack insertAcrossSlots(IItemHandler handler, ItemStack stack) {
        ItemStack remainder = stack;
        for (int slot = 0; slot < handler.getSlots() && !remainder.isEmpty(); slot++) {
            remainder = handler.insertItem(slot, remainder, false);
        }
        return remainder;
    }

    private IItemHandler getItemHandler(BlockPos blockPos, EnumFacing side) {
        TileEntity tile = world.getTileEntity(blockPos);
        // getCapability already returns null for unsupported capabilities, so
        // avoid the extra hasCapability lookup on every pipe tick.
        return tile == null
                ? null
                : tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side);
    }

    private IFluidHandler getFluidHandler(BlockPos blockPos, EnumFacing side) {
        TileEntity tile = world.getTileEntity(blockPos);
        return tile == null
                ? null
                : tile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side);
    }

    /**
     * Match Dung Pipe's Sewer Pipe drop behavior exactly: spawn at the pipe's
     * center, slightly below the block center, with no directional velocity.
     */
    private void spawnDrop(BlockPos blockPos, ItemStack stack) {
        double x = blockPos.getX() + 0.5D;
        double y = blockPos.getY() + 0.25D;
        double z = blockPos.getZ() + 0.5D;
        EntityItem item = new EntityItem(world, x, y, z, stack);
        item.motionX = 0.0D;
        item.motionY = 0.0D;
        item.motionZ = 0.0D;
        item.setDefaultPickupDelay();
        world.spawnEntity(item);
    }
}

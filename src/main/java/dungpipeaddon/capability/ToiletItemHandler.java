package dungpipeaddon.capability;

import dungpipeaddon.data.ToiletPairData;
import dungpipeaddon.tile.ToiletTileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A one-way view of the item handlers below the other toilets in this channel.
 * The toilet itself deliberately has no inventory slots or item storage.
 */
public class ToiletItemHandler implements IItemHandler {
    private final ToiletTileEntity owner;

    public ToiletItemHandler(ToiletTileEntity owner) {
        this.owner = owner;
    }

    @Override
    public int getSlots() {
        int slots = 0;
        for (IItemHandler handler : targetHandlers()) {
            slots = Math.max(slots, handler.getSlots());
        }
        // Keep the endpoint discoverable even before a peer container is
        // loaded. insertItem/extractItem still return the unchanged stack when
        // no valid channel destination exists.
        return Math.max(1, slots);
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (slot < 0) {
            return ItemStack.EMPTY;
        }
        for (IItemHandler handler : targetHandlers()) {
            if (slot >= handler.getSlots()) {
                continue;
            }
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack remainder = stack.copy();
        for (IItemHandler handler : targetHandlers()) {
            // A pipe may present slot zero even when the destination has a
            // larger inventory. Try every destination slot so the proxy acts
            // like a shared channel instead of exposing its target layout.
            for (int targetSlot = 0; targetSlot < handler.getSlots() && !remainder.isEmpty(); targetSlot++) {
                remainder = handler.insertItem(targetSlot, remainder, simulate);
            }
            if (remainder.isEmpty()) {
                break;
            }
        }
        return remainder;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0 || slot < 0) {
            return ItemStack.EMPTY;
        }
        for (IItemHandler handler : targetHandlers()) {
            if (slot >= handler.getSlots()) {
                continue;
            }
            ItemStack extracted = handler.extractItem(slot, amount, simulate);
            if (!extracted.isEmpty()) {
                return extracted;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        if (slot < 0) {
            return 0;
        }
        for (IItemHandler handler : targetHandlers()) {
            if (slot < handler.getSlots()) {
                return handler.getSlotLimit(slot);
            }
        }
        return 64;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return true;
    }

    private List<IItemHandler> targetHandlers() {
        List<IItemHandler> handlers = new ArrayList<>();
        if (owner.getWorld() == null || owner.getPairId() == null) {
            return handlers;
        }

        ToiletPairData data = ToiletPairData.get(owner.getWorld());
        UUID pairId = owner.getPairId();
        for (ToiletPairData.ToiletTarget target : data.findTargets(owner.getWorld(), owner.getPos(), pairId, false)) {
            BlockPos below = target.pos.down();
            TileEntity tile = target.world.getTileEntity(below);
            if (tile == null || tile instanceof ToiletTileEntity) {
                continue;
            }
            IItemHandler handler = tile.getCapability(
                    CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.UP
            );
            if (handler != null) {
                handlers.add(handler);
            }
        }
        return handlers;
    }
}

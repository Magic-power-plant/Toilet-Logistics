package dungpipeaddon.data;

import dungpipeaddon.DungPipeAddon;
import dungpipeaddon.block.ToiletBlock;
import dungpipeaddon.tile.ToiletTileEntity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.fluids.FluidStack;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

import javax.annotation.Nullable;

/** Persistent index of UUID-bound toilets, shared by all dimensions in a save. */
public class ToiletPairData extends WorldSavedData {
    private static final String DATA_NAME = DungPipeAddon.MODID + "_toilet_pairs";
    private static final String BINDINGS_TAG = "bindings";
    private static final String TRANSFER_FLUIDS_TAG = "transferFluids";
    private static final String MANUAL_FLUIDS_TAG = "manualFluids";

    private final Map<ToiletPosition, UUID> bindings = new HashMap<>();
    /** Fluid shown by a channel after a pipe/container transfer. */
    private final Map<UUID, FluidStack> transferFluids = new HashMap<>();
    /** Fluid explicitly inserted with a handheld fluid container. */
    private final Map<UUID, FluidStack> manualFluids = new HashMap<>();

    public ToiletPairData(String name) {
        super(name);
    }

    public ToiletPairData() {
        super(DATA_NAME);
    }

    public static ToiletPairData get(World world) {
        MapStorage storage = getStorage(world);
        ToiletPairData data = (ToiletPairData) storage.getOrLoadData(ToiletPairData.class, DATA_NAME);
        if (data == null) {
            data = new ToiletPairData();
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    public void register(World world, BlockPos pos, UUID pairId) {
        ToiletPosition key = new ToiletPosition(world.provider.getDimension(), pos);
        if (!pairId.equals(bindings.put(key, pairId))) {
            markDirty();
        }
    }

    public void unregister(World world, BlockPos pos, UUID pairId) {
        ToiletPosition key = new ToiletPosition(world.provider.getDimension(), pos);
        if (pairId.equals(bindings.get(key))) {
            bindings.remove(key);
            markDirty();
            boolean hasPeer = false;
            for (UUID bound : bindings.values()) {
                if (pairId.equals(bound)) {
                    hasPeer = true;
                    break;
                }
            }
            if (!hasPeer) {
                transferFluids.remove(pairId);
                manualFluids.remove(pairId);
                markDirty();
            }
        }
    }

    public boolean hasOther(World world, BlockPos pos, UUID pairId) {
        ToiletPosition current = new ToiletPosition(world.provider.getDimension(), pos);
        MinecraftServer server = world.getMinecraftServer();
        for (Map.Entry<ToiletPosition, UUID> entry : bindings.entrySet()) {
            if (!pairId.equals(entry.getValue()) || current.equals(entry.getKey())) {
                continue;
            }
            World targetWorld = resolveWorld(world, server, entry.getKey().dimension);
            if (targetWorld == null) {
                continue;
            }
            BlockPos targetPos = entry.getKey().pos;
            IBlockState targetState = targetWorld.getBlockState(targetPos);
            if (!(targetState.getBlock() instanceof ToiletBlock)) {
                continue;
            }
            TileEntity tile = targetWorld.getTileEntity(targetPos);
            if (tile instanceof ToiletTileEntity && pairId.equals(((ToiletTileEntity) tile).getPairId())) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public ToiletTarget findRandomOpenTarget(World sourceWorld, BlockPos sourcePos, UUID pairId, Random random) {
        List<ToiletTarget> candidates = findTargets(sourceWorld, sourcePos, pairId, true);
        return candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
    }

    /**
     * Returns loaded toilets on the same channel, excluding the calling toilet.
     * The list is rebuilt only when a capability is used, so the saved index is
     * still the only persistent state and stale entries are ignored safely.
     */
    public List<ToiletTarget> findTargets(World sourceWorld, BlockPos sourcePos, UUID pairId, boolean openOnly) {
        MinecraftServer server = sourceWorld.getMinecraftServer();
        ToiletPosition current = new ToiletPosition(sourceWorld.provider.getDimension(), sourcePos);
        List<ToiletTarget> candidates = new ArrayList<>();
        for (Map.Entry<ToiletPosition, UUID> entry : bindings.entrySet()) {
            if (!pairId.equals(entry.getValue()) || current.equals(entry.getKey())) {
                continue;
            }
            World targetWorld = resolveWorld(sourceWorld, server, entry.getKey().dimension);
            if (targetWorld == null) {
                continue;
            }
            BlockPos targetPos = entry.getKey().pos;
            IBlockState targetState = targetWorld.getBlockState(targetPos);
            if (!(targetState.getBlock() instanceof ToiletBlock)
                    || (openOnly && !targetState.getValue(ToiletBlock.OPEN))) {
                continue;
            }
            TileEntity tile = targetWorld.getTileEntity(targetPos);
            if (tile instanceof ToiletTileEntity && pairId.equals(((ToiletTileEntity) tile).getPairId())) {
                candidates.add(new ToiletTarget(targetWorld, targetPos));
            }
        }
        return candidates;
    }

    /** Returns the fluid currently shown by this channel, with manual input taking priority. */
    @Nullable
    public FluidStack getEffectiveFluid(UUID pairId) {
        FluidStack manual = manualFluids.get(pairId);
        if (manual != null && manual.getFluid() != null && manual.amount > 0) {
            return manual.copy();
        }
        FluidStack transfer = transferFluids.get(pairId);
        return transfer == null || transfer.getFluid() == null || transfer.amount <= 0
                ? null : transfer.copy();
    }

    /** Returns only fluid inserted through a bucket or another handheld container. */
    @Nullable
    public FluidStack getManualFluid(UUID pairId) {
        FluidStack fluid = manualFluids.get(pairId);
        return fluid == null || fluid.getFluid() == null || fluid.amount <= 0 ? null : fluid.copy();
    }

    /** Updates the shared display fluid produced by a pipe transfer. */
    public void setTransferFluid(World sourceWorld, UUID pairId, @Nullable FluidStack fluid) {
        setChannelFluid(sourceWorld, pairId, transferFluids, fluid);
    }

    /** Updates the shared display fluid inserted by a handheld container. */
    public void setManualFluid(World sourceWorld, UUID pairId, @Nullable FluidStack fluid) {
        setChannelFluid(sourceWorld, pairId, manualFluids, fluid);
    }

    /** Clears both sources of display fluid on every loaded toilet in this channel. */
    public void clearChannelFluid(World sourceWorld, UUID pairId) {
        boolean changed = transferFluids.remove(pairId) != null;
        changed |= manualFluids.remove(pairId) != null;
        if (changed) {
            markDirty();
        }
        syncChannelFluid(sourceWorld, pairId);
    }

    /** Clears only bucket/container fluid, allowing an older transfer display to return. */
    public void clearManualFluid(World sourceWorld, UUID pairId) {
        if (manualFluids.remove(pairId) != null) {
            markDirty();
        }
        syncChannelFluid(sourceWorld, pairId);
    }

    private void setChannelFluid(World sourceWorld, UUID pairId, Map<UUID, FluidStack> destination,
                                 @Nullable FluidStack fluid) {
        FluidStack next = normalizeFluid(fluid);
        FluidStack previous = destination.get(pairId);
        if (sameFluid(previous, next)) {
            // A stale client/cache can still exist even when the persisted value did not change.
            syncChannelFluid(sourceWorld, pairId);
            return;
        }
        if (next == null) {
            destination.remove(pairId);
        } else {
            destination.put(pairId, next);
        }
        markDirty();
        syncChannelFluid(sourceWorld, pairId);
    }

    /** Pushes the effective channel state into every loaded toilet and refreshes its light. */
    private void syncChannelFluid(World sourceWorld, UUID pairId) {
        FluidStack effective = getEffectiveFluid(pairId);
        MinecraftServer server = sourceWorld.getMinecraftServer();
        for (Map.Entry<ToiletPosition, UUID> entry : bindings.entrySet()) {
            if (!pairId.equals(entry.getValue())) {
                continue;
            }
            World targetWorld = resolveWorld(sourceWorld, server, entry.getKey().dimension);
            if (targetWorld == null) {
                continue;
            }
            TileEntity tile = targetWorld.getTileEntity(entry.getKey().pos);
            if (tile instanceof ToiletTileEntity
                    && pairId.equals(((ToiletTileEntity) tile).getPairId())) {
                ((ToiletTileEntity) tile).setSharedFluidCache(effective);
            }
        }
    }

    @Nullable
    private static FluidStack normalizeFluid(@Nullable FluidStack fluid) {
        if (fluid == null || fluid.getFluid() == null || fluid.amount <= 0) {
            return null;
        }
        FluidStack copy = fluid.copy();
        copy.amount = 1000;
        return copy;
    }

    private static boolean sameFluid(@Nullable FluidStack first, @Nullable FluidStack second) {
        if (first == null || second == null) {
            return first == second;
        }
        return first.isFluidStackIdentical(second);
    }

    @Nullable
    private static World resolveWorld(World sourceWorld, @Nullable MinecraftServer server, int dimension) {
        if (sourceWorld.provider.getDimension() == dimension) {
            return sourceWorld;
        }
        return server == null ? null : server.getWorld(dimension);
    }

    private static MapStorage getStorage(World world) {
        MinecraftServer server = world.getMinecraftServer();
        if (server != null) {
            World overworld = server.getWorld(0);
            if (overworld != null) {
                return overworld.getMapStorage();
            }
        }
        return world.getMapStorage();
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        bindings.clear();
        transferFluids.clear();
        manualFluids.clear();
        NBTTagList list = compound.getTagList(BINDINGS_TAG, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            if (entry.hasUniqueId("pairId")) {
                BlockPos pos = new BlockPos(entry.getInteger("x"), entry.getInteger("y"), entry.getInteger("z"));
                bindings.put(new ToiletPosition(entry.getInteger("dim"), pos), entry.getUniqueId("pairId"));
            }
        }
        readFluidMap(compound.getTagList(TRANSFER_FLUIDS_TAG, 10), transferFluids);
        readFluidMap(compound.getTagList(MANUAL_FLUIDS_TAG, 10), manualFluids);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<ToiletPosition, UUID> entry : bindings.entrySet()) {
            NBTTagCompound binding = new NBTTagCompound();
            binding.setInteger("dim", entry.getKey().dimension);
            binding.setInteger("x", entry.getKey().pos.getX());
            binding.setInteger("y", entry.getKey().pos.getY());
            binding.setInteger("z", entry.getKey().pos.getZ());
            binding.setUniqueId("pairId", entry.getValue());
            list.appendTag(binding);
        }
        compound.setTag(BINDINGS_TAG, list);
        compound.setTag(TRANSFER_FLUIDS_TAG, writeFluidMap(transferFluids));
        compound.setTag(MANUAL_FLUIDS_TAG, writeFluidMap(manualFluids));
        return compound;
    }

    private static void readFluidMap(NBTTagList list, Map<UUID, FluidStack> destination) {
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            if (!entry.hasUniqueId("pairId") || !entry.hasKey("fluid", 10)) {
                continue;
            }
            FluidStack fluid = FluidStack.loadFluidStackFromNBT(entry.getCompoundTag("fluid"));
            FluidStack normalized = normalizeFluid(fluid);
            if (normalized != null) {
                destination.put(entry.getUniqueId("pairId"), normalized);
            }
        }
    }

    private static NBTTagList writeFluidMap(Map<UUID, FluidStack> source) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<UUID, FluidStack> entry : source.entrySet()) {
            FluidStack fluid = normalizeFluid(entry.getValue());
            if (fluid == null) {
                continue;
            }
            NBTTagCompound value = new NBTTagCompound();
            value.setUniqueId("pairId", entry.getKey());
            value.setTag("fluid", fluid.writeToNBT(new NBTTagCompound()));
            list.appendTag(value);
        }
        return list;
    }

    public static final class ToiletTarget {
        public final World world;
        public final BlockPos pos;

        private ToiletTarget(World world, BlockPos pos) {
            this.world = world;
            this.pos = pos;
        }
    }

    private static final class ToiletPosition {
        private final int dimension;
        private final BlockPos pos;

        private ToiletPosition(int dimension, BlockPos pos) {
            this.dimension = dimension;
            this.pos = pos.toImmutable();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ToiletPosition)) {
                return false;
            }
            ToiletPosition other = (ToiletPosition) obj;
            return dimension == other.dimension && pos.equals(other.pos);
        }

        @Override
        public int hashCode() {
            return Objects.hash(dimension, pos);
        }
    }
}

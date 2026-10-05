package dungpipeaddon.block;

import dungpipeaddon.DungPipeAddon;
import dungpipeaddon.binding.ToiletBinding;
import dungpipeaddon.data.ToiletPairData;
import dungpipeaddon.tile.ToiletTileEntity;
import net.minecraft.block.Block;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Mirror;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Rotation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.DamageSource;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.Teleporter;
import net.minecraft.entity.Entity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/** One color of toilet; each color has an independent registry name. */
public class ToiletBlock extends Block {
    public static final PropertyDirection FACING = PropertyDirection.create("facing", facing -> facing.getAxis() != EnumFacing.Axis.Y);
    public static final PropertyBool OPEN = PropertyBool.create("open");
    private static final AxisAlignedBB NORTH_AABB = new AxisAlignedBB(2.0D / 16.0D, 0.0D, 1.0D / 16.0D, 14.0D / 16.0D, 1.0D, 1.0D);
    private static final AxisAlignedBB SOUTH_AABB = new AxisAlignedBB(2.0D / 16.0D, 0.0D, 0.0D, 14.0D / 16.0D, 1.0D, 15.0D / 16.0D);
    private static final AxisAlignedBB EAST_AABB = new AxisAlignedBB(0.0D, 0.0D, 2.0D / 16.0D, 15.0D / 16.0D, 1.0D, 14.0D / 16.0D);
    private static final AxisAlignedBB WEST_AABB = new AxisAlignedBB(1.0D / 16.0D, 0.0D, 2.0D / 16.0D, 1.0D, 1.0D, 14.0D / 16.0D);

    public ToiletBlock(String colorName) {
        super(Material.ROCK, MapColor.CLAY);
        setRegistryName(new ResourceLocation(DungPipeAddon.MODID, "toilet_" + colorName));
        setUnlocalizedName("toilet_" + colorName);
        setCreativeTab(DungPipeAddon.CREATIVE_TAB);
        setHardness(2.0F);
        setResistance(6.0F);
        setDefaultState(getBlockState().getBaseState()
                .withProperty(FACING, EnumFacing.NORTH)
                .withProperty(OPEN, false));
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        switch (state.getValue(FACING)) {
            case SOUTH:
                return SOUTH_AABB;
            case EAST:
                return EAST_AABB;
            case WEST:
                return WEST_AABB;
            case NORTH:
            default:
                return NORTH_AABB;
        }
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof ToiletTileEntity) {
            FluidStack fluid = ((ToiletTileEntity) tile).getFluidVisual();
            if (fluid != null && fluid.getFluid() != null) {
                return Math.max(0, Math.min(15, fluid.getFluid().getLuminosity(fluid)));
            }
        }
        return 0;
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new ToiletTileEntity();
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer, EnumHand hand) {
        EnumFacing facing = side.getAxis().isHorizontal()
                ? side
                : placer.getHorizontalFacing().getOpposite();
        return getDefaultState()
                .withProperty(FACING, facing)
                .withProperty(OPEN, world.isBlockPowered(pos));
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        ItemStack held = player.getHeldItem(hand);
        TileEntity tile = world.getTileEntity(pos);
        if (held.getItem() == DungPipeAddon.TOILET_PLUNGER && player.isSneaking()
                && tile instanceof ToiletTileEntity) {
            ((ToiletTileEntity) tile).clearFluidVisual();
            return true;
        }
        if (held.getItem() == Item.getItemFromBlock(this)) {
            if (!world.isRemote) {
                bindToilet(world, pos, player, held);
            }
            return true;
        }
        if (state.getValue(OPEN) && tile instanceof ToiletTileEntity
                && FluidUtil.getFluidHandler(held) != null) {
            // FluidUtil performs the normal bucket/container stack exchange;
            // the toilet handler only records the display fluid.
            // Do not run the handler on the client.  The client only predicts
            // the interaction and would otherwise treat its rendered transfer
            // fluid as a drainable tank before the server has responded.
            if (!world.isRemote) {
                ((ToiletTileEntity) tile).interactWithFluidContainer(player, hand);
            }
            return true;
        }
        setOpenState(world, pos, state, !state.getValue(OPEN), player);
        return true;
    }

    private void bindToilet(World world, BlockPos pos, EntityPlayer player, ItemStack held) {
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof ToiletTileEntity)) {
            return;
        }
        ToiletTileEntity toilet = (ToiletTileEntity) tile;
        UUID toiletId = toilet.getPairId();
        UUID itemId = ToiletBinding.getPairId(held);
        if (toiletId != null && itemId != null && !toiletId.equals(itemId)) {
            sendBindingMessage(player, "message.toilet_logistics.toilet.binding.conflict", TextFormatting.RED);
            return;
        }

        UUID pairId = toiletId != null ? toiletId : itemId;
        if (pairId == null) {
            pairId = UUID.randomUUID();
        }
        if (toiletId == null) {
            toilet.setPairId(pairId);
        }
        if (itemId == null) {
            bindOneHeldItem(player, held, pairId);
        }
        if (toiletId == null || itemId == null) {
        sendBindingMessage(player, "message.toilet_logistics.toilet.binding.success", TextFormatting.GREEN);
        }
    }

    private void bindOneHeldItem(EntityPlayer player, ItemStack held, UUID pairId) {
        if (held.getCount() == 1) {
            ToiletBinding.setPairId(held, pairId);
            return;
        }
        ItemStack bound = held.splitStack(1);
        ToiletBinding.setPairId(bound, pairId);
        if (!player.inventory.addItemStackToInventory(bound)) {
            player.dropItem(bound, false);
        }
    }

    private void sendBindingMessage(EntityPlayer player, String key, TextFormatting color) {
        TextComponentTranslation message = new TextComponentTranslation(key);
        message.getStyle().setColor(color);
        player.sendMessage(message);
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        UUID pairId = ToiletBinding.getPairId(stack);
        if (pairId != null) {
            TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof ToiletTileEntity) {
                ((ToiletTileEntity) tile).setPairId(pairId);
            }
        }
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof ToiletTileEntity) {
            ((ToiletTileEntity) tile).unregisterPair();
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        ItemStack drop = new ItemStack(Item.getItemFromBlock(this), 1, damageDropped(state));
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof ToiletTileEntity) {
            UUID pairId = ((ToiletTileEntity) tile).getPairId();
            if (pairId != null) {
                ToiletBinding.setPairId(drop, pairId);
            }
        }
        drops.add(drop);
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block blockIn, BlockPos fromPos) {
        if (!world.isRemote) {
            boolean powered = world.isBlockPowered(pos);
            if (powered || blockIn.getDefaultState().canProvidePower()) {
                boolean open = state.getValue(OPEN);
                if (open != powered) {
                    setOpenState(world, pos, state, powered, null);
                }
            }
        }
    }

    private void setOpenState(World world, BlockPos pos, IBlockState state, boolean open, @Nullable EntityPlayer player) {
        if (state.getValue(OPEN) == open) {
            return;
        }
        UUID pairId = getPairId(world, pos);
        FluidStack fluid = getFluidVisual(world, pos);
        world.setBlockState(pos, state.withProperty(OPEN, open), 2);
        restorePairId(world, pos, pairId);
        restoreFluidVisual(world, pos, fluid);
        playSound(player, world, pos, open);
        if (!world.isRemote && !open && pairId != null) {
            teleportStandingPlayers(world, pos, pairId);
        }
    }

    @Nullable
    private FluidStack getFluidVisual(World world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        return tile instanceof ToiletTileEntity ? ((ToiletTileEntity) tile).getFluidVisual() : null;
    }

    private void restoreFluidVisual(World world, BlockPos pos, @Nullable FluidStack fluid) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof ToiletTileEntity) {
            ToiletTileEntity toilet = (ToiletTileEntity) tile;
            UUID pairId = toilet.getPairId();
            if (pairId != null && !world.isRemote) {
                // The channel data is authoritative. Never restore a stale
                // per-tile copy after an open/close or rotation update.
                toilet.setSharedFluidCache(ToiletPairData.get(world).getEffectiveFluid(pairId));
            } else if (fluid != null) {
                toilet.setFluidVisual(fluid);
            }
        }
    }

    /**
     * Rotation replaces the block state and can recreate the tile entity. Keep
     * the channel identity and displayed fluid across wrench rotations.
     */
    @Override
    public boolean rotateBlock(World world, BlockPos pos, EnumFacing axis) {
        UUID pairId = getPairId(world, pos);
        FluidStack fluid = getFluidVisual(world, pos);
        boolean rotated = super.rotateBlock(world, pos, axis);
        if (rotated) {
            restorePairId(world, pos, pairId);
            restoreFluidVisual(world, pos, fluid);
        }
        return rotated;
    }

    @Nullable
    private UUID getPairId(@Nullable World world, BlockPos pos) {
        if (world == null) {
            return null;
        }
        TileEntity tile = world.getTileEntity(pos);
        return tile instanceof ToiletTileEntity ? ((ToiletTileEntity) tile).getPairId() : null;
    }

    private void restorePairId(World world, BlockPos pos, @Nullable UUID pairId) {
        if (pairId == null) {
            return;
        }
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof ToiletTileEntity && !pairId.equals(((ToiletTileEntity) tile).getPairId())) {
            ((ToiletTileEntity) tile).setPairId(pairId);
        }
    }

    private void teleportStandingPlayers(World sourceWorld, BlockPos sourcePos, UUID pairId) {
        AxisAlignedBB area = new AxisAlignedBB(
                sourcePos.getX(), sourcePos.getY() + 0.9D, sourcePos.getZ(),
                sourcePos.getX() + 1.0D, sourcePos.getY() + 1.5D, sourcePos.getZ() + 1.0D
        );
        List<EntityPlayer> players = sourceWorld.getEntitiesWithinAABB(EntityPlayer.class, area);
        ToiletPairData pairData = ToiletPairData.get(sourceWorld);
        TileEntity sourceTile = sourceWorld.getTileEntity(sourcePos);
        if (!(sourceTile instanceof ToiletTileEntity)) {
            return;
        }
        ToiletTileEntity sourceToilet = (ToiletTileEntity) sourceTile;
        FluidStack sourceFluid = sourceToilet.getFluidVisual();
        if (sourceFluid == null) {
            return;
        }
        for (EntityPlayer player : players) {
            AxisAlignedBB playerBox = player.getEntityBoundingBox();
            if (playerBox.minY < sourcePos.getY() + 0.9D || playerBox.minY > sourcePos.getY() + 1.05D) {
                continue;
            }
            // The toilet the player is leaving needs a water level. A peer may
            // be dry; the source level is still consumed after teleporting.
            ToiletPairData.ToiletTarget target = pairData.findRandomOpenTarget(
                    sourceWorld, sourcePos, pairId, sourceWorld.rand
            );
            if (target != null && teleportToTarget(player, target)) {
                applyFluidContact(player, target, sourceFluid);
                // A channel represents one shared level. Consume it once for
                // the successful transfer and clear every loaded peer.
                sourceToilet.clearFluidVisual();
                playFlushSound(target);
                break;
            }
        }
    }

    private void applyFluidContact(EntityPlayer player, ToiletPairData.ToiletTarget target, @Nullable FluidStack fluid) {
        if (fluid == null || fluid.getFluid() == null) {
            return;
        }
        net.minecraft.block.Block fluidBlock = fluid.getFluid().getBlock();
        String fluidName = fluid.getFluid().getName();
        net.minecraft.block.material.Material material = fluidBlock == null
                ? null : fluidBlock.getMaterial(fluidBlock.getDefaultState());
        boolean lavaContact = (fluidName != null
                && fluidName.toLowerCase(java.util.Locale.ROOT).contains("lava"))
                || material == net.minecraft.block.material.Material.LAVA;
        // Vanilla fluid collision can kill a low-health player before this
        // method returns, so mark the teleport before invoking it.
        if (lavaContact) {
            DungPipeAddon.markDiarrhea(player);
        }
        if (fluidBlock != null) {
            fluidBlock.onEntityCollidedWithBlock(
                    target.world, target.pos, fluidBlock.getDefaultState(), player
            );
        }
        if (lavaContact) {
            player.attackEntityFrom(DungPipeAddon.DIARRHEA_DAMAGE, 4.0F);
            player.setFire(15);
        } else if (material == net.minecraft.block.material.Material.WATER) {
            player.extinguish();
        }
    }

    private boolean teleportToTarget(EntityPlayer player, ToiletPairData.ToiletTarget target) {
        double x = target.pos.getX() + 0.5D;
        double y = target.pos.getY() + 1.0D;
        double z = target.pos.getZ() + 0.5D;
        if (player.world == target.world) {
            player.setPositionAndUpdate(x, y, z);
            return true;
        }
        if (!(player instanceof EntityPlayerMP) || !(target.world instanceof WorldServer)) {
            return false;
        }
        MinecraftServer server = target.world.getMinecraftServer();
        if (server == null) {
            return false;
        }
        final float yaw = player.rotationYaw;
        final float pitch = player.rotationPitch;
        Teleporter teleporter = new Teleporter((WorldServer) target.world) {
            @Override
            public void placeInPortal(Entity entity, float rotationYaw) {
                entity.setLocationAndAngles(x, y, z, yaw, pitch);
                entity.motionX = 0.0D;
                entity.motionY = 0.0D;
                entity.motionZ = 0.0D;
            }
        };
        server.getPlayerList().transferPlayerToDimension(
                (EntityPlayerMP) player, target.world.provider.getDimension(), teleporter
        );
        player.setPositionAndUpdate(x, y, z);
        return true;
    }

    private void playFlushSound(ToiletPairData.ToiletTarget target) {
        if (DungPipeAddon.TOILET_FLUSH_SOUND != null) {
            target.world.playSound(
                    null,
                    target.pos.getX() + 0.5D,
                    target.pos.getY() + 0.5D,
                    target.pos.getZ() + 0.5D,
                    DungPipeAddon.TOILET_FLUSH_SOUND,
                    SoundCategory.BLOCKS,
                    1.0F,
                    1.0F
            );
        }
    }

    private void playSound(EntityPlayer player, World world, BlockPos pos, boolean open) {
        world.playEvent(player, open ? 1007 : 1013, pos, 0);
    }

    @Override
    public Item getItemDropped(IBlockState state, java.util.Random random, int fortune) {
        return Item.getItemFromBlock(this);
    }

    @Override
    public int damageDropped(IBlockState state) {
        return 0;
    }

    @Override
    public void getSubBlocks(CreativeTabs tab, NonNullList<ItemStack> list) {
        list.add(new ItemStack(this, 1, 0));
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState()
                .withProperty(FACING, getFacing(meta))
                .withProperty(OPEN, (meta & 4) != 0);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        int meta = getMetaForFacing(state.getValue(FACING));
        return state.getValue(OPEN) ? meta | 4 : meta;
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, OPEN);
    }

    @Override
    public IBlockState withRotation(IBlockState state, Rotation rotation) {
        return state.withProperty(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public IBlockState withMirror(IBlockState state, Mirror mirror) {
        return state.withRotation(mirror.toRotation(state.getValue(FACING)));
    }

    private static EnumFacing getFacing(int meta) {
        switch (meta & 3) {
            case 0:
                return EnumFacing.NORTH;
            case 1:
                return EnumFacing.SOUTH;
            case 2:
                return EnumFacing.WEST;
            case 3:
            default:
                return EnumFacing.EAST;
        }
    }

    private static int getMetaForFacing(EnumFacing facing) {
        switch (facing) {
            case NORTH:
                return 0;
            case SOUTH:
                return 1;
            case WEST:
                return 2;
            case EAST:
            default:
                return 3;
        }
    }
}

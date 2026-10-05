package dungpipeaddon.block;

import dungpipeaddon.DungPipeAddon;
import dungpipeaddon.tile.ExtractionPipeTileEntity;
import net.minecraft.block.Block;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * A one-way item pipe. The copied Sewer Pipe opening is the input side; the
 * opposite side is the output toward a container or dropped item.
 */
public class ExtractionPipeBlock extends Block {
    public static final String REGISTRY_NAME = DungPipeAddon.MODID + ":drain_valve";
    /** FACING is the copied Sewer Pipe opening and the input side. */
    public static final PropertyDirection FACING = PropertyDirection.create("facing");

    public ExtractionPipeBlock() {
        this("drain_valve");
    }

    protected ExtractionPipeBlock(String name) {
        super(Material.IRON, MapColor.IRON);
        setUnlocalizedName(name);
        setRegistryName(new ResourceLocation(DungPipeAddon.MODID, name));
        setCreativeTab(DungPipeAddon.CREATIVE_TAB);
        setHardness(2.0F);
        setResistance(6.0F);
        setDefaultState(getBlockState().getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    @Nullable
    @Override
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        // Keep the collision shape aligned with the copied Sewer Pipe model.
        return getBoundingBox(state, world, pos);
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        switch (state.getValue(FACING)) {
            case NORTH:
                return new AxisAlignedBB(5 / 16.0D, 5 / 16.0D, 0 / 16.0D, 11 / 16.0D, 11 / 16.0D, 4 / 16.0D);
            case SOUTH:
                return new AxisAlignedBB(5 / 16.0D, 5 / 16.0D, 12 / 16.0D, 11 / 16.0D, 11 / 16.0D, 16 / 16.0D);
            case WEST:
                return new AxisAlignedBB(0 / 16.0D, 5 / 16.0D, 5 / 16.0D, 4 / 16.0D, 11 / 16.0D, 11 / 16.0D);
            case EAST:
                return new AxisAlignedBB(12 / 16.0D, 5 / 16.0D, 5 / 16.0D, 16 / 16.0D, 11 / 16.0D, 11 / 16.0D);
            case DOWN:
                return new AxisAlignedBB(5 / 16.0D, 0 / 16.0D, 5 / 16.0D, 11 / 16.0D, 4 / 16.0D, 11 / 16.0D);
            case UP:
                return new AxisAlignedBB(5 / 16.0D, 12 / 16.0D, 5 / 16.0D, 11 / 16.0D, 16 / 16.0D, 11 / 16.0D);
            default:
                return FULL_BLOCK_AABB;
        }
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new ExtractionPipeTileEntity();
    }

    @Override
    public IBlockState getStateForPlacement(
            World world,
            BlockPos pos,
            EnumFacing side,
            float hitX,
            float hitY,
            float hitZ,
            int meta,
            EntityLivingBase placer,
            EnumHand hand
    ) {
        // Keep Dung Pipe's placement convention, including top and bottom.
        return getDefaultState().withProperty(FACING, side.getOpposite());
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
    public boolean isPassable(IBlockAccess world, BlockPos pos) {
        return true;
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        switch (meta & 7) {
            case 0:
                return getDefaultState().withProperty(FACING, EnumFacing.getHorizontal(0));
            case 1:
                return getDefaultState().withProperty(FACING, EnumFacing.getHorizontal(1));
            case 2:
                return getDefaultState().withProperty(FACING, EnumFacing.getHorizontal(2));
            case 3:
                return getDefaultState().withProperty(FACING, EnumFacing.getHorizontal(3));
            case 4:
                return getDefaultState().withProperty(FACING, EnumFacing.DOWN);
            case 5:
                return getDefaultState().withProperty(FACING, EnumFacing.UP);
            default:
                return getDefaultState().withProperty(FACING, EnumFacing.getHorizontal(0));
        }
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        switch (state.getValue(FACING)) {
            case DOWN:
                return 4;
            case UP:
                return 5;
            default:
                return state.getValue(FACING).getHorizontalIndex();
        }
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }
}

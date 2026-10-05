package dungpipeaddon.block;

import dungpipeaddon.DungPipeAddon;
import dungpipeaddon.tile.SuctionPipeTileEntity;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * A container-only companion to the extraction pipe. It uses the same copied
 * Sewer Pipe model and six-way IO, but never emits a dropped item when the
 * output side has no container.
 */
public class SuctionPipeBlock extends ExtractionPipeBlock {
    public static final String REGISTRY_NAME = DungPipeAddon.MODID + ":extraction_pipe";

    public SuctionPipeBlock() {
        super("extraction_pipe");
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new SuctionPipeTileEntity();
    }
}

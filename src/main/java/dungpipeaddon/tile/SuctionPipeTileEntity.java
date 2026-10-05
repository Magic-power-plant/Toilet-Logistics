package dungpipeaddon.tile;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;

/** Item transfer tile that requires a container on both sides of the pipe. */
public class SuctionPipeTileEntity extends ExtractionPipeTileEntity {
    /**
     * The extraction pipe is the input-style counterpart to the drain valve:
     * its model-facing side receives the transferred stack, so the source is
     * read from the opposite side.
     */
    @Override
    protected EnumFacing getSourceSide(IBlockState state) {
        return state.getValue(dungpipeaddon.block.ExtractionPipeBlock.FACING).getOpposite();
    }

    @Override
    protected boolean dropWhenNoContainer() {
        return false;
    }
}

package dungpipeaddon.integration;

import dungpipeaddon.DungPipeAddon;
import dungpipeaddon.binding.ToiletBinding;
import dungpipeaddon.block.ToiletBlock;
import dungpipeaddon.data.ToiletPairData;
import dungpipeaddon.tile.ToiletTileEntity;
import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoProvider;
import mcjty.theoneprobe.api.ITheOneProbe;
import mcjty.theoneprobe.api.ProbeMode;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Function;

/** Optional The One Probe integration, loaded only when TOP receives the IMC function. */
@SuppressWarnings("unused")
public class TOPIntegration implements Function<ITheOneProbe, Void>, IProbeInfoProvider {
    @Override
    public Void apply(@Nullable ITheOneProbe probe) {
        if (probe != null) {
            probe.registerProvider(this);
        }
        return null;
    }

    @Override
    public String getID() {
        return DungPipeAddon.MODID + ":toilet_binding";
    }

    @Override
    public void addProbeInfo(ProbeMode mode, IProbeInfo probeInfo, EntityPlayer player, World world,
                             IBlockState blockState, IProbeHitData data) {
        if (!(blockState.getBlock() instanceof ToiletBlock)) {
            return;
        }
        BlockPos pos = data.getPos();
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof ToiletTileEntity)) {
            return;
        }
        UUID pairId = ((ToiletTileEntity) tile).getPairId();
        if (pairId == null) {
            return;
        }
        probeInfo.text(new TextComponentTranslation(
                "top.toilet_logistics.toilet.pair_id", ToiletBinding.formatPairId(pairId)));
        boolean found = ToiletPairData.get(world).hasOther(world, pos, pairId);
        probeInfo.text(new TextComponentTranslation(found
                ? "top.toilet_logistics.toilet.target_found"
                : "top.toilet_logistics.toilet.target_missing"));
    }
}

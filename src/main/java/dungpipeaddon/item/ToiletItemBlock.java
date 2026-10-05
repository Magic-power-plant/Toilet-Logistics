package dungpipeaddon.item;

import dungpipeaddon.binding.ToiletBinding;
import net.minecraft.block.Block;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/** Item form of one independently registered colored toilet block. */
public class ToiletItemBlock extends ItemBlock {
    public ToiletItemBlock(Block block) {
        super(block);
        setMaxStackSize(64);
        setRegistryName(block.getRegistryName());
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        UUID pairId = ToiletBinding.getPairId(stack);
        if (pairId == null) {
            tooltip.add(TextFormatting.GRAY + I18n.format("tooltip.toilet_logistics.toilet.unbound"));
            return;
        }
        tooltip.add(TextFormatting.GOLD + I18n.format("tooltip.toilet_logistics.toilet.bound"));
        tooltip.add(ToiletBinding.formatPairId(pairId));
    }

}

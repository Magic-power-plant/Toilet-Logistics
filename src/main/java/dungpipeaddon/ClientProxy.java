package dungpipeaddon;

import dungpipeaddon.block.ExtractionPipeBlock;
import dungpipeaddon.block.SuctionPipeBlock;
import dungpipeaddon.block.ToiletBlock;
import dungpipeaddon.item.ToiletPlungerItem;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class ClientProxy implements IProxy {
    @Override
    public void preInit() {
        MinecraftForge.EVENT_BUS.register(this);
        ClientRegistry.bindTileEntitySpecialRenderer(
                dungpipeaddon.tile.ToiletTileEntity.class, new ToiletFluidRenderer()
        );
    }

    @SubscribeEvent
    public void registerModels(ModelRegistryEvent event) {
        ModelLoader.setCustomModelResourceLocation(
                Item.getItemFromBlock(DungPipeAddon.EXTRACTION_PIPE),
                0,
                new ModelResourceLocation(ExtractionPipeBlock.REGISTRY_NAME, "inventory"));
        ModelLoader.setCustomModelResourceLocation(
                Item.getItemFromBlock(DungPipeAddon.SUCTION_PIPE),
                0,
                new ModelResourceLocation(SuctionPipeBlock.REGISTRY_NAME, "inventory"));
        ModelLoader.setCustomModelResourceLocation(
                DungPipeAddon.TOILET_PLUNGER,
                0,
                new ModelResourceLocation(ToiletPlungerItem.REGISTRY_NAME, "inventory"));
        for (EnumDyeColor color : EnumDyeColor.values()) {
            ModelLoader.setCustomModelResourceLocation(
                    DungPipeAddon.TOILET_ITEMS[color.getMetadata()],
                    0,
                    new ModelResourceLocation(
                            DungPipeAddon.MODID + ":toilet_" + color.getName(),
                            "inventory"));
        }
    }
}

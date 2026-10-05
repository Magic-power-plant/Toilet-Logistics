package dungpipeaddon;

import dungpipeaddon.block.ExtractionPipeBlock;
import dungpipeaddon.block.SuctionPipeBlock;
import dungpipeaddon.block.ToiletBlock;
import dungpipeaddon.item.ToiletPlungerItem;
import dungpipeaddon.item.ToiletItemBlock;
import dungpipeaddon.tile.ExtractionPipeTileEntity;
import dungpipeaddon.tile.SuctionPipeTileEntity;
import dungpipeaddon.tile.ToiletTileEntity;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.common.event.FMLInterModComms;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod(
        modid = DungPipeAddon.MODID,
        name = DungPipeAddon.NAME,
        version = DungPipeAddon.VERSION,
        dependencies = "required-after:dungpipe;after:theoneprobe;after:mekanism"
)
@Mod.EventBusSubscriber(modid = DungPipeAddon.MODID)
public class DungPipeAddon {
    public static final String MODID = "toilet_logistics";
    public static final String NAME = "Toilet Logistics";
    public static final String VERSION = "1.0";
    /** Damage dealt by fluid contact caused by a toilet teleport. */
    public static final DamageSource DIARRHEA_DAMAGE = new DamageSource("dungpipe_diarrhea")
            .setDamageBypassesArmor();
    private static final Map<UUID, Integer> DIARRHEA_MARKERS = new HashMap<>();

    public static final CreativeTabs CREATIVE_TAB = new CreativeTabs(MODID) {
        @Override
        public ItemStack getTabIconItem() {
            return TOILET_PLUNGER == null ? ItemStack.EMPTY : new ItemStack(TOILET_PLUNGER);
        }
    };

    @SidedProxy(
            clientSide = "dungpipeaddon.ClientProxy",
            serverSide = "dungpipeaddon.ServerProxy"
    )
    public static IProxy proxy;

    public static ExtractionPipeBlock EXTRACTION_PIPE;
    public static SuctionPipeBlock SUCTION_PIPE;

    public static ToiletBlock[] TOILETS = new ToiletBlock[16];
    public static Item[] TOILET_ITEMS = new Item[16];

    public static Item TOILET_PLUNGER;
    public static SoundEvent TOILET_FLUSH_SOUND;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        GameRegistry.registerTileEntity(
                ExtractionPipeTileEntity.class,
                new ResourceLocation(MODID, "drain_valve")
        );
        GameRegistry.registerTileEntity(
                SuctionPipeTileEntity.class,
                new ResourceLocation(MODID, "extraction_pipe")
        );
        GameRegistry.registerTileEntity(
                ToiletTileEntity.class,
                new ResourceLocation(MODID, "toilet")
        );
        if (Loader.isModLoaded("theoneprobe")) {
            FMLInterModComms.sendFunctionMessage(
                    "theoneprobe", "getTheOneProbe", "dungpipeaddon.integration.TOPIntegration"
            );
        }
        proxy.preInit();
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        EXTRACTION_PIPE = new ExtractionPipeBlock();
        event.getRegistry().register(EXTRACTION_PIPE);
        SUCTION_PIPE = new SuctionPipeBlock();
        event.getRegistry().register(SUCTION_PIPE);
        for (EnumDyeColor color : EnumDyeColor.values()) {
            TOILETS[color.getMetadata()] = new ToiletBlock(color.getName());
            event.getRegistry().register(TOILETS[color.getMetadata()]);
        }
    }

    @SubscribeEvent
    public static void registerSounds(RegistryEvent.Register<SoundEvent> event) {
        ResourceLocation soundId = new ResourceLocation(MODID, "toilet_flush");
        TOILET_FLUSH_SOUND = new SoundEvent(soundId).setRegistryName(soundId);
        event.getRegistry().register(TOILET_FLUSH_SOUND);
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        TOILET_PLUNGER = new ToiletPlungerItem();
        event.getRegistry().register(
                new ItemBlock(EXTRACTION_PIPE).setRegistryName(EXTRACTION_PIPE.getRegistryName())
        );
        event.getRegistry().register(
                new ItemBlock(SUCTION_PIPE).setRegistryName(SUCTION_PIPE.getRegistryName())
        );
        for (int i = 0; i < TOILETS.length; i++) {
            TOILET_ITEMS[i] = new ToiletItemBlock(TOILETS[i]);
            event.getRegistry().register(TOILET_ITEMS[i]);
        }
        event.getRegistry().register(TOILET_PLUNGER);
    }

    @SubscribeEvent
    public static void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        if (!player.world.isRemote) {
            tickDiarrheaMarker(player);
        }
        ItemStack head = player.getItemStackFromSlot(EntityEquipmentSlot.HEAD);
        if (!head.isEmpty() && head.getItem() == TOILET_PLUNGER) {
            ((ToiletPlungerItem) TOILET_PLUNGER).onWornTick(player.world, player, head);
        }
    }

    /** Marks fire damage caused by a toilet fluid contact for its duration. */
    public static void markDiarrhea(EntityPlayer player) {
        if (player != null && player.world != null && !player.world.isRemote) {
            DIARRHEA_MARKERS.put(player.getUniqueID(), 300);
        }
    }

    private static void tickDiarrheaMarker(EntityPlayer player) {
        UUID id = player.getUniqueID();
        Integer remaining = DIARRHEA_MARKERS.get(id);
        if (remaining == null) {
            return;
        }
        if (remaining <= 1) {
            DIARRHEA_MARKERS.remove(id);
        } else {
            DIARRHEA_MARKERS.put(id, remaining - 1);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        if (!isDiarrheaDamage(event.getSource())
                || !DIARRHEA_MARKERS.containsKey(player.getUniqueID())) {
            return;
        }

        // Forge posts this event after health reaches zero. Cancel the vanilla
        // fire/lava death, restore one heart, and kill with our custom source
        // so the normal death screen and chat use the diarrhea translation.
        event.setCanceled(true);
        player.setHealth(1.0F);
        DIARRHEA_MARKERS.remove(player.getUniqueID());
        player.attackEntityFrom(DIARRHEA_DAMAGE, 2.0F);
    }

    private static boolean isDiarrheaDamage(DamageSource source) {
        return source == DamageSource.LAVA
                || source == DamageSource.IN_FIRE
                || source == DamageSource.ON_FIRE
                || source.isFireDamage();
    }
}

package dungpipeaddon.item;

import dungpipeaddon.DungPipeAddon;
import dungpipeaddon.block.ToiletBlock;
import dungpipeaddon.tile.ToiletTileEntity;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import java.util.UUID;

/** A reusable wrench-style tool for rotating directional blocks. */
public class ToiletPlungerItem extends Item {
    public static final String REGISTRY_NAME = DungPipeAddon.MODID + ":toilet_plunger";
    private static final UUID ARMOR_MODIFIER_UUID = UUID.fromString("d9a2d5b1-5b34-4d2d-bf3f-8f7ddaf80c13");

    public ToiletPlungerItem() {
        setRegistryName(new ResourceLocation(DungPipeAddon.MODID, "toilet_plunger"));
        setUnlocalizedName("toilet_plunger");
        setCreativeTab(DungPipeAddon.CREATIVE_TAB);
        setMaxStackSize(1);
    }

    @Override
    public boolean doesSneakBypassUse(ItemStack stack, IBlockAccess world, BlockPos pos, EntityPlayer player) {
        return true;
    }

    /** Place this item in the player's head equipment slot when moved there
     * through the inventory, while leaving normal hand right-click unchanged.
     */
    @Override
    public EntityEquipmentSlot getEquipmentSlot(ItemStack stack) {
        return EntityEquipmentSlot.HEAD;
    }

    @Override
    public boolean isValidArmor(ItemStack stack, EntityEquipmentSlot armorType, Entity entity) {
        return armorType == EntityEquipmentSlot.HEAD;
    }

    @Override
    public Multimap<String, AttributeModifier> getAttributeModifiers(EntityEquipmentSlot slot, ItemStack stack) {
        Multimap<String, AttributeModifier> modifiers = HashMultimap.create();
        if (slot == EntityEquipmentSlot.HEAD) {
            modifiers.put(
                    SharedMonsterAttributes.ARMOR.getName(),
                    new AttributeModifier(ARMOR_MODIFIER_UUID, "Toilet plunger armor", 1.0D, 0)
            );
        }
        return modifiers;
    }

    /** Explicitly keep the vanilla Item behavior: holding the plunger and
     * right-clicking air must not auto-equip it into the head slot.
     */
    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        return new ActionResult<>(EnumActionResult.PASS, player.getHeldItem(hand));
    }

    /** Applies the requested periodic nausea while the item is worn. */
    public void onWornTick(World world, EntityPlayer player, ItemStack stack) {
        if (!world.isRemote && player.ticksExisted % (20 * 3) == 0) {
            player.addPotionEffect(new PotionEffect(MobEffects.NAUSEA, 20 * 4, 0));
        }
    }

    @Override
    public EnumActionResult onItemUseFirst(
            EntityPlayer player,
            World world,
            BlockPos pos,
            EnumFacing side,
            float hitX,
            float hitY,
            float hitZ,
            EnumHand hand
    ) {
        IBlockState state = world.getBlockState(pos);
        if (world.isAirBlock(pos)) {
            return EnumActionResult.PASS;
        }

        Block block = state.getBlock();
        if (player.isSneaking() && block instanceof ToiletBlock) {
            if (!world.isRemote) {
                net.minecraft.tileentity.TileEntity tile = world.getTileEntity(pos);
                if (tile instanceof ToiletTileEntity) {
                    ((ToiletTileEntity) tile).clearFluidVisual();
                }
            }
            player.swingArm(hand);
            return world.isRemote ? EnumActionResult.PASS : EnumActionResult.SUCCESS;
        }
        if (block.rotateBlock(world, pos, side)) {
            player.swingArm(hand);
            return world.isRemote ? EnumActionResult.PASS : EnumActionResult.SUCCESS;
        }

        IBlockState rotated = rotateDirectionalProperty(state);
        if (rotated == null) {
            return EnumActionResult.PASS;
        }
        world.setBlockState(pos, rotated, 3);
        player.swingArm(hand);
        return world.isRemote ? EnumActionResult.PASS : EnumActionResult.SUCCESS;
    }

    @Nullable
    @SuppressWarnings({"rawtypes", "unchecked"})
    private IBlockState rotateDirectionalProperty(IBlockState state) {
        for (IProperty<?> property : state.getPropertyKeys()) {
            if (!(property instanceof PropertyDirection)) {
                continue;
            }
            PropertyDirection direction = (PropertyDirection) property;
            EnumFacing current = (EnumFacing) state.getValue(direction);
            if (current.getAxis() != EnumFacing.Axis.Y) {
                return state.withProperty(direction, current.rotateY());
            }
        }
        return null;
    }
}

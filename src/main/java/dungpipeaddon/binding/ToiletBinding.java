package dungpipeaddon.binding;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextFormatting;

import javax.annotation.Nullable;
import java.util.UUID;

/** Shared UUID storage and the compact color/symbol representation used by tooltips and TOP. */
public final class ToiletBinding {
    public static final String PAIR_ID_TAG = "pairId";

    private static final TextFormatting[] COLORS = {
            TextFormatting.BLACK, TextFormatting.DARK_BLUE, TextFormatting.DARK_GREEN, TextFormatting.DARK_AQUA,
            TextFormatting.DARK_RED, TextFormatting.DARK_PURPLE, TextFormatting.GOLD, TextFormatting.GRAY,
            TextFormatting.DARK_GRAY, TextFormatting.BLUE, TextFormatting.GREEN, TextFormatting.AQUA,
            TextFormatting.RED, TextFormatting.LIGHT_PURPLE, TextFormatting.YELLOW, TextFormatting.WHITE
    };

    private static final String[] SYMBOLS = {
            "\u1511 ", "\u0296 ", "\u14F5 ", "\u21B8 ", "\u013F ", "\u2393 ", "\u3153 ", "\u3012 ",
            "\u2351 ", "\u254E ", "\u14B7 ", "\u30EA ", "\u30D5 ", "\u00A1 ", "\u1451 ", "\u1362 "
    };

    private ToiletBinding() {
    }

    @Nullable
    public static UUID getPairId(ItemStack stack) {
        if (stack.hasTagCompound() && stack.getTagCompound().hasUniqueId(PAIR_ID_TAG)) {
            return stack.getTagCompound().getUniqueId(PAIR_ID_TAG);
        }
        return null;
    }

    public static void setPairId(ItemStack stack, UUID pairId) {
        NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        tag.setUniqueId(PAIR_ID_TAG, pairId);
        stack.setTagCompound(tag);
    }

    /** Render the same eight color/symbol pairs used by shrunkgateway. */
    public static String formatPairId(UUID pairId) {
        String uuidHex = pairId.toString().replace("-", "").toUpperCase();
        StringBuilder visualUuid = new StringBuilder();
        for (int i = 0; i < 16 && i + 1 < uuidHex.length(); i += 2) {
            int colorIndex = Character.digit(uuidHex.charAt(i), 16);
            int symbolIndex = Character.digit(uuidHex.charAt(i + 1), 16);
            if (colorIndex >= 0 && symbolIndex >= 0) {
                visualUuid.append(COLORS[colorIndex]);
                visualUuid.append(SYMBOLS[symbolIndex]);
            }
        }
        return visualUuid.toString();
    }
}

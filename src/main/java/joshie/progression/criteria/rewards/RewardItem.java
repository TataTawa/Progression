package joshie.progression.criteria.rewards;

import joshie.progression.Progression;
import joshie.progression.api.ProgressionAPI;
import joshie.progression.api.criteria.IField;
import joshie.progression.api.criteria.IFilterProvider;
import joshie.progression.api.criteria.ProgressionRule;
import joshie.progression.api.special.*;
import joshie.progression.gui.fields.ItemFilterFieldPreview;
import joshie.progression.helpers.ItemHelper;
import joshie.progression.helpers.SpawnItemHelper;
import joshie.progression.network.PacketHandler;
import joshie.progression.network.PacketRewardItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import java.util.List;

import static joshie.progression.api.special.DisplayMode.EDIT;
import static joshie.progression.gui.core.GuiList.MODE;
import static net.minecraft.util.EnumChatFormatting.BLUE;

@ProgressionRule(name="item", color=0xFFE599FF)
public class RewardItem extends RewardBaseItemFilter implements ICustomDisplayName, ICustomDescription, ICustomWidth, ICustomTooltip, ISpecialFieldProvider, IStackSizeable, IRequestItem {
    public int stackSize = 1;

    @Override
    public String getDisplayName() {
        return MODE == EDIT ? Progression.translate(getProvider().getUnlocalisedName()) : Progression.translate(getProvider().getUnlocalisedName() + ".display");
    }

    @Override
    public String getDescription() {
        return "";
    }

    @Override
    public int getWidth(DisplayMode mode) {
        return mode == EDIT ? 100 : 55;
    }

    @Override
    public void addTooltip(List list) {
        list.add(BLUE + Progression.translate("item.free"));
        list.add(getIcon().getDisplayName() + " x" + stackSize);
    }

    @Override
    public void addSpecialFields(List<IField> fields, DisplayMode mode) {
        if (mode == EDIT) fields.add(new ItemFilterFieldPreview("filters", this, 50, 40, 2F));
        else fields.add(new ItemFilterFieldPreview("filters", this, 5, 25, 2.8F));
    }

    @Override
    public int getStackSize() {
        return stackSize;
    }

    @Override
    public ItemStack getRequestedStack(EntityPlayer player) {
        return ItemHelper.getRandomItemOfSize(filters, player, stackSize);
    }

    @Override
    public void reward(EntityPlayer player, ItemStack stack) {
        if (stack != null) {
            for (IFilterProvider filter: filters) {
                if (filter.getProvided().matches(stack)) {
                    PacketHandler.sendToClient(new PacketRewardItem(stack.copy()), (EntityPlayerMP) player);
                    SpawnItemHelper.addToPlayerInventory(player, stack.copy());
                    return;
                }
            }
        }
    }

    @Override
    public void reward(EntityPlayerMP player) {
        ProgressionAPI.registry.requestItem(this, player);
    }
}

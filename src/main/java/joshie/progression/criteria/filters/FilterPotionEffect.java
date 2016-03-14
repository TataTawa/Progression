package joshie.progression.criteria.filters;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonObject;

import joshie.progression.gui.fields.IItemSetterCallback;
import joshie.progression.gui.fields.ItemField;
import joshie.progression.gui.newversion.overlays.FeatureItemSelector.Type;
import joshie.progression.gui.selector.filters.PotionFilter;
import joshie.progression.helpers.JSONHelper;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;

public class FilterPotionEffect extends FilterBase implements IItemSetterCallback {
    private static final List<PotionEffect> EMPTY = new ArrayList();
    public int potionid = 16385; //Splash Potion of Regen, 33 seconds
    public ItemStack item;
    private List<PotionEffect> effects;
    private Set<Integer> ids;

    public FilterPotionEffect() {
        super("potioneffect", 0xFFFF73FF);
        list.add(new ItemField("item", this, 25, 25, 3F, 26, 70, 25, 75, Type.TRIGGER, PotionFilter.INSTANCE));
    }

    @Override
    public void readFromJSON(JsonObject data) {
        potionid = JSONHelper.getInteger(data, "potionid", 16385);
        setupEffectsItemsIDs();
    }

    @Override
    public void writeToJSON(JsonObject data) {
        JSONHelper.setInteger(data, "potionid", potionid, 16385);
    }
    
    private List<PotionEffect> getEffects(int metadata) {
        List<PotionEffect> effects = Items.potionitem.getEffects(metadata);
        return effects != null ? effects : EMPTY;
    }
    
    private Set<Integer> getIds(List<PotionEffect> list) {
        Set<Integer> ids = new HashSet();
        for (PotionEffect check: list) ids.add(check.getPotionID());
        return ids;
    }
    
    private void setupEffectsItemsIDs() {
        if (effects == null) effects = getEffects(potionid);
        if (ids == null) ids = getIds(effects);
        if (item == null) item = new ItemStack(Items.potionitem, 1, potionid);
    }
    
    @Override
    public boolean matches(ItemStack check) {
        if (check.getItem() != Items.potionitem) return false;        
        Set<Integer> checkids = getIds(getEffects(check.getItemDamage()));
        setupEffectsItemsIDs();
        for (Integer id: getIds(effects)) {
            if (checkids.contains(id)) return true;
        }
        
        return false;
    }

    @Override
    public void setItem(String fieldName, ItemStack stack) {
        potionid = stack.getItemDamage();
        effects = getEffects(potionid);
        ids = getIds(effects);
        item = new ItemStack(Items.potionitem, 1, potionid);
    }
}
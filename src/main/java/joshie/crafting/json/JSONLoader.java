package joshie.crafting.json;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import joshie.crafting.CraftAPIRegistry;
import joshie.crafting.CraftingMod;
import joshie.crafting.api.CraftingAPI;
import joshie.crafting.api.ICondition;
import joshie.crafting.api.ICriteria;
import joshie.crafting.api.IReward;
import joshie.crafting.api.ITab;
import joshie.crafting.api.ITrigger;
import joshie.crafting.api.crafting.CraftingEvent.CraftingType;
import joshie.crafting.helpers.StackHelper;
import joshie.crafting.lib.CraftingInfo;
import joshie.crafting.lib.Exceptions.ConditionNotFoundException;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.apache.commons.io.FileUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class JSONLoader {
    public static Gson gson;
    static {
        GsonBuilder builder = new GsonBuilder().setPrettyPrinting();
        gson = builder.create();
    }
   
    private static String[] splitStringEvery(String s, int interval) {
        int arrayLength = (int) Math.ceil(((s.length() / (double)interval)));
        String[] result = new String[arrayLength];

        int j = 0;
        int lastIndex = result.length - 1;
        for (int i = 0; i < lastIndex; i++) {
            result[i] = s.substring(j, j + interval);
            j += interval;
        } 
        
        result[lastIndex] = s.substring(j);
        return result;
    }
    
    @SideOnly(Side.CLIENT)
    public static String[] clientTabJsonData;
    public static String[] serverTabJsonData;
    
    public static DefaultSettings getTabs() {     
        final int MAX_LENGTH = 10000;
        DefaultSettings loader = null;
        try {
            File file = new File("config" + File.separator + CraftingInfo.MODPATH + File.separator + "criteria.json");
            if (!file.exists()) {
                loader = new DefaultSettings().setDefaults();
                String json = gson.toJson(loader);
                serverTabJsonData = splitStringEvery(json, MAX_LENGTH);
                Writer writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
                writer.write(json);
                writer.close();
                return loader;
            } else {
                String json = FileUtils.readFileToString(file);
                serverTabJsonData = splitStringEvery(json, MAX_LENGTH);
                return gson.fromJson(json, DefaultSettings.class);
            }
        } catch (Exception e) { e.printStackTrace(); } 
        return loader; //Return it whether it's null or not
    }
    
    public static boolean setTabsAndCriteriaFromString(String json) {
        try {
            DefaultSettings tab = gson.fromJson(json, DefaultSettings.class);
            loadJSON(tab);
            return true;
        } catch (Exception e) { return false; }
    }
    
    private static CraftingType getCraftingTypeFromName(String name) {
        for (CraftingType type : CraftingType.craftingTypes) {
            if (name.equalsIgnoreCase(type.name)) return type;
        }

        return CraftingType.CRAFTING;
    }
        
    public static void loadJSON(DefaultSettings settings) {
        Options.settings = settings;
    	boolean isClient = FMLCommonHandler.instance().getEffectiveSide() == Side.CLIENT;
        for (DataTab data: settings.tabs) {
            ItemStack stack = null;
            if (data.stack != null) {
                stack = StackHelper.getStackFromString(data.stack);
            }
            
            if (stack == null) {
                stack = new ItemStack(Items.book);
            }

            ITab iTab = CraftingAPI.registry.newTab(data.uniqueName);
            iTab.setDisplayName(data.displayName).setVisibility(data.isVisible).setStack(stack).setSortIndex(data.sortIndex);
            
            /** Step 1: we create add all instances of criteria to the registry **/
            for (DataCriteria criteria : data.criteria) {
                CraftingAPI.registry.newCriteria(iTab, criteria.uniqueName);
            }

            /** Step 2 : Register all the conditions and triggers for this criteria **/
            for (DataCriteria criteria : data.criteria) {
                ICriteria theCriteria = CraftingAPI.registry.getCriteriaFromName(criteria.uniqueName);
                if (theCriteria == null) {
                    throw new ConditionNotFoundException(criteria.uniqueName);
                }

                ITrigger[] triggerz = new ITrigger[criteria.triggers.size()];
                for (int j = 0; j < triggerz.length; j++) {
                    DataTrigger trigger = criteria.triggers.get(j);
                    ITrigger iTrigger = CraftingAPI.registry.newTrigger(theCriteria, trigger.type, trigger.data);
                    ICondition[] conditionz = new ICondition[trigger.conditions.size()];
                    for (int i = 0; i < conditionz.length; i++) {
                        DataGeneric condition = trigger.conditions.get(i);
                        conditionz[i] = CraftingAPI.registry.newCondition(theCriteria, condition.type, condition.data);
                    }

                    iTrigger.setConditions(conditionz);
                    triggerz[j] = iTrigger;
                }

                //Add the Rewards
                IReward[] rewardz = new IReward[criteria.rewards.size()];
                for (int k = 0; k < criteria.rewards.size(); k++) {
                    DataGeneric reward = criteria.rewards.get(k);
                    rewardz[k] = CraftingAPI.registry.newReward(theCriteria, reward.type, reward.data);
                }

                theCriteria.addTriggers(triggerz).addRewards(rewardz);
            }

            /** Step 3, nAdd the extra data **/
            for (DataCriteria criteria : data.criteria) {
                ICriteria theCriteria = CraftingAPI.registry.getCriteriaFromName(criteria.uniqueName);
                if (theCriteria == null) {
                    CraftingMod.logger.log(org.apache.logging.log4j.Level.WARN, "Criteria was not found, do not report this.");
                    throw new ConditionNotFoundException(criteria.uniqueName);
                }
                
                ICriteria[] thePrereqs = new ICriteria[criteria.prereqs.length];
                ICriteria[] theConflicts = new ICriteria[criteria.conflicts.length];
                for (int i = 0; i < thePrereqs.length; i++)
                    thePrereqs[i] = CraftingAPI.registry.getCriteriaFromName(criteria.prereqs[i]);
                for (int i = 0; i < theConflicts.length; i++)
                    theConflicts[i] = CraftingAPI.registry.getCriteriaFromName(criteria.conflicts[i]);
                boolean isVisible = criteria.isVisible;
                int repeatable = criteria.repeatable;
                int x = criteria.x;
                int y = criteria.y;
                
                ItemStack icon = null;
                if (criteria.displayStack != null) {
                    icon = StackHelper.getStackFromString(criteria.displayStack);
                }
                
                if (icon == null) {
                    icon = new ItemStack(Blocks.stone);
                }
                
                String display = criteria.displayName;
                if (repeatable <= 1) {
                    repeatable = 1;
                }
                
                theCriteria.addRequirements(thePrereqs).addConflicts(theConflicts).setDisplayName(display).setVisibility(isVisible).setRepeatAmount(repeatable).setIcon(icon);
                
                if (isClient) {
                	theCriteria.getTreeEditor().setCoordinates(x, y);
                }
            }
        }

        
        settings = null; //Clear out this object
    }

    public static void saveJSON(Object toSave, String name) {
        File file = new File("config" + File.separator + CraftingInfo.MODPATH + File.separator + name + ".json");
        try {
            Writer writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
            writer.write(gson.toJson(toSave));
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void saveData() {
        HashSet<String> tabNames = new HashSet();
        Collection<ITab> allTabs = CraftAPIRegistry.tabs.values();
        HashSet<String> names = new HashSet();
        DefaultSettings forJSONTabs = new DefaultSettings();
        for (ITab tab: allTabs) {
            ArrayList<DataCriteria> list = new ArrayList();
            if (!tabNames.add(tab.getUniqueName())) continue;
            DataTab tabData = new DataTab();
            tabData.uniqueName = tab.getUniqueName();
            tabData.displayName = tab.getDisplayName();
            tabData.sortIndex = tab.getSortIndex();
            tabData.isVisible = tab.isVisible();
            tabData.stack = StackHelper.getStringFromStack(tab.getStack());
            for (ICriteria c: tab.getCriteria()) {
                if (!names.add(c.getUniqueName())) continue;
                if (c.getTreeEditor() == null) continue;
                DataCriteria data = new DataCriteria();
                data.x = c.getTreeEditor().getX();
                data.y = c.getTreeEditor().getY();
                data.isVisible = c.isVisible();
                data.repeatable = c.getRepeatAmount();
                data.displayName = c.getDisplayName();
                data.uniqueName = c.getUniqueName();
                data.displayStack = StackHelper.getStringFromStack(c.getIcon());
                List<ITrigger> triggers = c.getTriggers();
                List<IReward> rewards = c.getRewards();
                List<ICriteria> prereqs = c.getRequirements();
                List<ICriteria> conflicts = c.getConflicts();

                ArrayList<DataTrigger> theTriggers = new ArrayList();
                ArrayList<DataGeneric> theRewards = new ArrayList();
                for (ITrigger trigger : c.getTriggers()) {
                    ArrayList<DataGeneric> theConditions = new ArrayList();
                    for (ICondition condition : trigger.getConditions()) {
                        JsonObject object = new JsonObject();
                        if (condition.isInverted()) {
                            object.addProperty("inverted", true);
                        }

                        condition.serialize(object);
                        DataGeneric dCondition = new DataGeneric(condition.getTypeName(), object);
                        theConditions.add(dCondition);
                    }

                    JsonObject triggerData = new JsonObject();
                    trigger.serialize(triggerData);
                    DataTrigger dTrigger = new DataTrigger(trigger.getTypeName(), triggerData, theConditions);
                    theTriggers.add(dTrigger);
                }

                for (IReward reward : c.getRewards()) {
                    JsonObject rewardData = new JsonObject();
                    reward.serialize(rewardData);
                    DataGeneric dReward = new DataGeneric(reward.getTypeName(), rewardData);
                    theRewards.add(dReward);
                }

                String[] thePrereqs = new String[prereqs.size()];
                String[] theConflicts = new String[conflicts.size()];
                for (int i = 0; i < thePrereqs.length; i++)
                    thePrereqs[i] = prereqs.get(i).getUniqueName();
                for (int i = 0; i < theConflicts.length; i++)
                    theConflicts[i] = conflicts.get(i).getUniqueName();
                data.triggers = theTriggers;
                data.rewards = theRewards;
                data.prereqs = thePrereqs;
                data.conflicts = theConflicts;
                list.add(data);
            }
            
            tabData.criteria = list;
            forJSONTabs.tabs.add(tabData);
        }
        
        saveJSON(forJSONTabs, "criteria");
    }
}

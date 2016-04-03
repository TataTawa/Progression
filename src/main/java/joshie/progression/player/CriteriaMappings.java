package joshie.progression.player;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import joshie.progression.Progression;
import joshie.progression.api.criteria.ICondition;
import joshie.progression.api.criteria.ICriteria;
import joshie.progression.api.criteria.IReward;
import joshie.progression.api.criteria.ITrigger;
import joshie.progression.api.special.ICancelable;
import joshie.progression.api.special.IStoreTriggerData;
import joshie.progression.handlers.APIHandler;
import joshie.progression.handlers.RemappingHandler;
import joshie.progression.helpers.CollectionHelper;
import joshie.progression.helpers.NBTHelper;
import joshie.progression.helpers.PlayerHelper;
import joshie.progression.network.*;
import joshie.progression.player.nbt.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fml.common.eventhandler.Event.Result;
import org.apache.logging.log4j.Level;

import java.util.*;

import static joshie.progression.Progression.data;

public class CriteriaMappings {
    private PlayerDataServer master;
    private UUID uuid;

    protected HashMap<ICriteria, Integer> completedCritera = new HashMap(); //All the completed criteria, with a number for how many times repeated
    protected Set<ITrigger> completedTriggers = new HashSet(); //All the completed trigger, With their unique name as their identifier, Persistent
    protected HashMultimap<UUID, IReward> unclaimedRewards = HashMultimap.create(); //List of the team members and the rewards they haven't claimed yet
    protected HashMap<UUID, HashMap<ICriteria, Integer>> numberRewards = new HashMap();
    protected HashMap<UUID, IStoreTriggerData> triggerDataMap = new HashMap(); //
    protected Set<IReward> claimedRewards = new HashSet();
    protected Set<ICriteria> impossible = new HashSet();

    //Generated by the remapping
    protected Multimap<String, ITrigger> activeTriggers; //List of all the active triggers, based on their trigger type

    //Sets the uuid associated with this class
    public void setMaster(PlayerDataServer master) {
        this.master = master;
        this.uuid = master.getUUID();
    }

    public void syncToClient(EntityPlayerMP player) {
        //remap(); //Remap the data, before the client gets sent the data

        PacketHandler.sendToClient(new PacketSyncTeam(master.getTeam()), player);
        PacketHandler.sendToClient(new PacketSyncAbilities(master.getAbilities()), player);
        PacketHandler.sendToClient(new PacketSyncCustomData(master.getCustomStats()), player);
        PacketHandler.sendToClient(new PacketSyncPoints(master.getPoints()), player);
        PacketHandler.sendToClient(new PacketSyncImpossible(impossible.toArray(new ICriteria[impossible.size()])), player);
        PacketHandler.sendToClient(new PacketSyncTriggers(completedTriggers), player); //Sync all trigger that are completed to the client
        PacketHandler.sendToClient(new PacketSyncTriggerData(triggerDataMap), player); //Sync all trigger data
        PacketHandler.sendToClient(new PacketSyncCriteria(true, completedCritera.values().toArray(new Integer[completedCritera.size()]), completedCritera.keySet().toArray(new ICriteria[completedCritera.size()])), player); //Sync all conditions to the client
    }

    private void readTriggerData(NBTTagCompound nbt) {
        Progression.logger.log(Level.INFO, "Reading the nbt data for uuid " + uuid);
        NBTTagList data = nbt.getTagList("ActiveTriggerData", 10);
        for (int i = 0; i < data.tagCount(); i++) {
            NBTTagCompound tag = data.getCompoundTagAt(i);
            UUID uuid = UUID.fromString(tag.getString("UUID"));
            NBTTagCompound triggerNBT = tag.getCompoundTag("Data");
            if (triggerDataMap.get(uuid) == null) {
                ITrigger trigger = APIHandler.getCache().getTriggerFromUUID(uuid);
                if (trigger != null) {
                    IStoreTriggerData triggerData = (IStoreTriggerData)trigger.copy();
                    triggerData.readDataFromNBT(triggerNBT);
                    triggerDataMap.put(uuid, triggerData);
                }
            } else triggerDataMap.get(uuid).readDataFromNBT(tag);
        }
    }

    //Reads the completed criteria
    public void readFromNBT(NBTTagCompound nbt) {
        NBTHelper.readTagCollection(nbt, "ClaimedRewards", RewardSet.INSTANCE.setCollection(claimedRewards));
        NBTHelper.readTagCollection(nbt, "ImpossibleCriteria", CriteriaSet.INSTANCE.setCollection(impossible));
        NBTHelper.readTagCollection(nbt, "CompletedTriggers", TriggerNBT.INSTANCE.setCollection(completedTriggers));
        NBTHelper.readMap(nbt, "UnclaimedRewards", UnclaimedNBT.INSTANCE.setMap(unclaimedRewards));
        NBTHelper.readMap(nbt, "CompletedCriteria", CriteriaNBT.INSTANCE.setMap(completedCritera));
        NBTHelper.readMap(nbt, "MemberRewardCounter", RewardCountNBT.INSTANCE.setMap(numberRewards));
        readTriggerData(nbt);
    }

    private void writeTriggerData(NBTTagCompound nbt) {
    //Save the extra data for the existing triggers
        NBTTagList data = new NBTTagList();
        for (UUID uuid : triggerDataMap.keySet()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("UUID", uuid.toString());
            NBTTagCompound triggerNBT = new NBTTagCompound();
            triggerDataMap.get(uuid).writeDataToNBT(triggerNBT);
            tag.setTag("Data", triggerNBT);
            data.appendTag(tag);
        }

        nbt.setTag("ActiveTriggerData", data);
    }

    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTHelper.writeCollection(nbt, "ClaimedRewards", RewardSet.INSTANCE.setCollection(claimedRewards));
        NBTHelper.writeCollection(nbt, "ImpossibleCriteria", CriteriaSet.INSTANCE.setCollection(impossible));
        NBTHelper.writeCollection(nbt, "CompletedTriggers", TriggerNBT.INSTANCE.setCollection(completedTriggers));
        NBTHelper.writeMap(nbt, "UnclaimedRewards", UnclaimedNBT.INSTANCE.setMap(unclaimedRewards));
        NBTHelper.writeMap(nbt, "CompletedCriteria", CriteriaNBT.INSTANCE.setMap(completedCritera));
        NBTHelper.writeMap(nbt, "MemberRewardCounter", RewardCountNBT.INSTANCE.setMap(numberRewards));
        writeTriggerData(nbt);
        return nbt;
    }

    public HashMap<ICriteria, Integer> getCompletedCriteria() {
        return completedCritera;
    }

    public Set<ITrigger> getCompletedTriggers() {
        return completedTriggers;
    }

    public void markCriteriaAsCompleted(boolean overwrite, Integer[] values, ICriteria... conditions) {
        if (overwrite) completedCritera = new HashMap();
        for (int i = 0; i < values.length; i++) {
            if (values[i] == 0) {
                completedCritera.remove(conditions[i]);
            } else completedCritera.put(conditions[i], values[i]);
        }
    }

    public void markTriggerAsCompleted(boolean overwrite, Set<ITrigger> triggers) {
        if (overwrite) completedTriggers = new HashSet();
        completedTriggers.addAll(triggers);
    }

    public void setTriggerData(boolean overwrite, PacketSyncTriggerData.DataPair[] pairs) {
        if (overwrite) triggerDataMap = new HashMap();
        for (PacketSyncTriggerData.DataPair pair: pairs) {
            ITrigger trigger = APIHandler.getCache().getTriggerFromUUID(pair.uuid);
            if (trigger != null) {
                ((IStoreTriggerData)trigger).readDataFromNBT(pair.data); //Fuck with local cache
            }
        }
    }

    private boolean containsAny(List<ICriteria> list) {
        for (ICriteria criteria : list) {
            if (completedCritera.keySet().contains(criteria)) return true;
        }

        return false;
    }

    public void sendTriggerDataToClient(ITrigger trigger) {
        if (trigger instanceof IStoreTriggerData) {
            sendTriggerDataToClient(trigger.getUniqueID(), (IStoreTriggerData) trigger);
        }
    }

    public void sendTriggerDataToClient(UUID uuid, IStoreTriggerData trigger) {
        NBTTagCompound nbt = new NBTTagCompound();
        trigger.writeDataToNBT(nbt);
        PacketHandler.sendToTeam(new PacketSyncTriggerData(uuid, nbt), master.team);
    }
    
    public boolean isImpossible(ICriteria criteria) {
        return impossible.contains(criteria);
    }
    
    public void setImpossibles(ICriteria... criteria) {
        for (ICriteria c : criteria) {
            impossible.add(c);
        }
    }
    
    public void switchPossibility(ICriteria criteria) {
        boolean isPossible = !isImpossible(criteria);
        if (isPossible) impossible.add(criteria);
        else CollectionHelper.remove(impossible, criteria);
        
        data.markDirty();
        PacketHandler.sendToTeam(new PacketSyncImpossible(impossible.toArray(new ICriteria[impossible.size()])), master.team);
    }
    
    /** Called to fire a trigger type, Triggers are only ever called on criteria that is activated **/
    public Result fireAllTriggers(String type, Object... triggerData) {
        if (activeTriggers == null) return Result.DEFAULT; //If the remapping hasn't occured yet, say goodbye!
        //If the trigger is a forced removal, then force remve it
        if (type.equals("forced-remove")) {
            ICriteria criteria = (ICriteria) triggerData[0];
            if (criteria == null || !completedCritera.keySet().contains(criteria)) return Result.DEFAULT;
            else removeCriteria(criteria);
            remap(); //Remap everything
            data.markDirty();
            return Result.ALLOW;
        }

        boolean completedAnyCriteria = false;
        Collection<ITrigger> triggers = activeTriggers.get(type);
        HashSet<ITrigger> cantContinue = new HashSet();
        List<ITrigger> toTrigger = new ArrayList();
        for (ITrigger trigger : triggers) {
            Collection<ICondition> conditions = trigger.getConditions();
            for (ICondition condition : conditions) { //If we're bypassing everything, ignore conditions
                if (condition.isSatisfied(master.team) == condition.isInverted()) {
                    cantContinue.add(trigger);
                    break;
                }
            }

            if (cantContinue.contains(trigger)) continue; //Grab the old data
            toTrigger.add(trigger); //Add triggers for firing
        }

        //Fire the trigger
        Collections.shuffle(toTrigger);
        for (ITrigger trigger : toTrigger) {
            if (trigger instanceof ICancelable) {
                boolean isCancelingEnabled = (((ICancelable) trigger).isCanceling());
                if ((trigger.onFired(uuid, triggerData))) {
                     if (isCancelingEnabled) {
                         sendTriggerDataToClient(trigger); //Send updated trigger before returning
                         return Result.DENY;
                     }
                }
            } else if (!trigger.onFired(uuid, triggerData)) {
                sendTriggerDataToClient(trigger); //Send updated trigger before denying
                return Result.DENY;
            }

            //After everything send updated trigger no matter what
            sendTriggerDataToClient(trigger);
        }
        
        //Next step, now that the triggers have been fire, we need to go through them again
        //Check if they have been satisfied, and if so, mark them as completed triggers
        HashSet<ITrigger> toRemove = new HashSet();
        for (ITrigger trigger : triggers) {
            if (cantContinue.contains(trigger)) continue; //If we're bypassing mark all triggers as fired
            if (trigger.isCompleted()) {
                completedTriggers.add(trigger);
                toRemove.add(trigger);
                PacketHandler.sendToTeam(new PacketSyncTriggers(trigger), master.team);
            }
        }

        //Create a list of new triggers to add to the active trigger map
        HashSet<ICriteria> toComplete = new HashSet();

        //Next step, now that we have fired the trigger, we need to go through all the active criteria
        //We should check if all triggers have been fulfilled
        for (ITrigger trigger : triggers) {
            if (cantContinue.contains(trigger) || trigger.getCriteria() == null) continue;
            ICriteria criteria = trigger.getCriteria();
            if (impossible.contains(criteria)) continue;
            
            //Check that all triggers are in the completed set
            List<ITrigger> allTriggers = criteria.getTriggers();
            boolean allRequired = criteria.getIfRequiresAllTasks();
            int countRequired = criteria.getTasksRequired();
            int firedCount = 0;
            boolean allFired = true;
            for (ITrigger criteriaTrigger : allTriggers) { //the completed triggers map, doesn't contains all the requirements, then we need to remove it
                if (!completedTriggers.contains(criteriaTrigger)) {
                    allFired = false;
                } else firedCount++;
            }

            //Complete the criteria and bypass any requirements
            if ((allFired && allRequired) || (!allRequired && firedCount >= countRequired)) {
                completedAnyCriteria = true;
                toComplete.add(criteria);
            }
        }

        //Remove completed triggers from the active map
        triggers.removeAll(toRemove);

        //Add the bypassing of requirements completion
        if (type.equals("forced-complete")) {
            ICriteria criteria = (ICriteria) triggerData[0];
            boolean repeat = criteria.canRepeatInfinite();
            if (!repeat) {
                int max = criteria.getRepeatAmount();
                int last = getCriteriaCount(criteria);
                repeat = last < max;
            }

            if (repeat) { //If we're allowed to fire again, do so
                completedAnyCriteria = true;
                toComplete.add(criteria);
            }
        }

        //Now that we have removed all the triggers, and marked this as completed and remapped data,
        // we should add the rewards to the unclaimed rewards list
        for (ICriteria criteria : toComplete) {
            for (IReward reward: criteria.getRewards()) {
                for (UUID uuid: master.team.getEveryone()) {
                    unclaimedRewards.get(uuid).addAll(criteria.getRewards());
                }
            }
        }

        Set<ICriteria> completed = new HashSet();
        //Now we should try and dish out any automatic rewards
        for (UUID uuid: unclaimedRewards.keySet()) {
            List<IReward> list = Lists.newArrayList(unclaimedRewards.get(uuid));
            Set<IReward> remove = new HashSet();
            Collections.shuffle(list);
            for (IReward reward: list) {
                if (reward.isAutomatic()) {
                    EntityPlayerMP aPlayer = (EntityPlayerMP) PlayerHelper.getPlayerFromUUID(uuid);
                    if (aPlayer != null) {
                        if(claimReward(aPlayer, reward)) {
                            completed.add(reward.getCriteria());
                        }

                        remove.add(reward); //Remove this from unclaimed list, once we have claimed it
                    }
                }
            }

            CollectionHelper.removeAll(unclaimedRewards.get(uuid), list);
        }

        for (ICriteria criteria: completed) {
            remapAfterClaiming(criteria);
        }

        //Mark data as dirty, whether it changed or not
        data.markDirty();
        return completedAnyCriteria ? Result.ALLOW : Result.DEFAULT;
    }

    private int getRewardsGiven(UUID uuid, ICriteria criteria) {
        int total = 0;
        if (numberRewards.get(uuid) != null && numberRewards.get(uuid).get(criteria) != null) {
            total = numberRewards.get(uuid).get(criteria);
        }

        return total;
    }

    private void setRewardsGiven(UUID uuid, ICriteria criteria, int amount) {
        HashMap map = numberRewards.get(uuid);
        if (map == null) {
            map = new HashMap();
            numberRewards.put(uuid, map);
        }

        map.put(criteria, amount);
    }

    public void remapAfterClaiming(ICriteria criteria) {
        HashSet<ITrigger> forRemovalFromActive = new HashSet(); //Reset them
        HashSet<ICriteria> toRemap = new HashSet();
        completeCriteria(criteria, forRemovalFromActive, toRemap);
        remapStuff(forRemovalFromActive, toRemap);
        for (UUID uuid: master.team.getEveryone()) {
            setRewardsGiven(uuid, criteria, 0);
        }
    }


    public boolean claimReward(EntityPlayerMP player, IReward reward) {
        UUID uuid = PlayerHelper.getUUIDForPlayer(player);
        ICriteria criteria = reward.getCriteria();
        int rewardsGiven = getRewardsGiven(uuid, criteria);
        if (rewardsGiven < criteria.getAmountOfRewards() || criteria.givesAllRewards()) {
            reward.reward(player);
            rewardsGiven++; //Increase the amount
        }

        setRewardsGiven(uuid, criteria, rewardsGiven);
        if ((!criteria.givesAllRewards() && rewardsGiven >= criteria.getAmountOfRewards()) || (criteria.givesAllRewards() && rewardsGiven >= criteria.getRewards().size())) { //If all the rewards have been given out, then do some remapping of everything
            return true;
        }

        return false;
    }

    public void removeCriteria(ICriteria criteria) {
        completedCritera.remove(criteria);
        PacketHandler.sendToTeam(new PacketSyncCriteria(false, new Integer[] { 0 }, new ICriteria[] { criteria }), master.team);
    }

    private void completeCriteria(ICriteria criteria, HashSet<ITrigger> forRemovalFromActive, HashSet<ICriteria> toRemap) {
        List<ITrigger> allTriggers = criteria.getTriggers();
        int completedTimes = getCriteriaCount(criteria);
        completedTimes++;
        completedCritera.put(criteria, completedTimes);
        //Now that we have updated how times we have completed this quest
        //We should mark all the triggers for removal from activetriggers, as well as actually remove their stored data
        for (ITrigger criteriaTrigger : allTriggers) {
            forRemovalFromActive.add(criteriaTrigger);
            //Remove all the conflicts triggers
            for (ICriteria conflict : criteria.getConflicts()) {
                forRemovalFromActive.addAll(conflict.getTriggers());
            }

            boolean repeat = criteria.canRepeatInfinite();
            if (!repeat) {
                int max = criteria.getRepeatAmount();
                int last = getCriteriaCount(criteria);
                repeat = last < max;
            }

            if (repeat) {
                CollectionHelper.remove(completedTriggers, criteriaTrigger);
                if (criteriaTrigger instanceof IStoreTriggerData) {
                    triggerDataMap.get(criteriaTrigger.getUniqueID()).readDataFromNBT(new NBTTagCompound());
                    sendTriggerDataToClient(criteriaTrigger); //Let the client know it was wiped
                }
            }
        }

        //The next step in the process is to update the active trigger maps for everything
        //That we unlock with this criteria have been completed
        toRemap.add(criteria);

        if (completedTimes == 1) { //Only do shit if this is the first time it was completed                    
            toRemap.addAll(RemappingHandler.criteriaToUnlocks.get(criteria));
        }

        Set<EntityPlayerMP> list = PlayerHelper.getPlayersFromUUID(uuid);
        for (EntityPlayerMP player : list) {
            PacketHandler.sendToTeam(new PacketSyncCriteria(false, new Integer[] { completedTimes }, new ICriteria[] { criteria }), master.team);
            if (criteria.displayAchievement()) PacketHandler.sendToTeam(new PacketCompleted(criteria), master.team);
        }
    }

    private void remapStuff(HashSet<ITrigger> forRemovalFromActive, HashSet<ICriteria> toRemap) {
        //Removes all the triggers from the active map
        for (ITrigger trigger : forRemovalFromActive) {
            activeTriggers.get(trigger.getUnlocalisedName()).remove(trigger);
        }

        //Remap the criteria
        for (ICriteria criteria : toRemap) {
            remapCriteriaOnCompletion(criteria);
        }
    }

    public int getCriteriaCount(ICriteria criteria) {
        int amount = 0;
        Integer last = completedCritera.get(criteria);
        if (last != null) {
            amount = last;
        }

        return amount;
    }

    private void remapCriteriaOnCompletion(ICriteria criteria) {
        ICriteria available = null;
        //We are now looping though all criteria, we now need to check to see if this
        //First step is to validate to see if this criteria, is available right now
        //If the criteria is repeatable, or is not completed continue
        boolean repeat = criteria.canRepeatInfinite();
        if (!repeat) {
            int max = criteria.getRepeatAmount();
            int last = getCriteriaCount(criteria);
            repeat = last < max;
        }

        if (repeat) {
            if (completedCritera.keySet().containsAll(criteria.getPreReqs())) {
                //If we have all the requirements, continue
                //Now that we know that we have all the requirements, we should check for conflicts
                //If it doesn't contain any of the conflicts, continue forwards
                if (!containsAny(criteria.getConflicts())) {
                    //The Criteria passed the check for being available, mark it as so
                    available = criteria;
                }
            }

            //If we are allowed to redo triggers, remove from completed
            completedTriggers.removeAll(criteria.getTriggers());
            //Remove all data for the triggers too
            for (ITrigger trigger: criteria.getTriggers()) {
                if (trigger instanceof IStoreTriggerData) {
                    triggerDataMap.get(trigger.getUniqueID()).readDataFromNBT(new NBTTagCompound());
                    sendTriggerDataToClient(trigger); //Let the client know it was wiped
                }
            }
        }

        if (available != null) {
            List<ITrigger> triggers = criteria.getTriggers(); //Grab a list of all the triggers
            for (ITrigger trigger : triggers) {
                //If we don't have the trigger in the completed map, mark it as available in the active triggers
                if (!completedTriggers.contains(trigger)) {
                    ITrigger clone = trigger.copy();
                    if (clone instanceof IStoreTriggerData) { //Create a new copy when we remap, with updated requirements
                        if (triggerDataMap.containsKey(trigger.getUniqueID())) {
                            NBTTagCompound tag = new NBTTagCompound(); //Create a tag
                            triggerDataMap.get(trigger.getUniqueID()).writeDataToNBT(tag); //Write to it
                            ((IStoreTriggerData) clone).readDataFromNBT(tag); //Copy the old data to the clone
                        }

                        triggerDataMap.put(clone.getUniqueID(), (IStoreTriggerData) clone); //Remap the triggers data
                    }

                    activeTriggers.get(clone.getUnlocalisedName()).add(clone);
                }
            }
        }
    }

    public void remap() {
        Progression.logger.log(Level.INFO, "Progression began remapping for the uuid: " + uuid);
        Set<ICriteria> availableCriteria = new HashSet(); //Recreate the available mappings
        activeTriggers = HashMultimap.create(); //Recreate the trigger mappings

        Collection<ICriteria> allCriteria = APIHandler.getCriteria().values();
        for (ICriteria criteria : allCriteria) {
            //If the criteria has been marked as impossible don't attach it to anything
            if (impossible.contains(criteria)) continue;
            
            //We are now looping though all criteria, we now need to check to see if this
            //First step is to validate to see if this criteria, is available right now
            //If the criteria is repeatable, or is not completed continue
            boolean repeat = criteria.canRepeatInfinite();
            if (!repeat) {
                int max = criteria.getRepeatAmount();
                int last = getCriteriaCount(criteria);
                repeat = last < max;
            }

            if (repeat) {
                if (completedCritera.keySet().containsAll(criteria.getPreReqs())) {
                    //If we have all the requirements, continue
                    //Now that we know that we have all the requirements, we should check for conflicts
                    //If it doesn't contain any of the conflicts, continue forwards
                    if (!containsAny(criteria.getConflicts())) {
                        //The Criteria passed the check for being available, mark it as so
                        availableCriteria.add(criteria);
                    }
                }
            }
        }

        //Now that we have remapped all of the criteria, we should remap the triggers
        for (ICriteria criteria : availableCriteria) {
            List<ITrigger> triggers = criteria.getTriggers(); //Grab a list of all the triggers
            for (ITrigger trigger : triggers) {
                //If we don't have the trigger in the completed map, mark it as available in the active triggers
                ITrigger clone = trigger.copy();
                if (clone instanceof IStoreTriggerData) { //Create a new copy when we remap, with updated requirements
                    if (triggerDataMap.containsKey(trigger.getUniqueID())) {
                        NBTTagCompound tag = new NBTTagCompound(); //Create a tag
                        triggerDataMap.get(trigger.getUniqueID()).writeDataToNBT(tag); //Write to it
                        ((IStoreTriggerData) clone).readDataFromNBT(tag); //Copy the old data to the clone
                    }

                    triggerDataMap.put(clone.getUniqueID(), (IStoreTriggerData) clone); //Remap the triggers data
                }

                //Only mark it as active if applicable
                if (!completedTriggers.contains(trigger)) {
                    activeTriggers.get(clone.getUnlocalisedName()).add(clone);
                }
            }
        }
    }
}

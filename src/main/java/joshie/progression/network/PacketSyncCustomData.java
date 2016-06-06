package joshie.progression.network;

import joshie.progression.network.core.PacketNBT;
import joshie.progression.player.PlayerTracker;
import joshie.progression.player.data.CustomStats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;

@Packet(isSided = true, side = Side.CLIENT)
public class PacketSyncCustomData extends PacketNBT {
    public PacketSyncCustomData() {}
    public PacketSyncCustomData(INBTWritable readable) {
        super(readable);
    }
    
    @Override
    public void handlePacket(EntityPlayer player) {
        PlayerTracker.getClientPlayer().setCustomData(new CustomStats().readFromNBT(tag));
    }
}

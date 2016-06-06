package joshie.progression.network;

import io.netty.buffer.ByteBuf;
import joshie.progression.Progression;
import joshie.progression.handlers.RemappingHandler;
import joshie.progression.helpers.MCClientHelper;
import joshie.progression.helpers.PlayerHelper;
import joshie.progression.json.JSONLoader;
import joshie.progression.json.Options;
import joshie.progression.network.core.PenguinPacket;
import joshie.progression.player.PlayerTracker;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.fml.common.network.ByteBufUtils;

@Packet
public class PacketReset extends PenguinPacket {
    private boolean singlePlayer;
    private String username;

    public PacketReset() {
        singlePlayer = false;
    }

    public PacketReset(String string) {
        singlePlayer = true;
        username = string;
    }

    @Override
    public void handlePacket(EntityPlayer player) {
        PacketReset.handle(player, singlePlayer, username);
    }

    @Override
    public void toBytes(ByteBuf to) {
        to.writeBoolean(singlePlayer);
        if (singlePlayer) {
            ByteBufUtils.writeUTF8String(to, username);
        }
    }

    @Override
    public void fromBytes(ByteBuf from) {
        singlePlayer = from.readBoolean();
        if (singlePlayer) {
            username = ByteBufUtils.readUTF8String(from);
        }
    }

    public static void handle(EntityPlayer sender, boolean singlePlayer, String username) {
        if (sender.worldObj.isRemote) {
            if (!singlePlayer) MCClientHelper.getPlayer().addChatComponentMessage(new ChatComponentText("All player data for Progression was reset."));
            else MCClientHelper.getPlayer().addChatComponentMessage(new ChatComponentText("All player data for " + username + " was reset."));
        } else {
            if (Options.editor) {
                if (!singlePlayer) {
                    if (Options.hardReset) Progression.instance.createWorldData(); //Recreate the world data, Wiping out any saved information for players
                    else Progression.data.clear();
                    RemappingHandler.reloadServerData(JSONLoader.getServerTabData(RemappingHandler.getHostName()), false);
                    for (EntityPlayerMP player : PlayerHelper.getAllPlayers()) {
                        //Reset all the data to default
                        RemappingHandler.onPlayerConnect(player);
                    }

                    PacketHandler.sendToEveryone(new PacketReset());
                } else {
                    if (PlayerTracker.reset(username) && sender instanceof EntityPlayerMP) {
                        PacketHandler.sendToClient(new PacketReset(username), sender);
                    }
                }
            }
        }
    }
}

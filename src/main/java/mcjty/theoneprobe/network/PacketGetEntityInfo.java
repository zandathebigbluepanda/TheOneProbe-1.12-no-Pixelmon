package mcjty.theoneprobe.network;

import io.netty.buffer.ByteBuf;
import mcjty.theoneprobe.TheOneProbe;
import mcjty.theoneprobe.api.IProbeHitEntityData;
import mcjty.theoneprobe.api.IProbeInfoEntityProvider;
import mcjty.theoneprobe.api.ProbeMode;
import mcjty.theoneprobe.apiimpl.ProbeHitEntityData;
import mcjty.theoneprobe.apiimpl.ProbeInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.List;
import java.util.UUID;

public class PacketGetEntityInfo implements IMessage {

    private int dim;
    private UUID uuid;
    private ProbeMode mode;
    private Vec3d hitVec;

    @Override
    public void fromBytes(ByteBuf buf) {
        dim = buf.readInt();
        uuid = new UUID(buf.readLong(), buf.readLong());
        mode = ProbeMode.values()[buf.readByte()];
        if (buf.readBoolean()) {
            hitVec = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(dim);
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
        buf.writeByte(mode.ordinal());
        if (hitVec == null) {
            buf.writeBoolean(false);
        } else {
            buf.writeBoolean(true);
            buf.writeDouble(hitVec.xCoord);
            buf.writeDouble(hitVec.yCoord);
            buf.writeDouble(hitVec.zCoord);
        }
    }

    public PacketGetEntityInfo() {
    }

    public PacketGetEntityInfo(int dim, ProbeMode mode, RayTraceResult mouseOver) {
        this.dim = dim;
        this.uuid = mouseOver.entityHit.getPersistentID();
        this.mode = mode;
        this.hitVec = mouseOver.hitVec;
    }

    public static class Handler implements IMessageHandler<PacketGetEntityInfo, IMessage> {
        @Override
        public IMessage onMessage(PacketGetEntityInfo message, MessageContext ctx) {
            FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() -> handle(message, ctx));
            return null;
        }

        private void handle(PacketGetEntityInfo message, MessageContext ctx) {
            WorldServer world = DimensionManager.getWorld(message.dim);
            if (world != null) {
                Entity entity = world.getEntityFromUuid(message.uuid);
                if (entity != null) {
                    ProbeInfo probeInfo = getProbeInfo(ctx.getServerHandler().playerEntity, message.mode, world, entity, message.hitVec);
                    PacketHandler.INSTANCE.sendTo(new PacketReturnEntityInfo(message.uuid, probeInfo), ctx.getServerHandler().playerEntity);
                }
            }
        }
    }

    private static ProbeInfo getProbeInfo(EntityPlayer player, ProbeMode mode, World world, Entity entity, Vec3d hitVec) {
        ProbeInfo probeInfo = TheOneProbe.theOneProbeImp.create();
        IProbeHitEntityData data = new ProbeHitEntityData(hitVec);

        List<IProbeInfoEntityProvider> entityProviders = TheOneProbe.theOneProbeImp.getEntityProviders();
        for (IProbeInfoEntityProvider provider : entityProviders) {
            try {
                provider.addProbeEntityInfo(mode, probeInfo, player, world, entity, data);
            } catch (Exception e) {
                probeInfo.text(TextFormatting.RED + "Error: " + provider.getID());
                TheOneProbe.logger.error("The One Probe catched error: ", e);
            }
        }
        return probeInfo;
    }

}
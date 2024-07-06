package earth.terrarium.tempad.common.network.c2s

import com.teamresourceful.bytecodecs.base.ByteCodec
import com.teamresourceful.bytecodecs.base.`object`.ObjectByteCodec
import com.teamresourceful.resourcefullib.common.bytecodecs.ExtraByteCodecs
import com.teamresourceful.resourcefullib.common.network.Packet
import com.teamresourceful.resourcefullib.common.network.base.NetworkHandle
import com.teamresourceful.resourcefullib.common.network.base.PacketType
import com.teamresourceful.resourcefullib.common.network.defaults.CodecPacketType
import earth.terrarium.tempad.Tempad.Companion.tempadId
import earth.terrarium.tempad.api.locations.TempadLocations
import earth.terrarium.tempad.common.entity.TimedoorEntity
import net.minecraft.resources.ResourceLocation
import java.util.*

data class OpenTimedoorPacket(val providerId: ResourceLocation, val locationId: UUID, val slotId: Int) :
    Packet<OpenTimedoorPacket> {
    companion object {
        val TYPE = CodecPacketType.Server.create(
            "open_timedoor".tempadId,
            ObjectByteCodec.create(
                ExtraByteCodecs.RESOURCE_LOCATION.fieldOf { it.providerId },
                ByteCodec.UUID.fieldOf { it.locationId },
                ByteCodec.INT.fieldOf { it.slotId },
                ::OpenTimedoorPacket
            ),
            NetworkHandle.handle { message, player ->
                if (TempadLocations[message.providerId] == null) return@handle
                val location = TempadLocations[player, message.providerId]?.let { it[message.locationId] }
                location?.let { TimedoorEntity.openTimedoor(player, message.slotId, it) }
            }
        )
    }

    override fun type(): PacketType<OpenTimedoorPacket> = TYPE
}

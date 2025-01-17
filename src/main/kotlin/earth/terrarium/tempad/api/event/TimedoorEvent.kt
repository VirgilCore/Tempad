package earth.terrarium.tempad.api.event

import earth.terrarium.tempad.api.context.SyncableContext
import earth.terrarium.tempad.common.entity.TimedoorEntity
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.neoforged.bus.api.ICancellableEvent
import net.neoforged.neoforge.event.entity.EntityEvent

open class TimedoorEvent(timedoor: TimedoorEntity): EntityEvent(timedoor) {
    override fun getEntity(): TimedoorEntity {
        return super.getEntity() as TimedoorEntity
    }

    class Open(timedoor: TimedoorEntity, val opener: Player, val tempadCtx: SyncableContext<*>): TimedoorEvent(timedoor), ICancellableEvent {
        var errorMessage: Component? = null

        /**
         * If you cancel the event, you should provide a reason why.
         */
        fun fail(message: Component) {
            isCanceled = true
            errorMessage = message
        }
    }
    class Close(timedoor: TimedoorEntity): TimedoorEvent(timedoor)
    class Enter(timedoor: TimedoorEntity, val teleportee: Entity): TimedoorEvent(timedoor), ICancellableEvent
    class Exit(timedoor: TimedoorEntity, val teleportee: Entity): TimedoorEvent(timedoor)
}
package earth.terrarium.tempad.common.entity

import com.teamresourceful.resourcefullib.common.color.Color
import earth.terrarium.tempad.Tempad
import earth.terrarium.tempad.api.context.SyncableContext
import earth.terrarium.tempad.api.context.drain
import earth.terrarium.tempad.api.event.TimedoorEvent
import earth.terrarium.tempad.api.locations.NamedGlobalPos
import earth.terrarium.tempad.api.locations.StaticNamedGlobalPos
import earth.terrarium.tempad.api.sizing.DefaultSizing
import earth.terrarium.tempad.api.sizing.DoorType
import earth.terrarium.tempad.api.sizing.TimedoorSizing
import earth.terrarium.tempad.common.config.CommonConfig
import earth.terrarium.tempad.common.network.s2c.RotatePlayerMomentumPacket
import earth.terrarium.tempad.common.registries.ModEntities
import earth.terrarium.tempad.common.registries.ModTags
import earth.terrarium.tempad.common.registries.ageUntilAllowedThroughTimedoor
import earth.terrarium.tempad.common.registries.chrononContent
import earth.terrarium.tempad.common.utils.*
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.*
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.portal.DimensionTransition
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.common.Tags
import java.util.*
import kotlin.jvm.optionals.getOrNull

class TimedoorEntity(type: EntityType<*>, level: Level) : Entity(type, level) {
    companion object {
        internal const val IDLE_BEFORE_START = 10
        internal const val ANIMATION_LENGTH = 6
        private val CLOSING_TIME = createDataKey<TimedoorEntity, Int>(EntityDataSerializers.INT)
        private val COLOR = createDataKey<TimedoorEntity, Color>(ModEntities.colorSerializer)
        private val TARGET_POS = createDataKey<TimedoorEntity, Vec3>(ModEntities.vec3Serializer)
        private val TARGET_DIMENSION =
            createDataKey<TimedoorEntity, ResourceKey<Level>>(ModEntities.dimensionKeySerializer)
        private val SIZING = createDataKey<TimedoorEntity, TimedoorSizing>(ModEntities.sizingSerializer)

        //feedback
        private val fail = Component.translatable("entity.tempad.timedoor.fail")
        private val posFail = Component.translatable("entity.tempad.timedoor.fail.pos")
        private val interDimFail = Component.translatable("entity.tempad.timedoor.fail.interdimensional")
        private val intraDimAllFail = Component.translatable("entity.tempad.timedoor.fail.interdimensional_all")
        private val intraDimFail = Component.translatable("entity.tempad.timedoor.fail.interdimensional")
        private val enteringFail = Component.translatable("entity.tempad.timedoor.fail.entering")
        private val leavingFail = Component.translatable("entity.tempad.timedoor.fail.leaving")
        private val noChrononsFail = Component.translatable("entity.tempad.timedoor.fail.no_chronons")

        fun openTimedoor(player: Player, ctx: SyncableContext<*>, location: NamedGlobalPos): Component? {
            val stack = ctx.stack
            val targetDimension = location.dimension ?: return posFail
            location.pos ?: return posFail
            val lookup = player.level().registryAccess().lookup(Registries.DIMENSION)
            val targetHolder = lookup.get().get(targetDimension).getOrNull() ?: return posFail
            val sourceHolder = lookup.get().get(player.level().dimension()).getOrNull() ?: return posFail
            if (!player.isCreative) {
                if (stack.chrononContent < 1000) return noChrononsFail
                player.level().dimension().let {
                    if (it != location.dimension) {
                        if (!CommonConfig.allowInterdimensionalTravel) return interDimFail
                        if (sourceHolder in ModTags.leavingNotSupported) return leavingFail
                        if (targetHolder in ModTags.enteringNotSupported) return enteringFail
                    } else {
                        if (!CommonConfig.allowIntradimensionalTravel) return intraDimAllFail
                        if (sourceHolder in ModTags.intradimensionalTravelNotSupported) return intraDimFail
                    }
                }
            }

            val timedoor = TimedoorEntity(ModEntities.TIMEDOOR_ENTITY, player.level()).apply {
                owner = player.uuid
                sizing = if (player.xRot > 45) DefaultSizing.FLOOR else DefaultSizing.DEFAULT
                sizing.placeTimedoor(DoorType.ENTRY, player.position(), player.yRot, this)
                setLocation(location)
            }
            val event = TimedoorEvent.Open(timedoor, player, ctx).post()
            if (event.isCanceled) return event.errorMessage ?: fail
            else if (CommonConfig.TimeDoor.logWhenOpen) {
                Tempad.logger.debug(
                    "Player {} opened a timedoor at {} in dimension {} to {} in dimension {}",
                    player.name.string,
                    player.blockPosition(),
                    player.level().dimension(),
                    location.name,
                    targetDimension
                )
            }
            if (!player.isCreative) {
                ctx.drain(1000)
                player.cooldowns.addCooldown(stack.item, 40)
            }
            player.level().addFreshEntity(timedoor)
            return null
        }
    }

    private fun canTeleport(entity: Entity, targetLevel: Level): Boolean {
        with(sizing) {
            return entity !is TimedoorEntity
                    && isInside(entity)
                    && entity !in ModTags.teleportingNotSupport
                    && entity.canChangeDimensions(level(), targetLevel)
                    && !entity.isPassenger
                    && entity.ageUntilAllowedThroughTimedoor?.let { entity.tickCount > it } ?: true
        }
    }

    var targetAngle = 0f

    var targetPos by DataDelegate(TARGET_POS)
    var targetDimension by DataDelegate(TARGET_DIMENSION)
    var color by DataDelegate(COLOR)
    var closingTime by DataDelegate(CLOSING_TIME)
    var sizing: TimedoorSizing
        get() = entityData.get(SIZING)
        set(value) {
            entityData.set(SIZING, value)
            this.fixupDimensions()
        }

    var linkedPortalEntity: TimedoorEntity? = null
        private set

    var owner: UUID? = null

    private val targetLevel: ServerLevel?
        get() = targetDimension.let { level().server[it] }

    private val selfLocation: StaticNamedGlobalPos
        get() = StaticNamedGlobalPos(
            name,
            StaticNamedGlobalPos.offsetLocation(this.pos, this.yRot),
            level().dimension(),
            yRot,
            color
        )

    override fun defineSynchedData(pBuilder: SynchedEntityData.Builder) {
        pBuilder.define(CLOSING_TIME, CommonConfig.TimeDoor.idleAfterEnter)
        pBuilder.define(COLOR, Tempad.ORANGE)
        pBuilder.define(TARGET_POS, Vec3.ZERO)
        pBuilder.define(TARGET_DIMENSION, Level.OVERWORLD)
        pBuilder.define(SIZING, DefaultSizing.DEFAULT)
    }

    override fun getDimensions(pose: Pose): EntityDimensions = sizing.dimensions

    override fun isAlwaysTicking() = true

    override fun tick() {
        if (level().isClientSide()) {
            if (sizing.dimensions.width != bbWidth || sizing.dimensions.height != bbHeight) {
                this.fixupDimensions()
                this.boundingBox = makeBoundingBox()
            }
            if (tickCount < IDLE_BEFORE_START) {
                level().addParticle(
                    DustParticleOptions(color.vec3f, 1.0f),
                    true,
                    x,
                    y + bbHeight / 2.0,
                    z,
                    0.0,
                    0.0,
                    0.0,
                )
            }
            return
        }
        if (tickCount < IDLE_BEFORE_START + ANIMATION_LENGTH) {
            return
        }
        if (tickCount > closingTime && closingTime != -1) {
            tryClose()
            return
        }
        val targetLevel = targetLevel ?: return tryClose()
        val entities = level().getEntities<Entity>(boundingBox) { canTeleport(it, targetLevel) }
        if (entities.isEmpty()) {
            tryClose()
            return
        }
        this.resetClosingTime()
        tryInitReceivingPortal()
        for (entity in entities) {
            val event = TimedoorEvent.Enter(this, entity).post()
            if (event.isCanceled) continue

            if (entity.level().dimension() == targetLevel.dimension()) {
                entity.deltaMovement = entity.deltaMovement.yRot(this.yRot - targetAngle)
                entity.teleportTo(
                    targetLevel,
                    targetPos.x,
                    targetPos.y,
                    targetPos.z,
                    RelativeMovement.ALL,
                    targetAngle,
                    entity.xRot
                )
                entity.hasImpulse = true
                if (entity is Player) {
                    RotatePlayerMomentumPacket(this.yRot - targetAngle).sendToClient(entity)
                }
                entity.ageUntilAllowedThroughTimedoor = entity.tickCount + 30
            } else {
                entity.changeDimension(
                    DimensionTransition(
                        targetLevel,
                        targetPos,
                        entity.deltaMovement,
                        targetAngle,
                        0.0F,
                        false,
                        DimensionTransition.DO_NOTHING
                    )
                )

                entity.ageUntilAllowedThroughTimedoor = entity.tickCount + 60
            }

            if (entity is Player && entity.uuid == owner && this.closingTime != -1) {
                this.closingTime = this.tickCount + CommonConfig.TimeDoor.idleAfterOwnerEnter
            }

            linkedPortalEntity?.let { TimedoorEvent.Exit(it, entity).post() }
        }
        tryClose()
    }

    private fun tryInitReceivingPortal() {
        val targetLevel = targetLevel ?: return
        linkedPortalEntity?.let {
            it.closingTime = this.closingTime - this.tickCount
            return
        }
        val targetPortal = TimedoorEntity(ModEntities.TIMEDOOR_ENTITY, targetLevel)
        targetPortal.linkedPortalEntity = this
        targetPortal.closingTime = this.closingTime - this.tickCount
        targetPortal.setLocation(selfLocation)
        targetPortal.sizing = sizing
        sizing.placeTimedoor(DoorType.EXIT, targetPos, targetAngle + 180f, targetPortal)
        linkedPortalEntity = targetPortal
        targetLevel.addFreshEntity(targetPortal)
    }

    private fun tryClose() {
        if (tickCount > closingTime + ANIMATION_LENGTH && closingTime != -1) {
            TimedoorEvent.Close(this).post()
            this.linkedPortalEntity?.linkedPortalEntity = null
            this.discard()
        }
    }

    override fun onRemovedFromLevel() {
        super.onRemovedFromLevel()
        if (this.linkedPortalEntity != null) this.linkedPortalEntity!!.linkedPortalEntity = null
        this.linkedPortalEntity = null
    }

    fun resetClosingTime() {
        if (closingTime != -1) {
            closingTime = this.tickCount + CommonConfig.TimeDoor.idleAfterEnter
        }
    }

    fun setLocation(location: NamedGlobalPos) {
        this.targetPos = location.pos!!
        this.targetDimension = location.dimension!!
        this.customName = location.name
        this.targetAngle = location.angle
        this.color = location.color
    }

    override fun readAdditionalSaveData(pCompound: CompoundTag) {}
    override fun addAdditionalSaveData(pCompound: CompoundTag) {}
}

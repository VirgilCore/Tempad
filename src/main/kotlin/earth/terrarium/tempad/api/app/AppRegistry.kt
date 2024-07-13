package earth.terrarium.tempad.api.app

import com.teamresourceful.bytecodecs.base.`object`.ObjectByteCodec
import com.teamresourceful.resourcefullib.common.bytecodecs.ExtraByteCodecs
import earth.terrarium.tempad.api.context.ContextInstance
import earth.terrarium.tempad.api.context.ItemContext
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Player

fun interface AppProvider {
    operator fun invoke(ctx: ContextInstance): TempadApp<*>?
}

data class AppHolder(val id: ResourceLocation, val ctx: ItemContext) {
    fun getApp(player: Player): TempadApp<*>? {
        return AppRegistry.get(id, ctx.getInstance(player))
    }
}

object AppRegistry {
    private val apps = mutableMapOf<ResourceLocation, AppProvider>()

    val BYTE_CODEC = ObjectByteCodec.create(
        ExtraByteCodecs.RESOURCE_LOCATION.fieldOf { it.id },
        ItemContext.codec.fieldOf { it.ctx },
        ::AppHolder
    )

    @JvmName("register")
    operator fun set(id: ResourceLocation, provider: AppProvider) {
        apps[id] = provider
    }

    operator fun get(id: ResourceLocation, ctx: ContextInstance): TempadApp<*>? {
        return apps[id]?.invoke(ctx)
    }

    fun getAll(ctx: ContextInstance): Map<ResourceLocation, TempadApp<*>> {
        return apps.mapValues { it.value(ctx) }.mapNotNull { it.value?.let { app -> it.key to app } }.toMap()
    }

    fun getIds(): Set<ResourceLocation> {
        return apps.keys
    }
}
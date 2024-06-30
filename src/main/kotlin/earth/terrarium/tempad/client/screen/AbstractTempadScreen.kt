package earth.terrarium.tempad.client.screen

import com.teamresourceful.resourcefullib.client.components.selection.SelectionList
import earth.terrarium.tempad.Tempad
import earth.terrarium.tempad.Tempad.Companion.tempadId
import earth.terrarium.tempad.api.app.AppRegistry
import earth.terrarium.tempad.api.fuel.FuelConsumer
import earth.terrarium.tempad.client.widgets.AppButton
import earth.terrarium.tempad.client.widgets.FuelBarWidget
import earth.terrarium.tempad.common.menu.AbstractTempadMenu
import earth.terrarium.tempad.common.network.c2s.OpenAppPacket
import earth.terrarium.tempad.common.registries.ModNetworking
import earth.terrarium.tempad.common.utils.get
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Inventory

abstract class AbstractTempadScreen<T: AbstractTempadMenu<*>>(val appSprite: ResourceLocation, menu: T, val inv: Inventory, title: Component): AbstractContainerScreen<T>(menu, inv, title) {
    init {
        this.imageWidth = 256
        this.imageHeight = 256
        this.titleLabelX = 36
        this.titleLabelY = 27
    }

    var localLeft: Int = 0
    var localTop: Int = 0

    companion object {
        val SPRITE = "screen/tempad".tempadId
    }

    override fun init() {
        super.init()
        this.localTop = this.topPos + 20
        this.localLeft = this.leftPos + 30

        val appList = SelectionList<AppButton>(localLeft - 16, localTop + 1, 16, 116, 16) { button ->
            ModNetworking.CHANNEL.sendToServer(OpenAppPacket(button!!.appId, menu.appContent?.slotId ?: -1))
        }

        for ((id, app) in AppRegistry.getAll(menu.appContent?.slotId ?: -1)) {
            appList.addEntry(AppButton(app, id))
        }

        addRenderableWidget(appList)

        val fuelConsumer = inv[menu.appContent?.slotId ?: -1].getCapability(FuelConsumer.CAPABILITY) ?: return

        addRenderableOnly(FuelBarWidget(fuelConsumer, 237, 52))
    }

    override fun renderBg(graphics: GuiGraphics, partialTick: Float, mouseX: Int, mouseY: Int) {
        graphics.blitSprite(SPRITE, this.leftPos, this.topPos, this.imageWidth, this.imageHeight)
        graphics.blitSprite(appSprite, this.leftPos + 30, this.topPos + 20, 198, 118)
    }

    override fun renderLabels(pGuiGraphics: GuiGraphics, pMouseX: Int, pMouseY: Int) {
        pGuiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, Tempad.ORANGE.value, true)
    }
}
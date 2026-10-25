package com.sb.peh.compat;

import com.Harbinger.Spore.core.SConfig;
import com.sb.peh.PlanetEscapeHandler;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Client side: Spore reads disable_vanilla on the client every tick (it calls
 * MusicManager.stopPlaying() while it is true), so that switch is flipped here,
 * using the client calendar.
 *   day   -> disable_vanilla = false  (TFC's music plays)
 *   night -> disable_vanilla = true   (vanilla music is held off, Spore plays)
 */
@EventBusSubscriber(modid = PlanetEscapeHandler.MODID, value = Dist.CLIENT)
public final class SMusicToggleClient {
    private SMusicToggleClient() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.getGameTime() % 20 != 0) {
            return;
        }
        SMusicToggle.set(SConfig.SERVER.disable_vanilla, SMusicGate.sporeTurn(true));
    }
}

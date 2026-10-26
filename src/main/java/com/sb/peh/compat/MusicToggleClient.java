package com.sb.peh.compat;

import com.Harbinger.Spore.core.SConfig;
import com.sb.peh.PlanetEscapeHandler;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Client side: Spore reads disable_vanilla and menu_song on the client every tick
 * (it calls MusicManager.stopPlaying() while disable_vanilla is true), so those
 * switches are flipped here.
 *   in a menu (no world loaded) -> menu_song = true,  disable_vanilla = true
 *   in a world, day             -> disable_vanilla = false (TFC / vanilla music plays)
 *   in a world, night           -> disable_vanilla = true  (Spore plays)
 */
@EventBusSubscriber(modid = PlanetEscapeHandler.MODID, value = Dist.CLIENT)
public final class MusicToggleClient {
    private MusicToggleClient() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            // Title screen etc: Spore's menu song on, vanilla menu music off. set() only writes on change.
            SMusicToggle.set(SConfig.SERVER.disable_vanilla, true);
            SMusicToggle.set(SConfig.SERVER.menu_song, true);
            return;
        }
        if (mc.level.getGameTime() % 20 != 0) {
            return;
        }
        SMusicToggle.set(SConfig.SERVER.disable_vanilla, SMusicGate.sporeTurn(true));
    }
}
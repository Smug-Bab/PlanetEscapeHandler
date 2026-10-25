package com.sb.peh.compat;

import com.Harbinger.Spore.core.SConfig;
import com.sb.peh.PlanetEscapeHandler;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Server side: Spore sends its ambient-song packets from the server and checks
 * ambient_song there, so that switch has to be flipped here, using the server calendar.
 *   day   -> ambient_song = false
 *   night -> ambient_song = true
 */
@EventBusSubscriber(modid = PlanetEscapeHandler.MODID)
public final class SMusicToggle {
    /** Also switch Spore's encounter (combat/boss) songs with day/night. false = they play any time. */
    private static final boolean TOGGLE_ENCOUNTER_SONGS = false;

    private static boolean warned;

    private SMusicToggle() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 != 0) {
            return;
        }
        boolean sporeTurn = SMusicGate.sporeTurn(false);
        set(SConfig.SERVER.ambient_song, sporeTurn);
        if (TOGGLE_ENCOUNTER_SONGS) {
            set(SConfig.SERVER.encounter_songs, sporeTurn);
        }
    }

    /** Only writes when the value actually changes, so it's a no-op almost every call. */
    static void set(ModConfigSpec.ConfigValue<Boolean> value, boolean wanted) {
        try {
            if (value.get() != wanted) {
                value.set(wanted);
            }
        } catch (RuntimeException e) {
            if (!warned) {
                warned = true;
                PlanetEscapeHandler.LOGGER.warn("Could not toggle a Spore config value", e);
            }
        }
    }
}

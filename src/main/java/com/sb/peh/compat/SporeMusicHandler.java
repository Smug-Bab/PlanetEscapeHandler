package com.sb.peh.compat;

import com.Harbinger.Spore.core.SConfig;
import com.sb.peh.PlanetEscapeHandler;
import net.dries007.tfc.util.calendar.Calendars;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Server side: Spore sends its ambient-song packets from the server and checks
 * ambient_song there, so that switch has to be flipped here, using the server calendar.
 *   day   -> ambient_song = false
 *   night -> ambient_song = true
 *
 * The client handler is a nested class registered with Dist.CLIENT, so the outer
 * class never touches client-only classes and is safe on a dedicated server.
 */
@EventBusSubscriber(modid = PlanetEscapeHandler.MODID)
public final class SporeMusicHandler {
    /** Also switch Spore's encounter (combat/boss) songs with day/night. false = they play any time. */
    private static final boolean TOGGLE_ENCOUNTER_SONGS = false;

    // Fractions of a TFC calendar day: 0.0 = midnight, 0.5 = noon.
    // 0.8 and 0.3 are what TFC's own "/time set night" and "/time set day" use.
    private static final float NIGHT_START = 0.8F;
    private static final float DAY_START = 0.3F;

    private static boolean warned;

    private SporeMusicHandler() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 != 0) {
            return;
        }
        boolean sporeTurn = sporeTurn(false);
        set(SConfig.SERVER.ambient_song, sporeTurn);
        if (TOGGLE_ENCOUNTER_SONGS) {
            set(SConfig.SERVER.encounter_songs, sporeTurn);
        }
    }

    @EventBusSubscriber(modid = PlanetEscapeHandler.MODID, value = Dist.CLIENT)
    public static final class Client {
        private Client() {
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                // Title screen etc: Spore's menu song on, vanilla menu music off. set() only writes on change.
                set(SConfig.SERVER.disable_vanilla, true);
                set(SConfig.SERVER.menu_song, true);
                return;
            }
            if (mc.level.getGameTime() % 20 != 0) {
                return;
            }
            set(SConfig.SERVER.disable_vanilla, sporeTurn(true));
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

    /** true = Spore's turn (night, or stage reached); false = TFC's turn. */
    public static boolean sporeTurn(boolean clientSide) {
        return isNight(clientSide) || isStageReached();
    }

    public static boolean isNight(boolean clientSide) {
        float fraction = Calendars.get(clientSide).getCalendarFractionOfDay();
        return fraction >= NIGHT_START || fraction < DAY_START;
    }

    /**
     * TODO: define the stage. Server side it can read anything; client side it must
     * be something the client knows about (an advancement, synced data, etc).
     */
    public static boolean isStageReached() {
        return false;
    }
}

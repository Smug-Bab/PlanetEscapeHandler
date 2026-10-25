package com.sb.peh.compat;

import net.dries007.tfc.util.calendar.Calendars;

/** Decides who owns the music, purely from the TFC calendar. */
public final class SMusicGate {
    // Fractions of a TFC calendar day: 0.0 = midnight, 0.5 = noon.
    // 0.8 and 0.3 are what TFC's own "/time set night" and "/time set day" use.
    private static final float NIGHT_START = 0.8F;
    private static final float DAY_START = 0.3F;

    private SMusicGate() {
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

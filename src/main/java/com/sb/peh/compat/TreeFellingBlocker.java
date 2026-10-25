package com.sb.peh.compat;

import ht.treechop.api.ChopEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * TreeChop asks "may I fell this tree?" through ChopEvent.BeforeFellEvent. Cancelling it
 * keeps the chopping mechanic (chops are still applied) but TreeChop breaks no other logs or leaves.
 *
 * Kept in its own class so TreeChop's classes are only loaded when TreeChop is installed;
 * call register() only behind a ModList check.
 */
public final class TreeFellingBlocker {
    private TreeFellingBlocker() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(TreeFellingBlocker::onBeforeFell);
    }

    private static void onBeforeFell(ChopEvent.BeforeFellEvent event) {
        event.setCanceled(true);
    }
}

package com.sb.peh;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;

@Mod(PlanetEscapeHandler.MODID)
public class PlanetEscapeHandler {
    public static final String MODID = "planetescapehandler";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PlanetEscapeHandler(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("PlanetEscapeHandler plugged in!");
    }
}

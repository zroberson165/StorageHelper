package com.storagehelper;

import com.storagehelper.chest.ChestDetector;
import com.storagehelper.chest.ChestSessions;
import com.storagehelper.config.StorageHelperConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Client entrypoint. Everything hangs off the chest screen; nothing runs on the server. */
public final class StorageHelperClient implements ClientModInitializer {
	public static final String MOD_ID = "storagehelper";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static StorageHelperConfig config;

	@Override
	public void onInitializeClient() {
		config = StorageHelperConfig.load();
		ChestDetector.init();
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) ->
				ChestSessions.onScreenInit(client, screen, config));
		LOGGER.info("[storagehelper] Storage Helper ready (sort mode: {}, {} click(s)/tick)",
				config.sortMode().id(), config.clicksPerTick());
	}

	public static StorageHelperConfig config() {
		return config;
	}
}

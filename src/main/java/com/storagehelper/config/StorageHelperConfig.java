package com.storagehelper.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.storagehelper.logic.SortMode;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persistent settings, stored as JSON in {@code config/storagehelper.json}.
 */
public final class StorageHelperConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("storagehelper");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String FILE_NAME = "storagehelper.json";

	/** Selected sort order; see {@link SortMode#id()}. */
	public String sortMode = SortMode.ALPHABETICAL.id();
	/** Inventory clicks sent per client tick while an operation runs (1-5). */
	public int clicksPerTick = 1;
	/** Slightly darken chest slots that do not match the search. */
	public boolean dimNonMatching = true;

	public static StorageHelperConfig load() {
		Path path = path();
		if (Files.isRegularFile(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				StorageHelperConfig cfg = GSON.fromJson(reader, StorageHelperConfig.class);
				if (cfg != null) {
					cfg.sanitize();
					return cfg;
				}
			} catch (Exception e) {
				LOGGER.warn("[storagehelper] Could not read {}, using defaults", path, e);
			}
		}
		StorageHelperConfig cfg = new StorageHelperConfig();
		cfg.save();
		return cfg;
	}

	public void save() {
		Path path = path();
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(this));
		} catch (IOException e) {
			LOGGER.warn("[storagehelper] Could not write {}", path, e);
		}
	}

	public SortMode sortMode() {
		return SortMode.fromId(sortMode, SortMode.ALPHABETICAL);
	}

	public void setSortMode(SortMode mode) {
		this.sortMode = mode.id();
		save();
	}

	public int clicksPerTick() {
		return Math.max(1, Math.min(5, clicksPerTick));
	}

	private void sanitize() {
		sortMode = sortMode().id();
		clicksPerTick = clicksPerTick();
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}
}

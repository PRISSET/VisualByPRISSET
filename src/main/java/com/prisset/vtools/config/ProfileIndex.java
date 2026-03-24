package com.prisset.vtools.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ProfileIndex {

    private static final Logger LOG = LoggerFactory.getLogger("prisset-vtools");
    private static final String FILE_NAME = "prisset-vtools-profiles.dat";

    private static final ProfileIndex INSTANCE = new ProfileIndex();

    private final Set<String> names = new LinkedHashSet<>();

    private ProfileIndex() {}

    public static ProfileIndex get() {
        return INSTANCE;
    }

    public boolean isTeammate(String name) {
        return names.contains(name.toLowerCase());
    }

    public void add(String name) {
        if (name == null || name.isBlank()) return;
        names.add(name.trim().toLowerCase());
        save();
    }

    public void remove(String name) {
        names.remove(name.toLowerCase());
        save();
    }

    public Set<String> all() {
        return Collections.unmodifiableSet(names);
    }

    public void load() {
        Path path = configPath();
        if (!Files.exists(path)) return;
        try {
            String content = Files.readString(path);
            JsonArray arr = JsonParser.parseString(content).getAsJsonArray();
            names.clear();
            arr.forEach(e -> names.add(e.getAsString().toLowerCase()));
        } catch (Exception e) {
            LOG.warn("Failed to load profiles", e);
        }
    }

    public void save() {
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            JsonArray arr = new JsonArray();
            names.forEach(arr::add);
            Files.writeString(path, arr.toString());
        } catch (IOException e) {
            LOG.error("Failed to save profiles", e);
        }
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }
}

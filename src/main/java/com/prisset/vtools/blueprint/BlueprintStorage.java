package com.prisset.vtools.blueprint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Saves and loads schematics to/from the config directory.
 * Files stored as .blueprint.json in prisset-vtools/blueprints/
 */
public final class BlueprintStorage {

    private static final Logger LOG = LoggerFactory.getLogger("prisset-vtools");
    private static final String DIR_NAME = "prisset-blueprints";
    private static final Gson GSON = new GsonBuilder().create();

    private BlueprintStorage() {}

    private static Path getDir() {
        Path dir = FabricLoader.getInstance().getConfigDir().resolve(DIR_NAME);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOG.error("Failed to create blueprints directory", e);
        }
        return dir;
    }

    /**
     * Save schematic to file. Filename derived from name field.
     */
    public static boolean save(SchematicData data) {
        String safeName = data.getName()
            .replaceAll("[^a-zA-Z0-9_\\-]", "_")
            .toLowerCase();
        if (safeName.isEmpty()) safeName = "unnamed";

        Path file = getDir().resolve(safeName + ".blueprint.json");

        // Avoid overwriting: append number if exists
        int counter = 1;
        while (Files.exists(file)) {
            file = getDir().resolve(safeName + "_" + counter + ".blueprint.json");
            counter++;
        }

        try {
            String json = GSON.toJson(data.toJson());
            Files.writeString(file, json);
            LOG.info("Saved blueprint: {} ({} blocks)", file.getFileName(), data.getNonAirCount());
            return true;
        } catch (IOException e) {
            LOG.error("Failed to save blueprint", e);
            return false;
        }
    }

    /**
     * Load schematic from file by name (without extension).
     */
    public static SchematicData load(String name) {
        Path dir = getDir();
        // Try exact match first
        Path file = dir.resolve(name + ".blueprint.json");
        if (!Files.exists(file)) {
            file = dir.resolve(name);
            if (!Files.exists(file)) return null;
        }

        try {
            String json = Files.readString(file);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            SchematicData data = SchematicData.fromJson(obj);
            LOG.info("Loaded blueprint: {} ({} blocks)", file.getFileName(), data.getNonAirCount());
            return data;
        } catch (Exception e) {
            LOG.error("Failed to load blueprint: {}", name, e);
            return null;
        }
    }

    /**
     * Delete a saved blueprint by name.
     */
    public static boolean delete(String name) {
        Path dir = getDir();
        Path file = dir.resolve(name + ".blueprint.json");
        if (!Files.exists(file)) {
            file = dir.resolve(name);
            if (!Files.exists(file)) return false;
        }
        try {
            Files.delete(file);
            LOG.info("Deleted blueprint: {}", name);
            return true;
        } catch (IOException e) {
            LOG.error("Failed to delete blueprint: {}", name, e);
            return false;
        }
    }

    /**
     * List all saved blueprint names (without extension).
     */
    public static List<String> listAll() {
        List<String> names = new ArrayList<>();
        Path dir = getDir();
        if (!Files.isDirectory(dir)) return names;

        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.toString().endsWith(".blueprint.json"))
                 .forEach(p -> {
                     String fn = p.getFileName().toString();
                     names.add(fn.replace(".blueprint.json", ""));
                 });
        } catch (IOException e) {
            LOG.error("Failed to list blueprints", e);
        }

        return names;
    }
}

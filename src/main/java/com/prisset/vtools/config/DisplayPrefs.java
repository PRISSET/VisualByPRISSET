package com.prisset.vtools.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DisplayPrefs {

    private static final Logger LOG = LoggerFactory.getLogger("prisset-vtools");
    private static final String FILE_NAME = "prisset-vtools.dat";

    private boolean active;
    private boolean debugOnly;
    private float vScale;
    private float hScale;
    private float fixedV;
    private int tintR;
    private int tintG;
    private int tintB;
    private int tintA;
    private boolean filterPlayers;
    private boolean filterMobs;
    private boolean filterDrops;
    private boolean stealth;

    private DisplayPrefs() {
        this.active = false;
        this.debugOnly = true;
        this.vScale = 1.0f;
        this.hScale = 1.0f;
        this.fixedV = -1.0f;
        this.tintR = 255;
        this.tintG = 255;
        this.tintB = 255;
        this.tintA = 128;
        this.filterPlayers = true;
        this.filterMobs = true;
        this.filterDrops = false;
        this.stealth = false;
    }

    public static DisplayPrefs defaults() {
        return new DisplayPrefs();
    }

    // --- Serialization with obfuscated keys ---

    private JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("a", active);
        obj.addProperty("d", debugOnly);
        obj.addProperty("vs", vScale);
        obj.addProperty("hs", hScale);
        obj.addProperty("fv", fixedV);
        obj.addProperty("r", tintR);
        obj.addProperty("g", tintG);
        obj.addProperty("b", tintB);
        obj.addProperty("o", tintA);
        obj.addProperty("fp", filterPlayers);
        obj.addProperty("fm", filterMobs);
        obj.addProperty("fd", filterDrops);
        obj.addProperty("st", stealth);
        return obj;
    }

    private static DisplayPrefs fromJson(JsonObject obj) {
        DisplayPrefs prefs = new DisplayPrefs();
        if (obj.has("a")) prefs.active = obj.get("a").getAsBoolean();
        if (obj.has("d")) prefs.debugOnly = obj.get("d").getAsBoolean();
        if (obj.has("vs")) prefs.vScale = obj.get("vs").getAsFloat();
        if (obj.has("hs")) prefs.hScale = obj.get("hs").getAsFloat();
        if (obj.has("fv")) prefs.fixedV = obj.get("fv").getAsFloat();
        if (obj.has("r")) prefs.tintR = obj.get("r").getAsInt();
        if (obj.has("g")) prefs.tintG = obj.get("g").getAsInt();
        if (obj.has("b")) prefs.tintB = obj.get("b").getAsInt();
        if (obj.has("o")) prefs.tintA = obj.get("o").getAsInt();
        if (obj.has("fp")) prefs.filterPlayers = obj.get("fp").getAsBoolean();
        if (obj.has("fm")) prefs.filterMobs = obj.get("fm").getAsBoolean();
        if (obj.has("fd")) prefs.filterDrops = obj.get("fd").getAsBoolean();
        if (obj.has("st")) prefs.stealth = obj.get("st").getAsBoolean();
        return prefs;
    }

    // --- File I/O ---

    private static Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    public static DisplayPrefs load() {
        Path path = getConfigPath();
        if (!Files.exists(path)) {
            DisplayPrefs prefs = defaults();
            prefs.save();
            return prefs;
        }
        try {
            String content = Files.readString(path);
            JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
            return fromJson(obj);
        } catch (Exception e) {
            LOG.warn("Failed to load config, using defaults", e);
            return defaults();
        }
    }

    public void save() {
        Path path = getConfigPath();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, toJson().toString());
        } catch (IOException e) {
            LOG.error("Failed to save config", e);
        }
    }

    // --- Getters ---

    public boolean isActive() { return active; }
    public boolean isDebugOnly() { return debugOnly; }
    public float getVScale() { return vScale; }
    public float getHScale() { return hScale; }
    public float getFixedV() { return fixedV; }
    public int getTintR() { return tintR; }
    public int getTintG() { return tintG; }
    public int getTintB() { return tintB; }
    public int getTintA() { return tintA; }
    public boolean isFilterPlayers() { return filterPlayers; }
    public boolean isFilterMobs() { return filterMobs; }
    public boolean isFilterDrops() { return filterDrops; }
    public boolean isStealth() { return stealth; }

    // --- Setters ---

    public void setActive(boolean val) { this.active = val; }
    public void setDebugOnly(boolean val) { this.debugOnly = val; }
    public void setVScale(float val) { this.vScale = clamp(val, 0.1f, 5.0f); }
    public void setHScale(float val) { this.hScale = clamp(val, 0.1f, 5.0f); }
    public void setFixedV(float val) { this.fixedV = val < 0 ? -1.0f : clamp(val, 0.1f, 10.0f); }
    public void setTintR(int val) { this.tintR = clamp(val, 0, 255); }
    public void setTintG(int val) { this.tintG = clamp(val, 0, 255); }
    public void setTintB(int val) { this.tintB = clamp(val, 0, 255); }
    public void setTintA(int val) { this.tintA = clamp(val, 0, 255); }
    public void setFilterPlayers(boolean val) { this.filterPlayers = val; }
    public void setFilterMobs(boolean val) { this.filterMobs = val; }
    public void setFilterDrops(boolean val) { this.filterDrops = val; }
    public void setStealth(boolean val) { this.stealth = val; }

    // --- Utility ---

    private static float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }

    private static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }
}

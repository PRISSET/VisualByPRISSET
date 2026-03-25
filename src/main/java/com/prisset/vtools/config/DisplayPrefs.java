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
    private boolean fastInteract;
    private boolean rgbMode;
    private float rgbSpeed;
    private boolean healthBars;
    private float zoomStrength;
    private boolean filterProjectiles;
    private boolean trailEnabled;
    private int trailLength;
    private int menuR;
    private int menuG;
    private int menuB;
    private boolean menuRgbMode;
    private float menuRgbSpeed;

    private boolean noBobbing;
    private boolean wTap;
    private boolean overlayEnabled;
    private boolean hitboxEnabled;
    private boolean espEnabled;
    private boolean tgEnabled;
    private String tgBotToken;
    private String tgChatId;
    private boolean afkGuard;
    private boolean autoFarm;
    private boolean autoEat;
    private boolean autoBuy;

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
        this.filterProjectiles = true;
        this.fastInteract = false;
        this.rgbMode = false;
        this.rgbSpeed = 1.0f;
        this.healthBars = true;
        this.zoomStrength = 4.0f;
        this.trailEnabled = false;
        this.trailLength = 15;
        this.menuR = 80;
        this.menuG = 80;
        this.menuB = 255;
        this.menuRgbMode = false;
        this.menuRgbSpeed = 1.0f;
        this.noBobbing = false;
        this.wTap = false;
        this.overlayEnabled = true;
        this.hitboxEnabled = true;
        this.espEnabled = false;
        this.tgEnabled = false;
        this.tgBotToken = "";
        this.tgChatId = "";
        this.afkGuard = false;
        this.autoFarm = false;
        this.autoEat = false;
        this.autoBuy = false;
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
        obj.addProperty("fj", filterProjectiles);
        obj.addProperty("fi", fastInteract);
        obj.addProperty("rb", rgbMode);
        obj.addProperty("rs", rgbSpeed);
        obj.addProperty("hb", healthBars);
        obj.addProperty("zs", zoomStrength);
        obj.addProperty("te", trailEnabled);
        obj.addProperty("tl", trailLength);
        obj.addProperty("mr", menuR);
        obj.addProperty("mg", menuG);
        obj.addProperty("mb", menuB);
        obj.addProperty("mrbm", menuRgbMode);
        obj.addProperty("mrs", menuRgbSpeed);
        obj.addProperty("nb", noBobbing);
        obj.addProperty("wt", wTap);
        obj.addProperty("oe", overlayEnabled);
        obj.addProperty("he", hitboxEnabled);
        obj.addProperty("ee", espEnabled);
        obj.addProperty("tge", tgEnabled);
        obj.addProperty("tgbt", tgBotToken);
        obj.addProperty("tgci", tgChatId);
        obj.addProperty("ag", afkGuard);
        obj.addProperty("af", autoFarm);
        obj.addProperty("ae", autoEat);
        obj.addProperty("ab", autoBuy);
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
        if (obj.has("fj")) prefs.filterProjectiles = obj.get("fj").getAsBoolean();
        if (obj.has("fi")) prefs.fastInteract = obj.get("fi").getAsBoolean();
        if (obj.has("rb")) prefs.rgbMode = obj.get("rb").getAsBoolean();
        if (obj.has("rs")) prefs.rgbSpeed = obj.get("rs").getAsFloat();
        if (obj.has("hb")) prefs.healthBars = obj.get("hb").getAsBoolean();
        if (obj.has("zs")) prefs.zoomStrength = obj.get("zs").getAsFloat();
        if (obj.has("te")) prefs.trailEnabled = obj.get("te").getAsBoolean();
        if (obj.has("tl")) prefs.trailLength = obj.get("tl").getAsInt();
        if (obj.has("mr")) prefs.menuR = obj.get("mr").getAsInt();
        if (obj.has("mg")) prefs.menuG = obj.get("mg").getAsInt();
        if (obj.has("mb")) prefs.menuB = obj.get("mb").getAsInt();
        if (obj.has("mrbm")) prefs.menuRgbMode = obj.get("mrbm").getAsBoolean();
        if (obj.has("mrs")) prefs.menuRgbSpeed = obj.get("mrs").getAsFloat();
        if (obj.has("nb")) prefs.noBobbing = obj.get("nb").getAsBoolean();
        if (obj.has("wt")) prefs.wTap = obj.get("wt").getAsBoolean();
        if (obj.has("oe")) prefs.overlayEnabled = obj.get("oe").getAsBoolean();
        if (obj.has("he")) prefs.hitboxEnabled = obj.get("he").getAsBoolean();
        if (obj.has("ee")) prefs.espEnabled = obj.get("ee").getAsBoolean();
        if (obj.has("tge")) prefs.tgEnabled = obj.get("tge").getAsBoolean();
        if (obj.has("tgbt")) prefs.tgBotToken = obj.get("tgbt").getAsString();
        if (obj.has("tgci")) prefs.tgChatId = obj.get("tgci").getAsString();
        if (obj.has("ag")) prefs.afkGuard = obj.get("ag").getAsBoolean();
        if (obj.has("af")) prefs.autoFarm = obj.get("af").getAsBoolean();
        if (obj.has("ae")) prefs.autoEat = obj.get("ae").getAsBoolean();
        if (obj.has("ab")) prefs.autoBuy = obj.get("ab").getAsBoolean();
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
    public boolean isFilterProjectiles() { return filterProjectiles; }
    public boolean isFastInteract() { return true; }
    public boolean isRgbMode() { return rgbMode; }
    public float getRgbSpeed() { return rgbSpeed; }
    public boolean isHealthBars() { return healthBars; }
    public float getZoomStrength() { return zoomStrength; }
    public boolean isTrailEnabled() { return trailEnabled; }
    public int getTrailLength() { return trailLength; }
    public int getMenuR() { return menuR; }
    public int getMenuG() { return menuG; }
    public int getMenuB() { return menuB; }
    public boolean isMenuRgbMode() { return menuRgbMode; }
    public float getMenuRgbSpeed() { return menuRgbSpeed; }
    public boolean isNoBobbing() { return noBobbing; }
    public boolean isWTap() { return wTap; }
    public boolean isOverlayEnabled() { return overlayEnabled; }
    public boolean isHitboxEnabled() { return hitboxEnabled; }
    public boolean isEspEnabled() { return espEnabled; }
    public boolean isTgEnabled() { return tgEnabled; }
    public String getTgBotToken() { return tgBotToken; }
    public String getTgChatId() { return tgChatId; }
    public boolean isAfkGuard() { return afkGuard; }
    public boolean isAutoFarm() { return autoFarm; }
    public boolean isAutoEat() { return autoEat; }
    public boolean isAutoBuy() { return autoBuy; }
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
    public void setFilterProjectiles(boolean val) { this.filterProjectiles = val; }
    public void setFastInteract(boolean val) { this.fastInteract = val; }
    public void setRgbMode(boolean val) { this.rgbMode = val; }
    public void setRgbSpeed(float val) { this.rgbSpeed = clamp(val, 0.1f, 5.0f); }
    public void setHealthBars(boolean val) { this.healthBars = val; }
    public void setZoomStrength(float val) { this.zoomStrength = clamp(val, 1.5f, 10.0f); }
    public void setTrailEnabled(boolean val) { this.trailEnabled = val; }
    public void setTrailLength(int val) { this.trailLength = clamp(val, 5, 40); }
    public void setMenuR(int val) { this.menuR = clamp(val, 0, 255); }
    public void setMenuG(int val) { this.menuG = clamp(val, 0, 255); }
    public void setMenuB(int val) { this.menuB = clamp(val, 0, 255); }
    public void setMenuRgbMode(boolean val) { this.menuRgbMode = val; }
    public void setMenuRgbSpeed(float val) { this.menuRgbSpeed = clamp(val, 0.1f, 5.0f); }
    public void setNoBobbing(boolean val) { this.noBobbing = val; }
    public void setWTap(boolean val) { this.wTap = val; }
    public void setOverlayEnabled(boolean val) { this.overlayEnabled = val; }
    public void setHitboxEnabled(boolean val) { this.hitboxEnabled = val; }
    public void setEspEnabled(boolean val) { this.espEnabled = val; }
    public void setTgEnabled(boolean val) { this.tgEnabled = val; }
    public void setTgBotToken(String val) { this.tgBotToken = val != null ? val : ""; }
    public void setTgChatId(String val) { this.tgChatId = val != null ? val : ""; }
    public void setAfkGuard(boolean val) { this.afkGuard = val; }
    public void setAutoFarm(boolean val) { this.autoFarm = val; }
    public void setAutoEat(boolean val) { this.autoEat = val; }
    public void setAutoBuy(boolean val) { this.autoBuy = val; }
    // --- Utility ---

    private static float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }

    private static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }
}

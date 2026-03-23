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

    private boolean surveyEnabled;
    private int surveyRadius;
    private boolean oreDiamond;
    private boolean oreGold;
    private boolean oreIron;
    private boolean oreCopper;
    private boolean oreRedstone;
    private boolean oreLapis;
    private boolean oreEmerald;
    private boolean oreCoal;
    private boolean surveyAutoLeave;
    private int cntDiamond;
    private int cntGold;
    private int cntIron;
    private int cntCopper;
    private int cntRedstone;
    private int cntLapis;
    private int cntEmerald;
    private int cntCoal;

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
        this.surveyEnabled = false;
        this.surveyRadius = 32;
        this.oreDiamond = true;
        this.oreGold = false;
        this.oreIron = false;
        this.oreCopper = false;
        this.oreRedstone = false;
        this.oreLapis = false;
        this.oreEmerald = false;
        this.oreCoal = false;
        this.surveyAutoLeave = false;
        this.cntDiamond = 0;
        this.cntGold = 0;
        this.cntIron = 0;
        this.cntCopper = 0;
        this.cntRedstone = 0;
        this.cntLapis = 0;
        this.cntEmerald = 0;
        this.cntCoal = 0;
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
        obj.addProperty("se", surveyEnabled);
        obj.addProperty("sr", surveyRadius);
        obj.addProperty("od", oreDiamond);
        obj.addProperty("og", oreGold);
        obj.addProperty("oi", oreIron);
        obj.addProperty("oc", oreCopper);
        obj.addProperty("or", oreRedstone);
        obj.addProperty("ol", oreLapis);
        obj.addProperty("oe", oreEmerald);
        obj.addProperty("ok", oreCoal);
        obj.addProperty("sal", surveyAutoLeave);
        obj.addProperty("cd", cntDiamond);
        obj.addProperty("cg", cntGold);
        obj.addProperty("ci", cntIron);
        obj.addProperty("cc", cntCopper);
        obj.addProperty("cr", cntRedstone);
        obj.addProperty("cl", cntLapis);
        obj.addProperty("ce", cntEmerald);
        obj.addProperty("ck", cntCoal);
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
        if (obj.has("se")) prefs.surveyEnabled = obj.get("se").getAsBoolean();
        if (obj.has("sr")) prefs.surveyRadius = obj.get("sr").getAsInt();
        if (obj.has("od")) prefs.oreDiamond = obj.get("od").getAsBoolean();
        if (obj.has("og")) prefs.oreGold = obj.get("og").getAsBoolean();
        if (obj.has("oi")) prefs.oreIron = obj.get("oi").getAsBoolean();
        if (obj.has("oc")) prefs.oreCopper = obj.get("oc").getAsBoolean();
        if (obj.has("or")) prefs.oreRedstone = obj.get("or").getAsBoolean();
        if (obj.has("ol")) prefs.oreLapis = obj.get("ol").getAsBoolean();
        if (obj.has("oe")) prefs.oreEmerald = obj.get("oe").getAsBoolean();
        if (obj.has("ok")) prefs.oreCoal = obj.get("ok").getAsBoolean();
        if (obj.has("sal")) prefs.surveyAutoLeave = obj.get("sal").getAsBoolean();
        if (obj.has("cd")) prefs.cntDiamond = obj.get("cd").getAsInt();
        if (obj.has("cg")) prefs.cntGold = obj.get("cg").getAsInt();
        if (obj.has("ci")) prefs.cntIron = obj.get("ci").getAsInt();
        if (obj.has("cc")) prefs.cntCopper = obj.get("cc").getAsInt();
        if (obj.has("cr")) prefs.cntRedstone = obj.get("cr").getAsInt();
        if (obj.has("cl")) prefs.cntLapis = obj.get("cl").getAsInt();
        if (obj.has("ce")) prefs.cntEmerald = obj.get("ce").getAsInt();
        if (obj.has("ck")) prefs.cntCoal = obj.get("ck").getAsInt();
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
    public boolean isSurveyEnabled() { return surveyEnabled; }
    public int getSurveyRadius() { return surveyRadius; }
    public boolean isOreDiamond() { return oreDiamond; }
    public boolean isOreGold() { return oreGold; }
    public boolean isOreIron() { return oreIron; }
    public boolean isOreCopper() { return oreCopper; }
    public boolean isOreRedstone() { return oreRedstone; }
    public boolean isOreLapis() { return oreLapis; }
    public boolean isOreEmerald() { return oreEmerald; }
    public boolean isOreCoal() { return oreCoal; }
    public boolean isSurveyAutoLeave() { return surveyAutoLeave; }
    public int getCntDiamond() { return cntDiamond; }
    public int getCntGold() { return cntGold; }
    public int getCntIron() { return cntIron; }
    public int getCntCopper() { return cntCopper; }
    public int getCntRedstone() { return cntRedstone; }
    public int getCntLapis() { return cntLapis; }
    public int getCntEmerald() { return cntEmerald; }
    public int getCntCoal() { return cntCoal; }
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
    public void setSurveyEnabled(boolean val) { this.surveyEnabled = val; }
    public void setSurveyRadius(int val) { this.surveyRadius = clamp(val, 16, 64); }
    public void setOreDiamond(boolean val) { this.oreDiamond = val; }
    public void setOreGold(boolean val) { this.oreGold = val; }
    public void setOreIron(boolean val) { this.oreIron = val; }
    public void setOreCopper(boolean val) { this.oreCopper = val; }
    public void setOreRedstone(boolean val) { this.oreRedstone = val; }
    public void setOreLapis(boolean val) { this.oreLapis = val; }
    public void setOreEmerald(boolean val) { this.oreEmerald = val; }
    public void setOreCoal(boolean val) { this.oreCoal = val; }
    public void setSurveyAutoLeave(boolean val) { this.surveyAutoLeave = val; }
    public void setCntDiamond(int val) { this.cntDiamond = clamp(val, 0, 999); }
    public void setCntGold(int val) { this.cntGold = clamp(val, 0, 999); }
    public void setCntIron(int val) { this.cntIron = clamp(val, 0, 999); }
    public void setCntCopper(int val) { this.cntCopper = clamp(val, 0, 999); }
    public void setCntRedstone(int val) { this.cntRedstone = clamp(val, 0, 999); }
    public void setCntLapis(int val) { this.cntLapis = clamp(val, 0, 999); }
    public void setCntEmerald(int val) { this.cntEmerald = clamp(val, 0, 999); }
    public void setCntCoal(int val) { this.cntCoal = clamp(val, 0, 999); }
    // --- Utility ---

    private static float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }

    private static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }
}

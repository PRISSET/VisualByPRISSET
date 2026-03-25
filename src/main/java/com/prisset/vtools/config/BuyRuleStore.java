package com.prisset.vtools.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BuyRuleStore {

    private static final Logger LOG = LoggerFactory.getLogger("prisset-vtools");
    private static final String FILE_NAME = "prisset-vtools-buyrules.dat";
    private static final BuyRuleStore INSTANCE = new BuyRuleStore();

    private final List<BuyRule> rules = new ArrayList<>();

    private BuyRuleStore() {}

    public static BuyRuleStore get() {
        return INSTANCE;
    }

    public static class BuyRule {
        private String displayName;
        private String itemId;
        private int maxPricePerUnit;
        private int quantity;
        private int bought;
        private boolean enabled;

        public BuyRule(String displayName, String itemId, int maxPricePerUnit, int quantity) {
            this.displayName = displayName;
            this.itemId = itemId;
            this.maxPricePerUnit = maxPricePerUnit;
            this.quantity = quantity;
            this.bought = 0;
            this.enabled = true;
        }

        public String getDisplayName() { return displayName; }
        public String getItemId() { return itemId; }
        public int getMaxPricePerUnit() { return maxPricePerUnit; }
        public int getQuantity() { return quantity; }
        public int getBought() { return bought; }
        public boolean isEnabled() { return enabled; }
        public int remaining() { return Math.max(0, quantity - bought); }
        public boolean isFulfilled() { return bought >= quantity; }

        public void setEnabled(boolean v) { this.enabled = v; }
        public void setMaxPricePerUnit(int v) { this.maxPricePerUnit = v; }
        public void setQuantity(int v) { this.quantity = v; }
        public void addBought(int count) { this.bought += count; }
        public void resetBought() { this.bought = 0; }

        JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("dn", displayName);
            obj.addProperty("id", itemId);
            obj.addProperty("mp", maxPricePerUnit);
            obj.addProperty("q", quantity);
            obj.addProperty("b", bought);
            obj.addProperty("e", enabled);
            return obj;
        }

        static BuyRule fromJson(JsonObject obj) {
            String dn = obj.has("dn") ? obj.get("dn").getAsString() : "";
            String id = obj.has("id") ? obj.get("id").getAsString() : "";
            int mp = obj.has("mp") ? obj.get("mp").getAsInt() : 0;
            int q = obj.has("q") ? obj.get("q").getAsInt() : 1;
            BuyRule rule = new BuyRule(dn, id, mp, q);
            if (obj.has("b")) rule.bought = obj.get("b").getAsInt();
            if (obj.has("e")) rule.enabled = obj.get("e").getAsBoolean();
            return rule;
        }
    }

    public List<BuyRule> all() {
        return Collections.unmodifiableList(rules);
    }

    public List<BuyRule> activeRules() {
        List<BuyRule> active = new ArrayList<>();
        for (BuyRule r : rules) {
            if (r.isEnabled() && !r.isFulfilled()) active.add(r);
        }
        return active;
    }

    public void add(BuyRule rule) {
        rules.add(rule);
        save();
    }

    public void remove(int index) {
        if (index >= 0 && index < rules.size()) {
            rules.remove(index);
            save();
        }
    }

    public void remove(BuyRule rule) {
        rules.remove(rule);
        save();
    }

    public void load() {
        Path path = configPath();
        if (!Files.exists(path)) return;
        try {
            String content = Files.readString(path);
            JsonArray arr = JsonParser.parseString(content).getAsJsonArray();
            rules.clear();
            arr.forEach(e -> rules.add(BuyRule.fromJson(e.getAsJsonObject())));
        } catch (Exception e) {
            LOG.warn("Failed to load buy rules", e);
        }
    }

    public void save() {
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            JsonArray arr = new JsonArray();
            rules.forEach(r -> arr.add(r.toJson()));
            Files.writeString(path, arr.toString());
        } catch (IOException e) {
            LOG.error("Failed to save buy rules", e);
        }
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }
}

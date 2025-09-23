package com.example.fishingFrenzy;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Biome;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerFishEvent.State;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.Color;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.ArrayList;

public class FishingFrenzyListener implements Listener {
    private final FishingFrenzyManager manager;
    private final Random random = new Random();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Plugin plugin;
    private final NamespacedKey luckyRodKey;
    private FileConfiguration config;
    private Set<String> allowedWorlds;
    private Set<String> allowedBiomes; // stored as lowercase tokens from config
    private Set<String> allowedTimes;

    public FishingFrenzyListener(FishingFrenzyManager manager) {
        this.manager = manager;
        this.plugin = manager.getPlugin();
        this.config = plugin.getConfig();
        this.luckyRodKey = new NamespacedKey(plugin, "lucky_rod");
        allowedWorlds = new HashSet<>(config.getStringList("frenzy.allowed-worlds"));
        allowedBiomes = normalizeToLower(config.getStringList("frenzy.allowed-biomes"));
        allowedTimes = new HashSet<>(config.getStringList("frenzy.allowed-times"));
    }

    public void reloadConfig(FileConfiguration config) {
        this.config = config;
        allowedWorlds = new HashSet<>(config.getStringList("frenzy.allowed-worlds"));
        allowedBiomes = normalizeToLower(config.getStringList("frenzy.allowed-biomes"));
        allowedTimes = new HashSet<>(config.getStringList("frenzy.allowed-times"));
    }

    private Set<String> normalizeToLower(List<String> input) {
        Set<String> out = new HashSet<>();
        if (input != null) {
            for (String s : input) if (s != null) out.add(s.trim().toLowerCase(Locale.ROOT));
        }
        return out;
    }

    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        if (event.getState() != State.CAUGHT_FISH) {
            manager.resetStreak(player);
            return;
        }
        // Check allowed world
        if (!allowedWorlds.isEmpty() && !allowedWorlds.contains(player.getWorld().getName())) {
            if (manager.isDebug()) plugin.getLogger().fine("Skip: world not allowed");
            return;
        }
        // Check allowed biome (supports "PLAINS", "minecraft:plains", or just "plains" in config)
        Biome biome = player.getLocation().getBlock().getBiome();
        if (!allowedBiomes.isEmpty() && !isBiomeAllowed(biome)) {
            Key k = biome.key();
            if (manager.isDebug()) plugin.getLogger().fine("Skip: biome not allowed: " + k.namespace() + ":" + k.value());
            return;
        }
        // Check allowed time
        if (!allowedTimes.isEmpty() && !isAllowedTime(player.getWorld().getTime())) {
            if (manager.isDebug()) plugin.getLogger().fine("Skip: time not allowed");
            return;
        }
        // Cooldown checks (do not block catches during an active Frenzy)
        if (!manager.isFrenzyActive()) {
            if (manager.isGlobalOnCooldown()) {
                player.sendMessage("§cFishing Frenzy is on global cooldown.");
                if (manager.isDebug()) plugin.getLogger().fine("Blocked by global cooldown");
                return;
            }
            if (manager.isPlayerOnCooldown(player)) {
                player.sendMessage("§cYou're on fishing cooldown.");
                if (manager.isDebug()) plugin.getLogger().fine("Blocked by player cooldown");
                return;
            }
        }
        // Increment frenzy meter (only matters outside active Frenzy)
        manager.incrementFrenzyMeter(player);
        // Streak logic
        manager.incrementStreak(player);
        int streak = manager.getStreak(player);
        double luckMultiplier = manager.getLuckMultiplier(player);
        // ActionBar streak feedback
        manager.sendActionBar(player, "<green>Streak: <yellow>" + streak + " <gray>(Luck x" + String.format("%.2f", luckMultiplier) + ")");
        // Loot logic
        boolean gotSpicy = false;
        if (manager.isFrenzyActive()) {
            ItemStack loot = null;
            if (manager.shouldPity(player)) {
                loot = getRandomSpicyLootFromConfig();
                gotSpicy = true;
                player.sendMessage("§6Frenzy Pity! §bYou are guaranteed something spicy!");
                if (manager.isDebug()) plugin.getLogger().info("Pity triggered: giving spicy loot");
            } else {
                double baseChance = 0.20 * manager.getLootMultiplier() * luckMultiplier;
                // Detect Lucky Rod (by PDC or display name fallback)
                boolean hasLuckyRod = false;
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType() == Material.FISHING_ROD && hand.hasItemMeta()) {
                    ItemMeta meta = hand.getItemMeta();
                    PersistentDataContainer pdc = meta.getPersistentDataContainer();
                    hasLuckyRod = pdc.has(luckyRodKey, PersistentDataType.BYTE);
                    if (!hasLuckyRod) {
                        // Use legacy String display name for maximum compatibility
                        if (meta.hasDisplayName()) {
                            String dn = meta.getDisplayName();
                            hasLuckyRod = dn != null && dn.contains("Lucky Rod");
                        }
                    }
                }
                if (hasLuckyRod) baseChance *= 2.0; // Double spicy chance with Lucky Rod
                double roll = random.nextDouble();
                if (manager.isDebug()) plugin.getLogger().info(String.format("Frenzy roll=%.4f baseChance=%.4f luck=%.2f streak=%d luckyRod=%s", roll, baseChance, luckMultiplier, streak, hasLuckyRod));
                if (roll < baseChance) {
                    loot = getRandomLootFromConfig();
                    gotSpicy = isSpicyLoot(loot);
                }
            }
            if (loot != null) {
                player.getWorld().dropItemNaturally(player.getLocation(), loot);
                player.sendMessage("§bFishing Frenzy! §aYou caught something special!");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0,1,0), 30, 0.5, 0.5, 0.5, 0.1);
                if (manager.isDebug()) plugin.getLogger().info("Dropped loot: " + loot.getType());
            } else if (manager.isDebug()) {
                plugin.getLogger().fine("No loot this catch.");
            }
            manager.incrementPity(player, gotSpicy);
        }
        // Set per-player cooldown only when not in Frenzy
        if (!manager.isFrenzyActive()) {
            manager.setPlayerCooldown(player);
        }
    }

    private boolean isBiomeAllowed(Biome biome) {
        if (allowedBiomes.isEmpty()) return true;
        Key k = biome.key();
        String namespaced = (k.namespace() + ":" + k.value()).toLowerCase(Locale.ROOT);
        String keyOnly = k.value().toLowerCase(Locale.ROOT);
        return allowedBiomes.contains(namespaced) || allowedBiomes.contains(keyOnly);
    }

    private boolean isAllowedTime(long worldTime) {
        // Minecraft day: 0-23999, 0=6am, 6000=noon, 12000=6pm, 18000=midnight
        for (String t : allowedTimes) {
            if (t.equalsIgnoreCase("DAY") && worldTime >= 0 && worldTime < 12000) return true;
            if (t.equalsIgnoreCase("NIGHT") && worldTime >= 12000 && worldTime < 24000) return true;
        }
        return false;
    }

    private ItemStack getRandomSpicyLootFromConfig() {
        List<?> lootTable = config.getList("frenzy.loot-table");
        if (lootTable == null || lootTable.isEmpty()) {
            return new ItemStack(Material.COD, 1);
        }
        // Filter spicy loot
        List<java.util.Map<?, ?>> spicyLoots = new java.util.ArrayList<>();
        for (Object entry : lootTable) {
            if (entry instanceof java.util.Map) {
                java.util.Map<?, ?> map = (java.util.Map<?, ?>) entry;
                if (Boolean.TRUE.equals(map.get("spicy"))) {
                    spicyLoots.add(map);
                }
            }
        }
        if (spicyLoots.isEmpty()) return new ItemStack(Material.COD, 1);
        java.util.Map<?, ?> map = spicyLoots.get(random.nextInt(spicyLoots.size()));
        return buildItemFromLootMap(map);
    }

    private boolean isSpicyLoot(ItemStack item) {
        // Check by material for now (matches spicy items in config)
        Material mat = item.getType();
        return mat == Material.ELYTRA || mat == Material.NETHERITE_SCRAP || mat == Material.TRIDENT ||
               mat == Material.ENCHANTED_GOLDEN_APPLE || mat == Material.TOTEM_OF_UNDYING || mat == Material.HEART_OF_THE_SEA;
    }

    private ItemStack getRandomLootFromConfig() {
        List<?> lootTable = config.getList("frenzy.loot-table");
        if (lootTable == null || lootTable.isEmpty()) {
            return new ItemStack(Material.COD, 1);
        }
        // Simple weighted random selection
        int totalWeight = 0;
        for (Object entry : lootTable) {
            if (entry instanceof java.util.Map) {
                Object weightObj = ((java.util.Map<?, ?>) entry).get("weight");
                int weight = 1;
                if (weightObj instanceof Number) {
                    weight = ((Number) weightObj).intValue();
                }
                totalWeight += weight;
            }
        }
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (Object entry : lootTable) {
            if (entry instanceof java.util.Map) {
                java.util.Map<?, ?> map = (java.util.Map<?, ?>) entry;
                Object weightObj = map.get("weight");
                int weight = 1;
                if (weightObj instanceof Number) {
                    weight = ((Number) weightObj).intValue();
                }
                cumulative += weight;
                if (roll < cumulative) {
                    return buildItemFromLootMap(map);
                }
            }
        }
        return new ItemStack(Material.COD, 1);
    }

    private ItemStack buildItemFromLootMap(java.util.Map<?, ?> map) {
        String type = "COD";
        Object typeObj = map.get("type");
        if (typeObj instanceof String) {
            type = (String) typeObj;
        }
        int amount = 1;
        if (map.containsKey("amount")) {
            String amtStr = map.get("amount").toString();
            if (amtStr.contains("-")) {
                String[] parts = amtStr.split("-");
                try {
                    int min = Integer.parseInt(parts[0].trim());
                    int max = Integer.parseInt(parts[1].trim());
                    if (max < min) { int tmp = min; min = max; max = tmp; }
                    amount = min + random.nextInt(max - min + 1);
                } catch (Exception ignored) {
                    // fallback: try plain int parse
                    try { amount = Integer.parseInt(amtStr.trim()); } catch (NumberFormatException ignored2) {}
                }
            } else {
                try { amount = Integer.parseInt(amtStr.trim()); } catch (NumberFormatException ignored) {}
            }
        }
        Material mat = Material.matchMaterial(type);
        if (mat == null) mat = Material.COD;
        ItemStack item = new ItemStack(mat, amount);
        // Handle enchants for books and gear (supports name:level or objects {name, level})
        if (map.containsKey("enchants")) {
            Object enchObj = map.get("enchants");
            if (enchObj instanceof List) {
                List<?> enchList = (List<?>) enchObj;
                if (!enchList.isEmpty()) {
                    if (mat == Material.ENCHANTED_BOOK) {
                        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) item.getItemMeta();
                        for (Object ench : enchList) {
                            String name = null;
                            int level = -1; // -1 = use default random for books
                            if (ench instanceof Map) {
                                Object n = ((Map<?, ?>) ench).get("name");
                                Object l = ((Map<?, ?>) ench).get("level");
                                if (n != null) name = n.toString();
                                if (l instanceof Number) level = ((Number) l).intValue();
                                else if (l != null) {
                                    try { level = Integer.parseInt(l.toString()); } catch (NumberFormatException ignored) {}
                                }
                            } else if (ench instanceof String) {
                                String s = (String) ench;
                                if (s.contains(":")) {
                                    String[] parts = s.split(":", 2);
                                    name = parts[0];
                                    try { level = Integer.parseInt(parts[1]); } catch (NumberFormatException ignored) {}
                                } else {
                                    name = s;
                                }
                            }
                            if (name == null) continue;
                            Enchantment enchant = resolveEnchantment(name);
                            if (enchant != null && meta != null) {
                                int useLevel = level > 0 ? level : (1 + random.nextInt(2)); // fallback 1-2
                                useLevel = Math.max(1, Math.min(useLevel, enchant.getMaxLevel()));
                                meta.addStoredEnchant(enchant, useLevel, true);
                            }
                        }
                        item.setItemMeta(meta);
                    } else {
                        ItemMeta meta = item.getItemMeta();
                        for (Object ench : enchList) {
                            String name = null;
                            int level = -1; // -1 means use fallback 3-5 like before
                            if (ench instanceof Map) {
                                Object n = ((Map<?, ?>) ench).get("name");
                                Object l = ((Map<?, ?>) ench).get("level");
                                if (n != null) name = n.toString();
                                if (l instanceof Number) level = ((Number) l).intValue();
                                else if (l != null) {
                                    try { level = Integer.parseInt(l.toString()); } catch (NumberFormatException ignored) {}
                                }
                            } else if (ench instanceof String) {
                                String s = (String) ench;
                                if (s.contains(":")) {
                                    String[] parts = s.split(":", 2);
                                    name = parts[0];
                                    try { level = Integer.parseInt(parts[1]); } catch (NumberFormatException ignored) {}
                                } else {
                                    name = s;
                                }
                            }
                            if (name == null) continue;
                            Enchantment enchant = resolveEnchantment(name);
                            if (enchant != null && meta != null) {
                                int useLevel = level > 0 ? level : (3 + random.nextInt(3)); // fallback 3-5
                                useLevel = Math.max(1, Math.min(useLevel, enchant.getMaxLevel()));
                                meta.addEnchant(enchant, useLevel, true);
                            }
                        }
                        item.setItemMeta(meta);
                    }
                }
            }
        }
        // Handle custom name via legacy string (avoid Adventure ItemMeta API to prevent shading mismatch)
        if (map.containsKey("name")) {
            ItemMeta meta = item.getItemMeta();
            String legacy = LegacyComponentSerializer.legacySection()
                    .serialize(miniMessage.deserialize(map.get("name").toString()))
                    .replace("§r", "");
            meta.setDisplayName(legacy);
            item.setItemMeta(meta);
        }
        // Handle custom lore via legacy string list
        if (map.containsKey("lore")) {
            Object loreObj = map.get("lore");
            if (loreObj instanceof List) {
                ItemMeta meta = item.getItemMeta();
                java.util.List<String> lore = new java.util.ArrayList<>();
                for (Object lineObj : (List<?>) loreObj) {
                    if (lineObj != null) {
                        String legacy = LegacyComponentSerializer.legacySection()
                                .serialize(miniMessage.deserialize(lineObj.toString()))
                                .replace("§r", "");
                        lore.add(legacy);
                    }
                }
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
        }
        // Apply potion meta if configured
        if (map.containsKey("potion")) {
            Object potionObj = map.get("potion");
            if (potionObj instanceof Map) {
                ItemMeta meta = item.getItemMeta();
                if (meta instanceof PotionMeta) {
                    PotionMeta pMeta = (PotionMeta) meta;
                    Map<?, ?> potionMap = (Map<?, ?>) potionObj;
                    // base potion type (Paper API); supports LONG_/STRONG_ mapping from flags
                    Object baseObj = potionMap.get("base");
                    if (baseObj instanceof Map) {
                        Map<?, ?> base = (Map<?, ?>) baseObj;
                        Object t = base.get("type");
                        if (t != null) {
                            try {
                                PotionType pType = PotionType.valueOf(t.toString().toUpperCase(Locale.ROOT));
                                boolean extended = Boolean.TRUE.equals(base.get("extended"));
                                boolean upgraded = Boolean.TRUE.equals(base.get("upgraded"));
                                PotionType apply = pType;
                                if (upgraded) {
                                    try { apply = PotionType.valueOf("STRONG_" + pType.name()); } catch (IllegalArgumentException ignored) {}
                                } else if (extended) {
                                    try { apply = PotionType.valueOf("LONG_" + pType.name()); } catch (IllegalArgumentException ignored) {}
                                }
                                pMeta.setBasePotionType(apply);
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                    // color
                    Object colorObj = potionMap.get("color");
                    Color parsedColor = parseColor(colorObj);
                    if (parsedColor != null) {
                        pMeta.setColor(parsedColor);
                    }
                    // custom effects
                    Object effectsObj = potionMap.get("effects");
                    if (effectsObj instanceof List) {
                        for (Object eff : (List<?>) effectsObj) {
                            PotionEffect effect = parsePotionEffect(eff);
                            if (effect != null) {
                                pMeta.addCustomEffect(effect, true);
                            }
                        }
                    }
                    item.setItemMeta(pMeta);
                }
            }
        }
        // Mark Lucky Rod via PDC for reliable detection
        if (map.containsKey("lucky-rod") && Boolean.TRUE.equals(map.get("lucky-rod"))) {
            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(luckyRodKey, PersistentDataType.BYTE, (byte)1);
            item.setItemMeta(meta);
        }
        return item;
    }

    // Prefer NamespacedKey lookup; fallback to legacy name method (suppressed deprecation)
    @SuppressWarnings("deprecation")
    private Enchantment resolveEnchantment(String name) {
        if (name == null) return null;
        String s = name.trim();
        try {
            if (s.contains(":")) {
                String[] parts = s.split(":", 2);
                NamespacedKey key = new NamespacedKey(parts[0], parts[1]);
                Enchantment e = Enchantment.getByKey(key);
                if (e != null) return e;
            } else {
                Enchantment e = Enchantment.getByKey(NamespacedKey.minecraft(s.toLowerCase(Locale.ROOT)));
                if (e != null) return e;
            }
        } catch (Exception ignored) {}
        return Enchantment.getByName(s.toUpperCase(Locale.ROOT));
    }

    // Parse color from various formats: "#RRGGBB", "R,G,B", or integer
    private Color parseColor(Object colorObj) {
        if (colorObj == null) return null;
        try {
            if (colorObj instanceof String) {
                String s = ((String) colorObj).trim();
                if (s.startsWith("#") && (s.length() == 7 || s.length() == 4)) {
                    // support #RGB and #RRGGBB
                    if (s.length() == 4) {
                        char r = s.charAt(1), g = s.charAt(2), b = s.charAt(3);
                        s = "#" + r + r + g + g + b + b;
                    }
                    int rgb = Integer.parseInt(s.substring(1), 16);
                    return Color.fromRGB(rgb);
                }
                if (s.contains(",")) {
                    String[] parts = s.split(",");
                    int r = Integer.parseInt(parts[0].trim());
                    int g = Integer.parseInt(parts[1].trim());
                    int b = Integer.parseInt(parts[2].trim());
                    return Color.fromRGB(r, g, b);
                }
                // try named constants (e.g., RED) using reflection to org.bukkit.Color
                try {
                    java.lang.reflect.Field f = Color.class.getField(s.toUpperCase(Locale.ROOT));
                    Object v = f.get(null);
                    if (v instanceof Color) return (Color) v;
                } catch (NoSuchFieldException ignored) {}
            } else if (colorObj instanceof Number) {
                int rgb = ((Number) colorObj).intValue();
                return Color.fromRGB(rgb);
            } else if (colorObj instanceof List) {
                List<?> list = (List<?>) colorObj;
                if (list.size() >= 3) {
                    int r = Integer.parseInt(list.get(0).toString());
                    int g = Integer.parseInt(list.get(1).toString());
                    int b = Integer.parseInt(list.get(2).toString());
                    return Color.fromRGB(r, g, b);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    // Parse a potion effect from map like {type: SPEED, duration: 30, level: 2, ambient: false, particles: true, icon: true}
    // duration is in seconds unless duration-ticks is provided or ticks:true is set
    private PotionEffect parsePotionEffect(Object obj) {
        if (!(obj instanceof Map)) return null;
        Map<?, ?> m = (Map<?, ?>) obj;
        Object typeObj = m.get("type");
        if (typeObj == null) return null;
        PotionEffectType peType = PotionEffectType.getByName(typeObj.toString().toUpperCase(Locale.ROOT));
        if (peType == null) return null;
        int durationTicks = 200; // default 10s
        if (m.containsKey("duration-ticks")) {
            try { durationTicks = Integer.parseInt(m.get("duration-ticks").toString()); } catch (NumberFormatException ignored) {}
        } else if (m.containsKey("duration")) {
            boolean isTicks = Boolean.TRUE.equals(m.get("ticks"));
            try {
                int val = Integer.parseInt(m.get("duration").toString());
                durationTicks = isTicks ? val : val * 20;
            } catch (NumberFormatException ignored) {}
        }
        int amplifier = 0;
        if (m.containsKey("level")) {
            try { amplifier = Math.max(0, Integer.parseInt(m.get("level").toString()) - 1); } catch (NumberFormatException ignored) {}
        } else if (m.containsKey("amplifier")) {
            try { amplifier = Math.max(0, Integer.parseInt(m.get("amplifier").toString())); } catch (NumberFormatException ignored) {}
        }
        boolean ambient = Boolean.TRUE.equals(m.get("ambient"));
        boolean particles = !Boolean.FALSE.equals(m.get("particles"));
        boolean icon = !Boolean.FALSE.equals(m.get("icon"));
        return new PotionEffect(peType, durationTicks, amplifier, ambient, particles, icon);
    }
}

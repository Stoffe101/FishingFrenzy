package com.example.fishingFrenzy;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class FishingFrenzyListener implements Listener {
    private final FishingFrenzyManager manager;
    private final Random random = new Random();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Plugin plugin;
    private final NamespacedKey luckyRodKey;
    private FileConfiguration config;
    private Set<String> allowedWorlds;
    private Set<String> allowedBiomes;
    private Set<String> allowedTimes;

    public FishingFrenzyListener(FishingFrenzyManager manager) {
        this.manager = manager;
        this.plugin = manager.getPlugin();
        this.config = plugin.getConfig();
        this.luckyRodKey = new NamespacedKey(plugin, "lucky_rod");
        allowedWorlds = new HashSet<>(config.getStringList("frenzy.allowed-worlds"));
        allowedBiomes = new HashSet<>(config.getStringList("frenzy.allowed-biomes"));
        allowedTimes = new HashSet<>(config.getStringList("frenzy.allowed-times"));
    }

    public void reloadConfig(FileConfiguration config) {
        this.config = config;
        allowedWorlds = new HashSet<>(config.getStringList("frenzy.allowed-worlds"));
        allowedBiomes = new HashSet<>(config.getStringList("frenzy.allowed-biomes"));
        allowedTimes = new HashSet<>(config.getStringList("frenzy.allowed-times"));
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
        // Check allowed biome
        Biome biome = player.getLocation().getBlock().getBiome();
        if (!allowedBiomes.isEmpty() && !allowedBiomes.contains(biome.name())) {
            if (manager.isDebug()) plugin.getLogger().fine("Skip: biome not allowed: " + biome.name());
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
                // Detect Lucky Rod (by PDC or name fallback)
                boolean hasLuckyRod = false;
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand != null && hand.getType() == Material.FISHING_ROD && hand.hasItemMeta()) {
                    ItemMeta meta = hand.getItemMeta();
                    PersistentDataContainer pdc = meta.getPersistentDataContainer();
                    hasLuckyRod = pdc.has(luckyRodKey, PersistentDataType.BYTE);
                    if (!hasLuckyRod && meta.hasDisplayName()) {
                        hasLuckyRod = meta.getDisplayName().contains("Lucky Rod");
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
                int min = Integer.parseInt(parts[0]);
                int max = Integer.parseInt(parts[1]);
                amount = min + random.nextInt(max - min + 1);
            } else {
                amount = Integer.parseInt(amtStr);
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
                            Enchantment enchant = Enchantment.getByName(name.toUpperCase());
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
                            Enchantment enchant = Enchantment.getByName(name.toUpperCase());
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
        // Handle custom name (serialize MiniMessage to legacy string)
        if (map.containsKey("name")) {
            ItemMeta meta = item.getItemMeta();
            String legacy = LegacyComponentSerializer.legacySection().serialize(miniMessage.deserialize(map.get("name").toString()))
                    .replace("§r", "");
            meta.setDisplayName(legacy);
            item.setItemMeta(meta);
        }
        // Handle custom lore
        if (map.containsKey("lore")) {
            ItemMeta meta = item.getItemMeta();
            List<String> loreList = (List<String>) map.get("lore");
            List<String> coloredLore = new java.util.ArrayList<>();
            for (String line : loreList) {
                String legacy = LegacyComponentSerializer.legacySection().serialize(miniMessage.deserialize(line)).replace("§r", "");
                coloredLore.add(legacy);
            }
            meta.setLore(coloredLore);
            item.setItemMeta(meta);
        }
        // Mark Lucky Rod via PDC for reliable detection
        if (map.containsKey("lucky-rod") && Boolean.TRUE.equals(map.get("lucky-rod"))) {
            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(luckyRodKey, PersistentDataType.BYTE, (byte)1);
            item.setItemMeta(meta);
        }
        return item;
    }
}

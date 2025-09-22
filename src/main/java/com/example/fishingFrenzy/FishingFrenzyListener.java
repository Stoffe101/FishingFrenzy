package com.example.fishingFrenzy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Particle;
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
import org.bukkit.plugin.Plugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class FishingFrenzyListener implements Listener {
    private final FishingFrenzyManager manager;
    private final Random random = new Random();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Plugin plugin;
    private FileConfiguration config;
    private Set<String> allowedWorlds;
    private Set<String> allowedBiomes;
    private Set<String> allowedTimes;

    public FishingFrenzyListener(FishingFrenzyManager manager) {
        this.manager = manager;
        this.plugin = manager.getPlugin();
        this.config = plugin.getConfig();
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
        if (!allowedWorlds.isEmpty() && !allowedWorlds.contains(player.getWorld().getName())) return;
        // Check allowed biome
        Biome biome = player.getLocation().getBlock().getBiome();
        if (!allowedBiomes.isEmpty() && !allowedBiomes.contains(biome.name())) return;
        // Check allowed time
        if (!allowedTimes.isEmpty() && !isAllowedTime(player.getWorld().getTime())) return;
        // Cooldown checks
        if (manager.isPlayerOnCooldown(player) || manager.isGlobalOnCooldown()) {
            player.sendMessage(miniMessage.deserialize("<red>Fishing Frenzy is on cooldown!"));
            return;
        }
        // Increment frenzy meter
        manager.incrementFrenzyMeter(player);
        // Streak logic
        manager.incrementStreak(player);
        int streak = manager.getStreak(player);
        double luckMultiplier = manager.getLuckMultiplier(player);
        // ActionBar streak feedback
        player.sendActionBar(miniMessage.deserialize("<green>Streak: <yellow>" + streak + " <gray>(Luck x" + String.format("%.2f", luckMultiplier) + ")"));
        // Loot logic
        boolean gotSpicy = false;
        if (manager.isFrenzyActive()) {
            ItemStack loot = null;
            if (manager.shouldPity(player)) {
                loot = getRandomSpicyLootFromConfig();
                gotSpicy = true;
                player.sendMessage(miniMessage.deserialize("<gold>Frenzy Pity! <aqua>You are guaranteed something spicy!"));
            } else {
                double baseChance = 0.20 * manager.getLootMultiplier() * luckMultiplier;
                // Detect Lucky Rod
                boolean hasLuckyRod = false;
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand != null && hand.getType() == Material.FISHING_ROD && hand.hasItemMeta()) {
                    ItemMeta meta = hand.getItemMeta();
                    if (meta.hasDisplayName() && meta.getDisplayName().contains("Lucky Rod")) {
                        hasLuckyRod = true;
                    }
                }
                if (hasLuckyRod) baseChance *= 2.0; // Double spicy chance with Lucky Rod
                if (random.nextDouble() < baseChance) {
                    loot = getRandomLootFromConfig();
                    gotSpicy = isSpicyLoot(loot);
                }
            }
            if (loot != null) {
                player.getWorld().dropItemNaturally(player.getLocation(), loot);
                player.sendMessage(miniMessage.deserialize("<aqua>Fishing Frenzy! <green>You caught something special!"));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0,1,0), 30, 0.5, 0.5, 0.5, 0.1);
            }
            manager.incrementPity(player, gotSpicy);
        }
        // Set cooldown after catch
        manager.setPlayerCooldown(player);
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
        // Handle enchants for books and gear
        if (map.containsKey("enchants")) {
            Object enchObj = map.get("enchants");
            if (enchObj instanceof List) {
                List<?> enchList = (List<?>) enchObj;
                if (!enchList.isEmpty()) {
                    if (mat == Material.ENCHANTED_BOOK) {
                        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) item.getItemMeta();
                        for (Object ench : enchList) {
                            Enchantment enchant = Enchantment.getByName(ench.toString());
                            if (enchant != null && meta != null) {
                                meta.addStoredEnchant(enchant, 1 + random.nextInt(2), true);
                            }
                        }
                        item.setItemMeta(meta);
                    } else {
                        ItemMeta meta = item.getItemMeta();
                        for (Object ench : enchList) {
                            Enchantment enchant = Enchantment.getByName(ench.toString());
                            if (enchant != null && meta != null) {
                                meta.addEnchant(enchant, 3 + random.nextInt(3), true); // Level 3-5
                            }
                        }
                        item.setItemMeta(meta);
                    }
                }
            }
        }
        // Handle custom name
        if (map.containsKey("name")) {
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(miniMessage.deserialize(map.get("name").toString()).toString());
            item.setItemMeta(meta);
        }
        // Handle custom lore
        if (map.containsKey("lore")) {
            ItemMeta meta = item.getItemMeta();
            List<String> loreList = (List<String>) map.get("lore");
            List<String> coloredLore = new java.util.ArrayList<>();
            for (String line : loreList) {
                coloredLore.add(miniMessage.deserialize(line).toString());
            }
            meta.setLore(coloredLore);
            item.setItemMeta(meta);
        }
        return item;
    }
}

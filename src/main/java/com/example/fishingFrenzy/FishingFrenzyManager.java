package com.example.fishingFrenzy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.bossbar.BossBar.Color;
import net.kyori.adventure.bossbar.BossBar.Overlay;

public class FishingFrenzyManager {
    private final Plugin plugin;
    private FileConfiguration config;
    private boolean frenzyActive = false;
    private int frenzyTimeLeft = 0; // seconds
    private int nextFrenzyIn;
    private int warningTime;
    private int frenzyDuration;
    private int globalCooldown;
    private int perPlayerCooldown;
    private double lootMultiplier;
    private String mode;
    private BukkitAudiences adventure;
    private MiniMessage miniMessage = MiniMessage.miniMessage();
    private BossBar globalBossBar;

    private int frenzyMeter = 0;
    private int frenzyMeterRequired;
    private boolean meterEnabled;
    private Map<UUID, Integer> playerStreaks = new HashMap<>();
    private Map<UUID, Long> playerCooldowns = new HashMap<>();
    private long globalCooldownEnd = 0;
    private double luckPerStreak;
    private int maxStreak;
    private Map<UUID, Integer> playerPityCounters = new HashMap<>();
    private int pityThreshold;

    public FishingFrenzyManager(Plugin plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
        this.adventure = BukkitAudiences.create(plugin);
        loadConfig();
    }

    public void reloadConfig(FileConfiguration config) {
        this.config = config;
        loadConfig();
        createGlobalBossBar();
    }

    private void loadConfig() {
        this.frenzyDuration = config.getInt("frenzy.duration", 90);
        this.globalCooldown = config.getInt("frenzy.cooldown", 3600);
        this.perPlayerCooldown = config.getInt("frenzy.per-player-cooldown", 600);
        this.lootMultiplier = config.getDouble("frenzy.loot-multiplier", 1.0);
        this.mode = config.getString("frenzy.mode", "global");
        this.warningTime = 300; // TODO: make configurable if needed
        this.nextFrenzyIn = globalCooldown;
        this.meterEnabled = config.getBoolean("frenzy.meter.enabled", true);
        this.frenzyMeterRequired = config.getInt("frenzy.meter.required-fish", 100);
        this.luckPerStreak = config.getDouble("frenzy.streaks.luck-per-streak", 0.05);
        this.maxStreak = config.getInt("frenzy.streaks.max-streak", 5);
        this.frenzyMeter = 0;
        this.pityThreshold = config.getInt("frenzy.pity-threshold", 15);
        this.playerStreaks.clear();
        this.playerCooldowns.clear();
        this.playerPityCounters.clear();
        this.globalCooldownEnd = 0;
    }

    public boolean isFrenzyActive() {
        return frenzyActive;
    }

    public void startScheduler() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (frenzyActive) {
                    frenzyTimeLeft--;
                    updateGlobalBossBar();
                    if (frenzyTimeLeft % 20 == 0 || frenzyTimeLeft <= 10) {
                        sendActionBarToAll("<aqua>Fishing Frenzy! <white>Time left: <yellow>" + formatTime(frenzyTimeLeft));
                    }
                    if (frenzyTimeLeft <= 0) {
                        endFrenzy();
                    }
                } else {
                    nextFrenzyIn--;
                    updateGlobalBossBar();
                    if (nextFrenzyIn == warningTime) {
                        sendTitleToAll("<yellow>Fishing Frenzy soon!", "<gray>Starts in 5 minutes!");
                    }
                    if (nextFrenzyIn <= 60 && nextFrenzyIn % 10 == 0) {
                        sendActionBarToAll("<yellow>Fishing Frenzy starts in: <aqua>" + formatTime(nextFrenzyIn));
                    }
                    if (nextFrenzyIn <= 0) {
                        startFrenzy();
                    }
                }
            }
        }.runTaskTimer(plugin, 20, 20); // every second
        createGlobalBossBar();
    }

    private void createGlobalBossBar() {
        String title = config.getString("frenzy.meter.bossbar-title", "<gradient:aqua:blue>Frenzy Meter: <white><progress>");
        globalBossBar = BossBar.bossBar(miniMessage.deserialize(title.replace("<progress>", "")), 1.0f, Color.BLUE, Overlay.PROGRESS);
        for (Player p : Bukkit.getOnlinePlayers()) {
            adventure.player(p).showBossBar(globalBossBar);
        }
    }

    private void updateGlobalBossBar() {
        if (globalBossBar == null) return;
        float progress = frenzyActive ? (float)frenzyTimeLeft / (float)frenzyDuration : (float)nextFrenzyIn / (float)globalCooldown;
        String title = config.getString("frenzy.meter.bossbar-title", "<gradient:aqua:blue>Frenzy Meter: <white><progress>");
        String progressText = frenzyActive ? formatTime(frenzyTimeLeft) : formatTime(nextFrenzyIn);
        globalBossBar.name(miniMessage.deserialize(title.replace("<progress>", progressText)));
        globalBossBar.progress(Math.max(0f, Math.min(1f, progress)));
    }

    private void startFrenzy() {
        frenzyActive = true;
        frenzyTimeLeft = frenzyDuration;
        nextFrenzyIn = globalCooldown;
        setGlobalCooldown();
        sendTitleToAll("<aqua>Fishing Frenzy!", "<white>Catch rare loot for " + (frenzyDuration / 60) + " minutes!");
        sendActionBarToAll("<aqua>Fishing Frenzy has started! <white>Time left: <yellow>" + formatTime(frenzyTimeLeft));
    }

    private void endFrenzy() {
        frenzyActive = false;
        sendTitleToAll("<red>Fishing Frenzy Ended", "<gray>Back to normal fishing.");
    }

    public void sendStatus(CommandSender sender) {
        if (frenzyActive) {
            sender.sendMessage("§bFishing Frenzy is ACTIVE! Time left: §e" + formatTime(frenzyTimeLeft));
        } else {
            sender.sendMessage("§7Next Fishing Frenzy in: §b" + formatTime(nextFrenzyIn));
        }
        sender.sendMessage("§7Mode: §f" + mode + " §7Loot Multiplier: §f" + lootMultiplier);
    }

    private void sendTitleToAll(String title, String subtitle) {
        Component t = miniMessage.deserialize(title);
        Component s = miniMessage.deserialize(subtitle);
        Bukkit.getOnlinePlayers().forEach(p -> adventure.player(p).showTitle(net.kyori.adventure.title.Title.title(t, s)));
    }

    private void sendActionBarToAll(String msg) {
        Component c = miniMessage.deserialize(msg);
        Bukkit.getOnlinePlayers().forEach(p -> adventure.player(p).sendActionBar(c));
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public double getLootMultiplier() {
        return lootMultiplier;
    }

    private String formatTime(int seconds) {
        int min = seconds / 60;
        int sec = seconds % 60;
        return String.format("%d:%02d", min, sec);
    }

    public void incrementFrenzyMeter(Player player) {
        if (!meterEnabled || frenzyActive) return;
        frenzyMeter++;
        updateGlobalBossBar();
        if (frenzyMeter >= frenzyMeterRequired) {
            triggerGlobalFrenzy();
        }
    }

    private void triggerGlobalFrenzy() {
        frenzyMeter = 0;
        startFrenzy();
    }

    public void incrementStreak(Player player) {
        UUID uuid = player.getUniqueId();
        int streak = playerStreaks.getOrDefault(uuid, 0) + 1;
        if (streak > maxStreak) streak = maxStreak;
        playerStreaks.put(uuid, streak);
    }

    public void resetStreak(Player player) {
        playerStreaks.put(player.getUniqueId(), 0);
    }

    public int getStreak(Player player) {
        return playerStreaks.getOrDefault(player.getUniqueId(), 0);
    }

    public double getLuckMultiplier(Player player) {
        int streak = getStreak(player);
        return 1.0 + (luckPerStreak * streak);
    }

    public boolean isPlayerOnCooldown(Player player) {
        long now = System.currentTimeMillis();
        return playerCooldowns.getOrDefault(player.getUniqueId(), 0L) > now;
    }

    public void setPlayerCooldown(Player player) {
        playerCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + perPlayerCooldown * 1000L);
    }

    public boolean isGlobalOnCooldown() {
        return globalCooldownEnd > System.currentTimeMillis();
    }

    public void setGlobalCooldown() {
        globalCooldownEnd = System.currentTimeMillis() + globalCooldown * 1000L;
    }

    public void incrementPity(Player player, boolean gotSpicy) {
        UUID uuid = player.getUniqueId();
        if (gotSpicy) {
            playerPityCounters.put(uuid, 0);
        } else {
            int pity = playerPityCounters.getOrDefault(uuid, 0) + 1;
            playerPityCounters.put(uuid, pity);
        }
    }

    public boolean shouldPity(Player player) {
        return playerPityCounters.getOrDefault(player.getUniqueId(), 0) >= pityThreshold;
    }

    public void resetPity(Player player) {
        playerPityCounters.put(player.getUniqueId(), 0);
    }
}

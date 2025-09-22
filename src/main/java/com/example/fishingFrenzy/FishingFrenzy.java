package com.example.fishingFrenzy;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class FishingFrenzy extends JavaPlugin {

    private FishingFrenzyManager frenzyManager;
    private FileConfiguration config;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        config = getConfig();
        frenzyManager = new FishingFrenzyManager(this, config);
        getServer().getPluginManager().registerEvents(new FishingFrenzyListener(frenzyManager), this);
        frenzyManager.startScheduler();
        getCommand("frenzy").setExecutor(this);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            config = getConfig();
            frenzyManager.reloadConfig(config);
            sender.sendMessage("<green>Fishing Frenzy config reloaded!".replace('<', '§'));
            return true;
        } else if (args.length == 1 && args[0].equalsIgnoreCase("status")) {
            frenzyManager.sendStatus(sender);
            return true;
        }
        sender.sendMessage("<yellow>Usage: /frenzy <reload|status>".replace('<', '§'));
        return true;
    }

    public FileConfiguration getPluginConfig() {
        return config;
    }
}

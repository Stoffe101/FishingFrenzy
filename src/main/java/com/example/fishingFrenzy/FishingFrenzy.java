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

        // Use Bukkit command declared in plugin.yml
        if (getCommand("frenzy") != null) {
            getCommand("frenzy").setExecutor(this);
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("frenzy")) return false;
        return handleFrenzyCommand(sender, args);
    }

    private boolean handleFrenzyCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.isOp() && !sender.hasPermission("fishingfrenzy.command")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            config = getConfig();
            frenzyManager.reloadConfig(config);
            sender.sendMessage("§aFishing Frenzy config reloaded!");
            return true;
        } else if (args.length == 1 && args[0].equalsIgnoreCase("status")) {
            frenzyManager.sendStatus(sender);
            return true;
        }
        sender.sendMessage("§eUsage: /frenzy <reload|status>");
        return true;
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public FileConfiguration getPluginConfig() {
        return config;
    }
}

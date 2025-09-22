package com.example.fishingFrenzy;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class FishingFrenzy extends JavaPlugin {

    private FishingFrenzyManager frenzyManager;
    private FishingFrenzyListener listener;
    private FileConfiguration config;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        config = getConfig();
        frenzyManager = new FishingFrenzyManager(this, config);
        listener = new FishingFrenzyListener(frenzyManager);
        getServer().getPluginManager().registerEvents(listener, this);
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
        if (args.length == 1) {
            String sub = args[0].toLowerCase();
            switch (sub) {
                case "reload":
                    reloadConfig();
                    config = getConfig();
                    frenzyManager.reloadConfig(config);
                    if (listener != null) listener.reloadConfig(config);
                    sender.sendMessage("§aFishing Frenzy config reloaded!");
                    return true;
                case "status":
                    frenzyManager.sendStatus(sender);
                    return true;
                case "start":
                    if (frenzyManager.tryStartFrenzy()) {
                        sender.sendMessage("§aStarted Fishing Frenzy.");
                    } else {
                        sender.sendMessage("§eFrenzy is already active.");
                    }
                    return true;
                case "end":
                    if (frenzyManager.tryEndFrenzy()) {
                        sender.sendMessage("§cEnded Fishing Frenzy.");
                    } else {
                        sender.sendMessage("§eNo active frenzy to end.");
                    }
                    return true;
            }
        }
        sender.sendMessage("§eUsage: /frenzy <reload|status|start|end>");
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

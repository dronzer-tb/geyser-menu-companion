package com.geysermenu.companion.spigot;

import com.geysermenu.companion.api.GeyserMenuAPI;
import com.geysermenu.companion.network.MenuClient;
import com.geysermenu.companion.api.BedrockPlayer;
import com.geysermenu.companion.spigot.api.SpigotGeyserMenuAPI;
import com.geysermenu.companion.spigot.config.SpigotConfig;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class GeyserMenuSpigot extends JavaPlugin {

    private static GeyserMenuSpigot instance;

    private SpigotConfig config;
    private MenuClient menuClient;
    private SpigotGeyserMenuAPI api;
    private boolean floodgateAvailable;

    @Override
    public void onEnable() {
        instance = this;

        // Check for Floodgate
        floodgateAvailable = getServer().getPluginManager().getPlugin("floodgate") != null;
        if (!floodgateAvailable) {
            getLogger().warning("Floodgate not found! Bedrock player detection may not work correctly.");
        }

        // Load configuration
        saveDefaultConfig();
        this.config = new SpigotConfig(this);

        // Initialize API
        this.api = new SpigotGeyserMenuAPI(this);
        GeyserMenuAPI.setInstance(api);

        // Connect to GeyserMenu extension
        connectToExtension();

        getLogger().info("GeyserMenuCompanion has been enabled!");
    }

    @Override
    public void onDisable() {
        if (menuClient != null) {
            menuClient.disconnect();
        }
        getLogger().info("GeyserMenuCompanion has been disabled!");
    }

    private void connectToExtension() {
        String host = config.getExtensionHost();
        int port = config.getExtensionPort();
        String secretKey = config.getSecretKey();
        String serverIdentifier = config.getServerIdentifier();
        boolean enableSsl = config.isEnableSsl();

        menuClient = new MenuClient(host, port, secretKey, serverIdentifier, getLogger(), enableSsl);
        menuClient.setAutoReconnect(config.isAutoReconnect());
        menuClient.setReconnectDelaySeconds(config.getReconnectDelay());

        menuClient.onConnectionLost(() -> {
            getLogger().warning("Connection to GeyserMenu extension lost!");
        });

        menuClient.onButtonClick(click -> {
            // Find player and call API handler
            java.util.UUID playerUuid = java.util.UUID.fromString(click.getPlayerUuid());
            BedrockPlayer player = new BedrockPlayer(playerUuid, click.getXuid(), click.getPlayerName());
            api.handleButtonClick(click.getButtonId(), player, null);
        });
        menuClient.onError(error -> {
            getLogger().warning("GeyserMenu error: " + error);
        });

        menuClient.connect().thenAccept(success -> {
            if (success) {
                getLogger().info("Successfully connected to GeyserMenu extension!");
                // Sync any already registered buttons
                api.resyncButtons();
            } else {
                getLogger().warning("Failed to connect to GeyserMenu extension. Will retry...");
            }
        });
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("geysermenu.admin")) {
            sender.sendMessage("You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("GeyserMenuCompanion v" + getDescription().getVersion());
            sender.sendMessage("Status: " + (menuClient.isConnected() ? "Connected" : "Disconnected"));
            sender.sendMessage("Commands: /geysermenu [reload|status|reconnect]");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                reloadConfig();
                config = new SpigotConfig(this);
                sender.sendMessage("Configuration reloaded!");
            }
            case "status" -> {
                sender.sendMessage("Connection Status:");
                sender.sendMessage("  Connected: " + menuClient.isConnected());
                sender.sendMessage("  Authenticated: " + menuClient.isAuthenticated());
                sender.sendMessage("  Floodgate: " + (floodgateAvailable ? "Available" : "Not found"));
            }
            case "reconnect" -> {
                sender.sendMessage("Reconnecting to GeyserMenu extension...");
                if (menuClient != null) {
                    menuClient.disconnect();
                }
                connectToExtension();
            }
            default -> sender.sendMessage("Unknown subcommand. Use: reload, status, reconnect");
        }

        return true;
    }

    public static GeyserMenuSpigot getInstance() {
        return instance;
    }

    public MenuClient getMenuClient() {
        return menuClient;
    }

    public boolean isFloodgateAvailable() {
        return floodgateAvailable;
    }

    public SpigotConfig getPluginConfig() {
        return config;
    }
}

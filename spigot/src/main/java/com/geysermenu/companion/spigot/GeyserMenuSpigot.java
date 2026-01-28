package com.geysermenu.companion.spigot;

import com.geysermenu.companion.api.GeyserMenuAPI;
import com.geysermenu.companion.network.MenuClient;
import com.geysermenu.companion.api.BedrockPlayer;
import com.geysermenu.companion.spigot.api.SpigotGeyserMenuAPI;
import com.geysermenu.companion.spigot.config.SpigotConfig;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
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

        // Resync buttons when authentication succeeds (important for reconnection)
        menuClient.onAuthSuccess(() -> {
            getLogger().info("Authenticated with GeyserMenu extension, syncing buttons...");
            api.resyncButtons();
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
            } else {
                getLogger().warning("Failed to connect to GeyserMenu extension. Will retry...");
            }
        });
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Handle /gemu command - opens the menu for bedrock players or reorders buttons
        if (command.getName().equalsIgnoreCase("gemu")) {
            return handleGemuCommand(sender, args);
        }
        
        // Handle /geysermenu admin command
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
    
    /**
     * Handle the /gemu command to open the GeyserMenu for Bedrock players
     * or reorder buttons with syntax: /gemu p:<name> s:<position>
     */
    private boolean handleGemuCommand(CommandSender sender, String[] args) {
        // Check for button reorder syntax: /gemu p:<name> s:<position>
        if (args.length >= 2) {
            return handleButtonReorder(sender, args);
        }
        
        // Standard menu open for players
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            sender.sendMessage("§eAdmin usage: /gemu p:<button_name> s:<position>");
            return true;
        }
        
        if (!menuClient.isConnected()) {
            player.sendMessage("§cGeyserMenu is not connected. Please try again later.");
            return true;
        }
        
        // Check if player is a Bedrock player
        if (floodgateAvailable) {
            try {
                org.geysermc.floodgate.api.FloodgateApi floodgate = org.geysermc.floodgate.api.FloodgateApi.getInstance();
                if (!floodgate.isFloodgatePlayer(player.getUniqueId())) {
                    player.sendMessage("§cThis command is only available for Bedrock players.");
                    return true;
                }
            } catch (Exception e) {
                getLogger().warning("Error checking Floodgate status: " + e.getMessage());
            }
        }
        
        // Request the extension to open the main menu
        menuClient.requestOpenMainMenu(player.getUniqueId());
        getLogger().info("Requested menu open for player: " + player.getName());
        return true;
    }
    
    /**
     * Handle button reordering: /gemu p:<name> s:<position>
     */
    private boolean handleButtonReorder(CommandSender sender, String[] args) {
        if (!sender.hasPermission("geysermenu.admin")) {
            sender.sendMessage("§cYou don't have permission to reorder buttons.");
            return true;
        }
        
        if (!menuClient.isConnected()) {
            sender.sendMessage("§cGeyserMenu is not connected.");
            return true;
        }
        
        String buttonName = null;
        Integer position = null;
        
        // Parse arguments: p:<name> s:<position>
        for (String arg : args) {
            if (arg.toLowerCase().startsWith("p:")) {
                buttonName = arg.substring(2);
            } else if (arg.toLowerCase().startsWith("s:")) {
                try {
                    position = Integer.parseInt(arg.substring(2));
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cInvalid position number: " + arg.substring(2));
                    return true;
                }
            }
        }
        
        if (buttonName == null || buttonName.isEmpty()) {
            sender.sendMessage("§cButton name not specified. Usage: /gemu p:<button_name> s:<position>");
            sender.sendMessage("§7Example: /gemu p:spawn s:1");
            return true;
        }
        
        if (position == null || position < 1) {
            sender.sendMessage("§cPosition not specified or invalid. Usage: /gemu p:<button_name> s:<position>");
            sender.sendMessage("§7Position must be 1 or greater (1 = first)");
            return true;
        }
        
        // Send reorder request to extension
        menuClient.requestReorderButton(buttonName, position);
        sender.sendMessage("§aButton '" + buttonName + "' moved to position " + position);
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

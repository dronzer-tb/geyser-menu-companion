package com.geysermenu.companion.velocity;

import com.geysermenu.companion.api.GeyserMenuAPI;
import com.geysermenu.companion.network.MenuClient;
import com.geysermenu.companion.velocity.api.VelocityGeyserMenuAPI;
import com.geysermenu.companion.velocity.config.VelocityConfig;
import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.List;

@Plugin(
        id = "geysermenucompanion",
        name = "GeyserMenuCompanion",
        version = "1.0.0-SNAPSHOT",
        description = "Companion plugin for GeyserMenu - API for sending menus to Bedrock players",
        authors = {"kasniya"},
        dependencies = {
                @Dependency(id = "floodgate", optional = true)
        }
)
public class GeyserMenuVelocity {

    private static GeyserMenuVelocity instance;

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private VelocityConfig config;
    private MenuClient menuClient;
    private VelocityGeyserMenuAPI api;
    private boolean floodgateAvailable;

    @Inject
    public GeyserMenuVelocity(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        instance = this;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        // Check for Floodgate
        floodgateAvailable = server.getPluginManager().getPlugin("floodgate").isPresent();
        if (!floodgateAvailable) {
            logger.warn("Floodgate not found! Bedrock player detection may not work correctly.");
        }

        // Load configuration
        this.config = new VelocityConfig(dataDirectory, logger);

        // Initialize API
        this.api = new VelocityGeyserMenuAPI(this);
        GeyserMenuAPI.setInstance(api);

        // Register command
        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("geysermenu")
                        .aliases("gm", "gmenu")
                        .build(),
                new GeyserMenuCommand()
        );

        // Connect to GeyserMenu extension
        connectToExtension();

        logger.info("GeyserMenuCompanion has been enabled!");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (menuClient != null) {
            menuClient.disconnect();
        }
        logger.info("GeyserMenuCompanion has been disabled!");
    }

    private void connectToExtension() {
        String host = config.getExtensionHost();
        int port = config.getExtensionPort();
        String secretKey = config.getSecretKey();
        String serverIdentifier = config.getServerIdentifier();
        boolean enableSsl = config.isEnableSsl();

        // Create a JUL logger wrapper for the MenuClient
        java.util.logging.Logger julLogger = java.util.logging.Logger.getLogger("GeyserMenuCompanion");

        menuClient = new MenuClient(host, port, secretKey, serverIdentifier, julLogger, enableSsl);
        menuClient.setAutoReconnect(config.isAutoReconnect());
        menuClient.setReconnectDelaySeconds(config.getReconnectDelay());

        menuClient.onConnectionLost(() -> {
            logger.warn("Connection to GeyserMenu extension lost!");
        });

        menuClient.onError(error -> {
            logger.warn("GeyserMenu error: " + error);
        });

        menuClient.connect().thenAccept(success -> {
            if (success) {
                logger.info("Successfully connected to GeyserMenu extension!");
            } else {
                logger.warn("Failed to connect to GeyserMenu extension. Will retry...");
            }
        });
    }

    public static GeyserMenuVelocity getInstance() {
        return instance;
    }

    public ProxyServer getServer() {
        return server;
    }

    public Logger getLogger() {
        return logger;
    }

    public MenuClient getMenuClient() {
        return menuClient;
    }

    public boolean isFloodgateAvailable() {
        return floodgateAvailable;
    }

    public VelocityConfig getPluginConfig() {
        return config;
    }

    private class GeyserMenuCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            String[] args = invocation.arguments();

            if (!source.hasPermission("geysermenu.admin")) {
                source.sendMessage(Component.text("You don't have permission to use this command."));
                return;
            }

            if (args.length == 0) {
                source.sendMessage(Component.text("GeyserMenuCompanion v1.0.0"));
                source.sendMessage(Component.text("Status: " + (menuClient.isConnected() ? "Connected" : "Disconnected")));
                source.sendMessage(Component.text("Commands: /geysermenu [reload|status|reconnect]"));
                return;
            }

            switch (args[0].toLowerCase()) {
                case "reload" -> {
                    config = new VelocityConfig(dataDirectory, logger);
                    source.sendMessage(Component.text("Configuration reloaded!"));
                }
                case "status" -> {
                    source.sendMessage(Component.text("Connection Status:"));
                    source.sendMessage(Component.text("  Connected: " + menuClient.isConnected()));
                    source.sendMessage(Component.text("  Authenticated: " + menuClient.isAuthenticated()));
                    source.sendMessage(Component.text("  Floodgate: " + (floodgateAvailable ? "Available" : "Not found")));
                }
                case "reconnect" -> {
                    source.sendMessage(Component.text("Reconnecting to GeyserMenu extension..."));
                    if (menuClient != null) {
                        menuClient.disconnect();
                    }
                    connectToExtension();
                }
                default -> source.sendMessage(Component.text("Unknown subcommand. Use: reload, status, reconnect"));
            }
        }

        @Override
        public List<String> suggest(Invocation invocation) {
            if (invocation.arguments().length <= 1) {
                return List.of("reload", "status", "reconnect");
            }
            return List.of();
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return invocation.source().hasPermission("geysermenu.admin");
        }
    }
}

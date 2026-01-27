package com.geysermenu.companion.spigot.config;

import com.geysermenu.companion.spigot.GeyserMenuSpigot;
import org.bukkit.configuration.file.FileConfiguration;

public class SpigotConfig {

    private final GeyserMenuSpigot plugin;

    private String extensionHost;
    private int extensionPort;
    private String secretKey;
    private String serverIdentifier;
    private boolean autoReconnect;
    private int reconnectDelay;
    private boolean enableSsl;

    public SpigotConfig(GeyserMenuSpigot plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    private void loadConfig() {
        plugin.saveDefaultConfig();
        FileConfiguration config = plugin.getConfig();

        // Connection settings
        extensionHost = config.getString("connection.host", "127.0.0.1");
        extensionPort = config.getInt("connection.port", 19133);
        secretKey = config.getString("connection.secret-key", "CHANGE_ME");
        serverIdentifier = config.getString("connection.server-identifier", "spigot-server");

        // Reconnection settings
        autoReconnect = config.getBoolean("connection.auto-reconnect", true);
        reconnectDelay = config.getInt("connection.reconnect-delay", 5);
        enableSsl = config.getBoolean("connection.enable-ssl", false);
    }

    public String getExtensionHost() {
        return extensionHost;
    }

    public int getExtensionPort() {
        return extensionPort;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public String getServerIdentifier() {
        return serverIdentifier;
    }

    public boolean isAutoReconnect() {
        return autoReconnect;
    }

    public int getReconnectDelay() {
        return reconnectDelay;
    }

    public boolean isEnableSsl() {
        return enableSsl;
    }
}

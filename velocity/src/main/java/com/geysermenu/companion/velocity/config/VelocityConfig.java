package com.geysermenu.companion.velocity.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class VelocityConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private String extensionHost = "127.0.0.1";
    private int extensionPort = 19133;
    private String secretKey = "CHANGE_ME";
    private String serverIdentifier = "velocity-proxy";
    private boolean autoReconnect = true;
    private int reconnectDelay = 5;
    private boolean enableSsl = false;

    public VelocityConfig(Path dataDirectory, Logger logger) {
        Path configPath = dataDirectory.resolve("config.json");

        try {
            Files.createDirectories(dataDirectory);

            if (Files.exists(configPath)) {
                try (Reader reader = Files.newBufferedReader(configPath)) {
                    VelocityConfig loaded = GSON.fromJson(reader, VelocityConfig.class);
                    if (loaded != null) {
                        this.extensionHost = loaded.extensionHost;
                        this.extensionPort = loaded.extensionPort;
                        this.secretKey = loaded.secretKey;
                        this.serverIdentifier = loaded.serverIdentifier;
                        this.autoReconnect = loaded.autoReconnect;
                        this.reconnectDelay = loaded.reconnectDelay;
                        this.enableSsl = loaded.enableSsl;
                    }
                }
            }

            // Save config (to add any new fields)
            try (Writer writer = Files.newBufferedWriter(configPath)) {
                GSON.toJson(this, writer);
            }

        } catch (IOException e) {
            logger.error("Failed to load/save config", e);
        }
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

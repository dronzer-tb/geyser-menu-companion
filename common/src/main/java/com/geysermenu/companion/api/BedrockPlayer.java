package com.geysermenu.companion.api;

import java.util.UUID;

/**
 * Represents a Bedrock player connected through Geyser.
 */
public class BedrockPlayer {
    
    private final UUID uuid;
    private final String xuid;
    private final String name;
    
    public BedrockPlayer(UUID uuid, String xuid, String name) {
        this.uuid = uuid;
        this.xuid = xuid;
        this.name = name;
    }
    
    /**
     * Get the player's Java UUID (assigned by Floodgate/Geyser)
     */
    public UUID getUuid() {
        return uuid;
    }
    
    /**
     * Get the player's Xbox Live ID (XUID)
     */
    public String getXuid() {
        return xuid;
    }
    
    /**
     * Get the player's Bedrock/Xbox username
     */
    public String getName() {
        return name;
    }
    
    @Override
    public String toString() {
        return "BedrockPlayer{" +
                "uuid=" + uuid +
                ", xuid='" + xuid + '\'' +
                ", name='" + name + '\'' +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BedrockPlayer that = (BedrockPlayer) o;
        return uuid.equals(that.uuid);
    }
    
    @Override
    public int hashCode() {
        return uuid.hashCode();
    }
}

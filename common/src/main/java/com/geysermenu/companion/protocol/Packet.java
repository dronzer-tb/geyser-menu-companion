package com.geysermenu.companion.protocol;

/**
 * Base packet class for communication protocol
 * Must match the extension's Packet class
 */
public class Packet {

    private PacketType type;
    private String payload;

    public Packet() {}

    public Packet(PacketType type, String payload) {
        this.type = type;
        this.payload = payload;
    }

    public enum PacketType {
        // Handshake
        AUTH_REQUEST,
        AUTH_RESPONSE,

        // Menu operations
        SEND_MENU,
        MENU_RESPONSE,

        // Button registration
        REGISTER_BUTTONS,   // Client -> Server: Register menu buttons from companion
        BUTTON_CLICKED,     // Server -> Client: A registered button was clicked
        REQUEST_BUTTONS,    // Server -> Client: Request companion to send registered buttons
        OPEN_MAIN_MENU,     // Client -> Server: Request to open main menu for a player
        REORDER_BUTTON,     // Client -> Server: Reorder a button to a specific position

        // Player state
        PLAYER_JOIN,
        PLAYER_LEAVE,
        PLAYER_LIST,
        PLAYER_LIST_RESPONSE,

        // Utility
        PING,
        PONG,
        ERROR
    }

    public PacketType getType() {
        return type;
    }

    public void setType(PacketType type) {
        this.type = type;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }
}

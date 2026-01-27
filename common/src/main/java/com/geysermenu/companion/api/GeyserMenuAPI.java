package com.geysermenu.companion.api;

import com.geysermenu.companion.menu.MenuData;
import com.geysermenu.companion.menu.MenuResponse;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The main API for interacting with GeyserMenu.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * GeyserMenuAPI api = GeyserMenuAPI.getInstance();
 *
 * // Register a button that appears in the main menu
 * api.registerButton(MenuButton.builder()
 *     .id("my-plugin-button")
 *     .text("My Plugin")
 *     .imageUrl("https://example.com/icon.png")
 *     .priority(10)
 *     .onClick((player, session) -> {
 *         openMyPluginMenu(player);
 *     })
 *     .build());
 *
 * // Create and send a simple menu
 * api.createSimpleMenu("My Menu", player.getUniqueId())
 *    .content("Choose an option:")
 *    .button("Option 1")
 *    .button("Option 2")
 *    .button("Option 3")
 *    .send(response -> {
 *        if (!response.wasClosed()) {
 *            player.sendMessage("You clicked: " + response.getButtonText());
 *        }
 *    });
 *
 * // Create a modal (yes/no) dialog
 * api.createModalMenu("Confirm", player.getUniqueId())
 *    .content("Are you sure you want to continue?")
 *    .button("Yes")
 *    .button("No")
 *    .send(response -> {
 *        if (response.getButtonId() == 0) {
 *            // Yes clicked
 *        }
 *    });
 *
 * // Create a custom form with inputs
 * api.createCustomMenu("Settings", player.getUniqueId())
 *    .input("name", "Your Name", "Enter name...", "")
 *    .toggle("notifications", "Enable Notifications", true)
 *    .slider("volume", "Volume", 0, 100, 1, 50)
 *    .send(response -> {
 *        String name = response.getString("name");
 *        Boolean notifications = response.getBoolean("notifications");
 *        Number volume = response.getNumber("volume");
 *    });
 * }</pre>
 */
public abstract class GeyserMenuAPI {

    private static GeyserMenuAPI instance;

    /**
     * Get the GeyserMenuAPI instance
     */
    public static GeyserMenuAPI getInstance() {
        return instance;
    }

    /**
     * Set the API instance (called by the plugin)
     */
    public static void setInstance(GeyserMenuAPI api) {
        instance = api;
    }

    // ==================== Connection Status ====================

    /**
     * Check if a player is a Bedrock player
     *
     * @param playerUuid The player's UUID
     * @return true if the player is a Bedrock player
     */
    public abstract boolean isBedrockPlayer(UUID playerUuid);

    /**
     * Check if the companion is connected to the GeyserMenu extension
     *
     * @return true if connected and authenticated
     */
    public abstract boolean isConnected();

    // ==================== Button Registration ====================

    /**
     * Register a button to appear in the main GeyserMenu.
     * The button will be visible to all players (or filtered by condition).
     *
     * @param button The button to register
     */
    public abstract void registerButton(MenuButton button);

    /**
     * Unregister a previously registered button.
     *
     * @param buttonId The button's unique ID
     */
    public abstract void unregisterButton(String buttonId);

    /**
     * Get all registered buttons.
     *
     * @return List of registered buttons
     */
    public abstract List<MenuButton> getRegisteredButtons();

    // ==================== Player Events ====================

    /**
     * Register a listener for when Bedrock players join.
     *
     * @param listener The listener to call when a player joins
     */
    public abstract void onPlayerJoin(Consumer<BedrockPlayer> listener);

    /**
     * Register a listener for when Bedrock players leave.
     *
     * @param listener The listener to call when a player leaves
     */
    public abstract void onPlayerLeave(Consumer<BedrockPlayer> listener);

    /**
     * Get all online Bedrock players.
     *
     * @return List of online Bedrock players
     */
    public abstract List<BedrockPlayer> getOnlineBedrockPlayers();

    // ==================== Form Sending ====================

    /**
     * Send a form to a player using the FormBuilder API.
     *
     * @param playerUuid The player's UUID
     * @param form The form to send (built using FormBuilder)
     */
    public abstract void sendForm(UUID playerUuid, FormBuilder.Form form);

    /**
     * Create a simple menu (button list)
     *
     * @param title The menu title
     * @param targetPlayer The target player's UUID
     * @return A menu builder
     */
    public abstract SimpleMenuBuilder createSimpleMenu(String title, UUID targetPlayer);

    /**
     * Create a modal menu (yes/no dialog)
     *
     * @param title The menu title
     * @param targetPlayer The target player's UUID
     * @return A menu builder
     */
    public abstract ModalMenuBuilder createModalMenu(String title, UUID targetPlayer);

    /**
     * Create a custom menu (forms with inputs, toggles, sliders)
     *
     * @param title The menu title
     * @param targetPlayer The target player's UUID
     * @return A menu builder
     */
    public abstract CustomMenuBuilder createCustomMenu(String title, UUID targetPlayer);

    /**
     * Send a pre-built menu
     *
     * @param menuData The menu data
     * @param callback Response callback
     */
    public abstract void sendMenu(MenuData menuData, Consumer<MenuResponse> callback);

    /**
     * Builder for simple menus (button list)
     */
    public interface SimpleMenuBuilder {
        SimpleMenuBuilder content(String content);
        SimpleMenuBuilder button(String text);
        SimpleMenuBuilder button(String text, String imageUrl);
        void send(Consumer<MenuResponse> callback);
    }

    /**
     * Builder for modal menus (yes/no dialog)
     */
    public interface ModalMenuBuilder {
        ModalMenuBuilder content(String content);
        ModalMenuBuilder button(String text);
        void send(Consumer<MenuResponse> callback);
    }

    /**
     * Builder for custom menus (forms)
     */
    public interface CustomMenuBuilder {
        CustomMenuBuilder label(String text);
        CustomMenuBuilder input(String id, String label, String placeholder, String defaultValue);
        CustomMenuBuilder toggle(String id, String label, boolean defaultValue);
        CustomMenuBuilder slider(String id, String label, float min, float max, float step, float defaultValue);
        CustomMenuBuilder dropdown(String id, String label, java.util.List<String> options);
        CustomMenuBuilder stepSlider(String id, String label, java.util.List<String> options);
        void send(Consumer<MenuResponse> callback);
    }
}

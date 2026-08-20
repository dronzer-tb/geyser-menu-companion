package com.geysermenu.companion.spigot.api;

import com.geysermenu.companion.api.BedrockPlayer;
import com.geysermenu.companion.api.FormBuilder;
import com.geysermenu.companion.api.GeyserMenuAPI;
import com.geysermenu.companion.api.MenuButton;
import com.geysermenu.companion.protocol.ButtonData;
import com.geysermenu.companion.menu.MenuData;
import com.geysermenu.companion.menu.MenuResponse;
import com.geysermenu.companion.spigot.GeyserMenuSpigot;
import com.geysermenu.companion.spigot.scheduler.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Spigot implementation of the GeyserMenu API
 */
public class SpigotGeyserMenuAPI extends GeyserMenuAPI {

    private final GeyserMenuSpigot plugin;
    private final Map<String, MenuButton> registeredButtons = new ConcurrentHashMap<>();
    private final List<Consumer<BedrockPlayer>> joinListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<BedrockPlayer>> leaveListeners = new CopyOnWriteArrayList<>();

    public SpigotGeyserMenuAPI(GeyserMenuSpigot plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isBedrockPlayer(UUID playerUuid) {
        if (plugin.isFloodgateAvailable()) {
            try {
                return FloodgateApi.getInstance().isFloodgatePlayer(playerUuid);
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    @Override
    public boolean isConnected() {
        return plugin.getMenuClient() != null && plugin.getMenuClient().isAuthenticated();
    }

    // ==================== Button Registration ====================

    @Override
    public void registerButton(MenuButton button) {
        if (button == null || button.getId() == null) {
            plugin.getLogger().warning("Cannot register button with null ID");
            return;
        }
        registeredButtons.put(button.getId(), button);
        plugin.getLogger().info("Registered menu button: " + button.getId());
        
        // Notify extension about the new button
        syncButtonsToExtension();
    }

    @Override
    public void unregisterButton(String buttonId) {
        if (buttonId == null) return;
        MenuButton removed = registeredButtons.remove(buttonId);
        if (removed != null) {
            plugin.getLogger().info("Unregistered menu button: " + buttonId);
            syncButtonsToExtension();
        }
    }

    @Override
    public List<MenuButton> getRegisteredButtons() {
        return new ArrayList<>(registeredButtons.values());
    }

    /**
     * Get buttons visible to a specific player (filtered by conditions)
     */
    public List<MenuButton> getButtonsForPlayer(BedrockPlayer player) {
        List<MenuButton> visible = new ArrayList<>();
        for (MenuButton button : registeredButtons.values()) {
            if (button.getCondition() == null || button.getCondition().test(player)) {
                visible.add(button);
            }
        }
        // Sort by priority (lower = first)
        visible.sort((a, b) -> Integer.compare(a.getPriority(), b.getPriority()));
        return visible;
    }

    /**
     * Handle a button click pushed to us by the GeyserMenu extension.
     *
     * <p>This is invoked on a Netty IO thread. Nothing below may touch the Bukkit API on that
     * thread, and on Folia the correct thread for anything player-facing is that player's own
     * region thread, so both the command dispatch and the third-party onClick handler are hopped
     * onto the player's entity scheduler. If the player is offline the click is dropped rather
     * than run on the wrong thread.
     */
    public void handleButtonClick(String buttonId, BedrockPlayer player, Object session) {
        MenuButton button = registeredButtons.get(buttonId);
        if (button == null) {
            plugin.getLogger().warning("Unknown button clicked: " + buttonId);
            return;
        }

        final String command = button.getCommand();
        final java.util.function.BiConsumer<Object, Object> onClick = button.getOnClick();
        final boolean hasCommand = command != null && !command.isEmpty();

        if (!hasCommand && onClick == null) {
            return;
        }

        // Netty IO thread -> global region thread (player lookup) -> that player's region thread.
        FoliaScheduler.runForPlayer(plugin, player.getUuid(), bukkitPlayer -> {
            if (hasCommand) {
                try {
                    bukkitPlayer.performCommand(command);
                } catch (Throwable t) {
                    plugin.getLogger().warning("Error running command for button " + buttonId + ": " + t);
                }
            }
            if (onClick != null) {
                try {
                    onClick.accept(player, session);
                } catch (Throwable t) {
                    plugin.getLogger().warning("Error in onClick handler for button " + buttonId + ": " + t);
                }
            }
        }, () -> plugin.getLogger().warning(
                "Button '" + buttonId + "' clicked by " + player.getName() + " but that player is not online here"));
    }

    /**
     * Re-sync buttons with extension (used after reconnection)
     */
    public void resyncButtons() {
        syncButtonsToExtension();
    }

    private void syncButtonsToExtension() {
        // Check if connected to extension
        if (plugin.getMenuClient() == null || !plugin.getMenuClient().isAuthenticated()) {
            plugin.getLogger().info("Cannot sync buttons - not connected/authenticated yet. Buttons queued: " + registeredButtons.size());
            return;
        }
        
        if (registeredButtons.isEmpty()) {
            plugin.getLogger().info("No buttons to sync to extension");
            return;
        }
        
        // Convert MenuButtons to ButtonData for transmission
        List<ButtonData> buttonDataList = new ArrayList<>();
        for (MenuButton button : registeredButtons.values()) {
            ButtonData data = new ButtonData(
                button.getId(),
                button.getText(),
                button.getImageUrl(),
                button.getImagePath(),
                button.getPriority()
            );
            buttonDataList.add(data);
            plugin.getLogger().info("Syncing button: " + button.getId() + " (text: " + button.getText() + ")");
        }
        
        // Send to extension
        plugin.getMenuClient().sendButtons(buttonDataList);
    }

    // ==================== Player Events ====================

    @Override
    public void onPlayerJoin(Consumer<BedrockPlayer> listener) {
        joinListeners.add(listener);
    }

    @Override
    public void onPlayerLeave(Consumer<BedrockPlayer> listener) {
        leaveListeners.add(listener);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Reads the global online-player list, so on Folia this must be called from the global
     * region thread (see {@link FoliaScheduler#runGlobal}), not from an IO thread.
     */
    @Override
    public List<BedrockPlayer> getOnlineBedrockPlayers() {
        List<BedrockPlayer> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isBedrockPlayer(player.getUniqueId())) {
                String xuid = "";
                if (plugin.isFloodgateAvailable()) {
                    try {
                        var floodgatePlayer = FloodgateApi.getInstance().getPlayer(player.getUniqueId());
                        if (floodgatePlayer != null) {
                            xuid = floodgatePlayer.getXuid();
                        }
                    } catch (Exception ignored) {}
                }
                players.add(new BedrockPlayer(player.getUniqueId(), xuid, player.getName()));
            }
        }
        return players;
    }

    /**
     * Called when a Bedrock player joins (from plugin event handler)
     */
    public void notifyPlayerJoin(BedrockPlayer player) {
        for (Consumer<BedrockPlayer> listener : joinListeners) {
            try {
                listener.accept(player);
            } catch (Exception e) {
                plugin.getLogger().warning("Error in player join listener: " + e.getMessage());
            }
        }
    }

    /**
     * Called when a Bedrock player leaves (from plugin event handler)
     */
    public void notifyPlayerLeave(BedrockPlayer player) {
        for (Consumer<BedrockPlayer> listener : leaveListeners) {
            try {
                listener.accept(player);
            } catch (Exception e) {
                plugin.getLogger().warning("Error in player leave listener: " + e.getMessage());
            }
        }
    }

    // ==================== Form Sending ====================

    @Override
    public void sendForm(UUID playerUuid, FormBuilder.Form form) {
        if (!isConnected()) {
            plugin.getLogger().warning("Cannot send form - not connected to GeyserMenu extension");
            return;
        }

        // Convert FormBuilder.Form to MenuData
        MenuData menuData = convertFormToMenuData(form, playerUuid);
        sendMenu(menuData, response -> {
            if (form.getResponseHandler() != null) {
                form.getResponseHandler().accept(response);
            }
        });
    }

    private MenuData convertFormToMenuData(FormBuilder.Form form, UUID playerUuid) {
        MenuData.Builder builder = MenuData.builder().target(playerUuid);
        
        switch (form.getType()) {
            case "simple" -> {
                builder.simple().title(form.getTitle());
                if (form.getContent() != null) builder.content(form.getContent());
                for (FormBuilder.ButtonData button : form.getButtons()) {
                    if (button.getImageUrl() != null) {
                        builder.button(button.getText(), button.getImageUrl());
                    } else {
                        builder.button(button.getText());
                    }
                }
            }
            case "modal" -> {
                builder.modal().title(form.getTitle());
                if (form.getContent() != null) builder.content(form.getContent());
                for (FormBuilder.ButtonData button : form.getButtons()) {
                    builder.button(button.getText());
                }
            }
            case "custom" -> {
                builder.custom().title(form.getTitle());
                for (FormBuilder.FormElement element : form.getElements()) {
                    switch (element.getType()) {
                        case "label" -> builder.label(element.getText());
                        case "input" -> builder.input(element.getId(), element.getLabel(), 
                                element.getPlaceholder(), element.getDefaultValue());
                        case "toggle" -> builder.toggle(element.getId(), element.getLabel(), 
                                element.getDefaultBoolean());
                        case "slider" -> builder.slider(element.getId(), element.getLabel(),
                                element.getMin(), element.getMax(), element.getStep(), element.getDefaultFloat());
                        case "dropdown" -> builder.dropdown(element.getId(), element.getLabel(), 
                                element.getOptions());
                        case "stepSlider" -> builder.stepSlider(element.getId(), element.getLabel(), 
                                element.getOptions());
                    }
                }
            }
        }
        
        return builder.build();
    }

    @Override
    public SimpleMenuBuilder createSimpleMenu(String title, UUID targetPlayer) {
        return new SpigotSimpleMenuBuilder(this, title, targetPlayer);
    }

    @Override
    public ModalMenuBuilder createModalMenu(String title, UUID targetPlayer) {
        return new SpigotModalMenuBuilder(this, title, targetPlayer);
    }

    @Override
    public CustomMenuBuilder createCustomMenu(String title, UUID targetPlayer) {
        return new SpigotCustomMenuBuilder(this, title, targetPlayer);
    }

    @Override
    public void sendMenu(MenuData menuData, Consumer<MenuResponse> callback) {
        if (!isConnected()) {
            plugin.getLogger().warning("Cannot send menu - not connected to GeyserMenu extension");
            return;
        }

        final java.util.UUID target = menuData.getTargetPlayer();
        plugin.getMenuClient().sendMenu(menuData, response -> {
            // Arrives on a Netty IO thread. Hop onto the target player's region thread so the
            // caller's handler may safely touch that player; fall back to the global region
            // thread when the player is no longer online (Folia has no single "main thread").
            java.util.UUID owner = response.getPlayerUuid() != null ? response.getPlayerUuid() : target;
            FoliaScheduler.runForPlayer(plugin, owner,
                    bukkitPlayer -> callback.accept(response),
                    () -> callback.accept(response));
        });
    }

    // ---- Builder Implementations ----

    private static class SpigotSimpleMenuBuilder implements SimpleMenuBuilder {
        private final SpigotGeyserMenuAPI api;
        private final MenuData.Builder builder;

        SpigotSimpleMenuBuilder(SpigotGeyserMenuAPI api, String title, UUID targetPlayer) {
            this.api = api;
            this.builder = MenuData.builder()
                    .simple()
                    .title(title)
                    .target(targetPlayer);
        }

        @Override
        public SimpleMenuBuilder content(String content) {
            builder.content(content);
            return this;
        }

        @Override
        public SimpleMenuBuilder button(String text) {
            builder.button(text);
            return this;
        }

        @Override
        public SimpleMenuBuilder button(String text, String imageUrl) {
            builder.button(text, imageUrl);
            return this;
        }

        @Override
        public void send(Consumer<MenuResponse> callback) {
            api.sendMenu(builder.build(), callback);
        }
    }

    private static class SpigotModalMenuBuilder implements ModalMenuBuilder {
        private final SpigotGeyserMenuAPI api;
        private final MenuData.Builder builder;

        SpigotModalMenuBuilder(SpigotGeyserMenuAPI api, String title, UUID targetPlayer) {
            this.api = api;
            this.builder = MenuData.builder()
                    .modal()
                    .title(title)
                    .target(targetPlayer);
        }

        @Override
        public ModalMenuBuilder content(String content) {
            builder.content(content);
            return this;
        }

        @Override
        public ModalMenuBuilder button(String text) {
            builder.button(text);
            return this;
        }

        @Override
        public void send(Consumer<MenuResponse> callback) {
            api.sendMenu(builder.build(), callback);
        }
    }

    private static class SpigotCustomMenuBuilder implements CustomMenuBuilder {
        private final SpigotGeyserMenuAPI api;
        private final MenuData.Builder builder;

        SpigotCustomMenuBuilder(SpigotGeyserMenuAPI api, String title, UUID targetPlayer) {
            this.api = api;
            this.builder = MenuData.builder()
                    .custom()
                    .title(title)
                    .target(targetPlayer);
        }

        @Override
        public CustomMenuBuilder label(String text) {
            builder.label(text);
            return this;
        }

        @Override
        public CustomMenuBuilder input(String id, String label, String placeholder, String defaultValue) {
            builder.input(id, label, placeholder, defaultValue);
            return this;
        }

        @Override
        public CustomMenuBuilder toggle(String id, String label, boolean defaultValue) {
            builder.toggle(id, label, defaultValue);
            return this;
        }

        @Override
        public CustomMenuBuilder slider(String id, String label, float min, float max, float step, float defaultValue) {
            builder.slider(id, label, min, max, step, defaultValue);
            return this;
        }

        @Override
        public CustomMenuBuilder dropdown(String id, String label, List<String> options) {
            builder.dropdown(id, label, options);
            return this;
        }

        @Override
        public CustomMenuBuilder stepSlider(String id, String label, List<String> options) {
            builder.stepSlider(id, label, options);
            return this;
        }

        @Override
        public void send(Consumer<MenuResponse> callback) {
            api.sendMenu(builder.build(), callback);
        }
    }
}

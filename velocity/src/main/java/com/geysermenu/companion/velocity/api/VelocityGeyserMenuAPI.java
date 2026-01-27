package com.geysermenu.companion.velocity.api;

import com.geysermenu.companion.api.BedrockPlayer;
import com.geysermenu.companion.api.FormBuilder;
import com.geysermenu.companion.api.GeyserMenuAPI;
import com.geysermenu.companion.api.MenuButton;
import com.geysermenu.companion.menu.MenuData;
import com.geysermenu.companion.menu.MenuResponse;
import com.geysermenu.companion.velocity.GeyserMenuVelocity;
import com.velocitypowered.api.proxy.Player;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Velocity implementation of the GeyserMenu API
 */
public class VelocityGeyserMenuAPI extends GeyserMenuAPI {

    private final GeyserMenuVelocity plugin;
    private final Map<String, MenuButton> registeredButtons = new ConcurrentHashMap<>();
    private final List<Consumer<BedrockPlayer>> joinListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<BedrockPlayer>> leaveListeners = new CopyOnWriteArrayList<>();

    public VelocityGeyserMenuAPI(GeyserMenuVelocity plugin) {
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
            plugin.getLogger().warn("Cannot register button with null ID");
            return;
        }
        registeredButtons.put(button.getId(), button);
        plugin.getLogger().info("Registered menu button: " + button.getId());
    }

    @Override
    public void unregisterButton(String buttonId) {
        if (buttonId == null) return;
        MenuButton removed = registeredButtons.remove(buttonId);
        if (removed != null) {
            plugin.getLogger().info("Unregistered menu button: " + buttonId);
        }
    }

    @Override
    public List<MenuButton> getRegisteredButtons() {
        return new ArrayList<>(registeredButtons.values());
    }

    /**
     * Get buttons visible to a specific player
     */
    public List<MenuButton> getButtonsForPlayer(BedrockPlayer player) {
        List<MenuButton> visible = new ArrayList<>();
        for (MenuButton button : registeredButtons.values()) {
            if (button.getCondition() == null || button.getCondition().test(player)) {
                visible.add(button);
            }
        }
        visible.sort((a, b) -> Integer.compare(a.getPriority(), b.getPriority()));
        return visible;
    }

    /**
     * Handle a button click from the extension
     */
    public void handleButtonClick(String buttonId, BedrockPlayer player, Object session) {
        MenuButton button = registeredButtons.get(buttonId);
        if (button == null) {
            plugin.getLogger().warn("Unknown button clicked: " + buttonId);
            return;
        }

        // Execute command if set
        if (button.getCommand() != null && !button.getCommand().isEmpty()) {
            plugin.getServer().getPlayer(player.getUuid()).ifPresent(velocityPlayer -> {
                plugin.getServer().getCommandManager().executeAsync(velocityPlayer, button.getCommand());
            });
        }

        // Execute onClick handler if set
        if (button.getOnClick() != null) {
            plugin.getServer().getScheduler().buildTask(plugin, () -> {
                button.getOnClick().accept(player, session);
            }).schedule();
        }
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

    @Override
    public List<BedrockPlayer> getOnlineBedrockPlayers() {
        List<BedrockPlayer> players = new ArrayList<>();
        for (Player player : plugin.getServer().getAllPlayers()) {
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
                players.add(new BedrockPlayer(player.getUniqueId(), xuid, player.getUsername()));
            }
        }
        return players;
    }

    /**
     * Called when a Bedrock player joins
     */
    public void notifyPlayerJoin(BedrockPlayer player) {
        for (Consumer<BedrockPlayer> listener : joinListeners) {
            try {
                listener.accept(player);
            } catch (Exception e) {
                plugin.getLogger().warn("Error in player join listener: " + e.getMessage());
            }
        }
    }

    /**
     * Called when a Bedrock player leaves
     */
    public void notifyPlayerLeave(BedrockPlayer player) {
        for (Consumer<BedrockPlayer> listener : leaveListeners) {
            try {
                listener.accept(player);
            } catch (Exception e) {
                plugin.getLogger().warn("Error in player leave listener: " + e.getMessage());
            }
        }
    }

    // ==================== Form Sending ====================

    @Override
    public void sendForm(UUID playerUuid, FormBuilder.Form form) {
        if (!isConnected()) {
            plugin.getLogger().warn("Cannot send form - not connected to GeyserMenu extension");
            return;
        }

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
        return new VelocitySimpleMenuBuilder(this, title, targetPlayer);
    }

    @Override
    public ModalMenuBuilder createModalMenu(String title, UUID targetPlayer) {
        return new VelocityModalMenuBuilder(this, title, targetPlayer);
    }

    @Override
    public CustomMenuBuilder createCustomMenu(String title, UUID targetPlayer) {
        return new VelocityCustomMenuBuilder(this, title, targetPlayer);
    }

    @Override
    public void sendMenu(MenuData menuData, Consumer<MenuResponse> callback) {
        if (!isConnected()) {
            plugin.getLogger().warn("Cannot send menu - not connected to GeyserMenu extension");
            return;
        }

        plugin.getMenuClient().sendMenu(menuData, response -> {
            // Run callback on the server thread
            plugin.getServer().getScheduler().buildTask(plugin, () -> callback.accept(response)).schedule();
        });
    }

    // ---- Builder Implementations ----

    private static class VelocitySimpleMenuBuilder implements SimpleMenuBuilder {
        private final VelocityGeyserMenuAPI api;
        private final MenuData.Builder builder;

        VelocitySimpleMenuBuilder(VelocityGeyserMenuAPI api, String title, UUID targetPlayer) {
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

    private static class VelocityModalMenuBuilder implements ModalMenuBuilder {
        private final VelocityGeyserMenuAPI api;
        private final MenuData.Builder builder;

        VelocityModalMenuBuilder(VelocityGeyserMenuAPI api, String title, UUID targetPlayer) {
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

    private static class VelocityCustomMenuBuilder implements CustomMenuBuilder {
        private final VelocityGeyserMenuAPI api;
        private final MenuData.Builder builder;

        VelocityCustomMenuBuilder(VelocityGeyserMenuAPI api, String title, UUID targetPlayer) {
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

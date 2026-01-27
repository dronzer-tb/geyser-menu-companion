package com.geysermenu.companion.api;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Represents a button that can be registered with the GeyserMenu.
 * Buttons registered here will appear in the main menu for all (or filtered) Bedrock players.
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * MenuButton button = MenuButton.builder()
 *     .id("my-plugin-teleport")
 *     .text("Teleport Menu")
 *     .imageUrl("https://example.com/teleport.png")
 *     .priority(10)
 *     .condition(player -> player.hasPermission("myplugin.teleport"))
 *     .onClick((player, session) -> {
 *         openTeleportMenu(player);
 *     })
 *     .build();
 * 
 * GeyserMenuAPI.getInstance().registerButton(button);
 * }</pre>
 */
public class MenuButton {
    
    private final String id;
    private final String text;
    private final String imageUrl;
    private final String imagePath;
    private final int priority;
    private final Predicate<Object> condition;  // Object = Player in runtime
    private final BiConsumer<Object, Object> onClick;  // Object = Player, Session in runtime
    private final String command;
    
    private MenuButton(Builder builder) {
        this.id = builder.id;
        this.text = builder.text;
        this.imageUrl = builder.imageUrl;
        this.imagePath = builder.imagePath;
        this.priority = builder.priority;
        this.condition = builder.condition;
        this.onClick = builder.onClick;
        this.command = builder.command;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public String getId() {
        return id;
    }
    
    public String getText() {
        return text;
    }
    
    public String getImageUrl() {
        return imageUrl;
    }
    
    public String getImagePath() {
        return imagePath;
    }
    
    public int getPriority() {
        return priority;
    }
    
    public Predicate<Object> getCondition() {
        return condition;
    }
    
    public BiConsumer<Object, Object> getOnClick() {
        return onClick;
    }
    
    public String getCommand() {
        return command;
    }
    
    public boolean hasImage() {
        return imageUrl != null || imagePath != null;
    }
    
    public boolean isUrlImage() {
        return imageUrl != null;
    }
    
    /**
     * Builder for MenuButton
     */
    public static class Builder {
        private String id;
        private String text;
        private String imageUrl;
        private String imagePath;
        private int priority = 100;
        private Predicate<Object> condition = player -> true;
        private BiConsumer<Object, Object> onClick;
        private String command;
        
        /**
         * Set the unique identifier for this button.
         * This is used to unregister or update the button later.
         * 
         * @param id Unique button ID (e.g., "my-plugin-main-button")
         * @return this builder
         */
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        /**
         * Set the button text displayed to players.
         * 
         * @param text Button label
         * @return this builder
         */
        public Builder text(String text) {
            this.text = text;
            return this;
        }
        
        /**
         * Set an image URL for the button icon.
         * The image will be loaded from the internet.
         * 
         * @param imageUrl URL to the image (PNG recommended)
         * @return this builder
         */
        public Builder imageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
            this.imagePath = null;
            return this;
        }
        
        /**
         * Set a resource pack path for the button icon.
         * The image will be loaded from the player's resource pack.
         * 
         * @param imagePath Path like "textures/items/diamond"
         * @return this builder
         */
        public Builder imagePath(String imagePath) {
            this.imagePath = imagePath;
            this.imageUrl = null;
            return this;
        }
        
        /**
         * Set the priority (sort order) for this button.
         * Lower values appear first in the menu.
         * Default is 100.
         * 
         * @param priority Sort priority (lower = higher in list)
         * @return this builder
         */
        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }
        
        /**
         * Set a condition for when this button should be visible.
         * The predicate receives the Player object.
         * 
         * @param condition Predicate that returns true if button should be shown
         * @return this builder
         */
        public Builder condition(Predicate<Object> condition) {
            this.condition = condition;
            return this;
        }
        
        /**
         * Set the click handler for this button.
         * The consumer receives (Player, GeyserSession).
         * 
         * @param onClick Click handler
         * @return this builder
         */
        public Builder onClick(BiConsumer<Object, Object> onClick) {
            this.onClick = onClick;
            return this;
        }
        
        /**
         * Set a command to execute when clicked (alternative to onClick).
         * The command is run as the player (without leading /).
         * 
         * @param command Command to execute
         * @return this builder
         */
        public Builder command(String command) {
            this.command = command;
            return this;
        }
        
        /**
         * Build the MenuButton.
         * 
         * @return The built MenuButton
         * @throws IllegalStateException if id or text is not set
         */
        public MenuButton build() {
            if (id == null || id.isEmpty()) {
                throw new IllegalStateException("Button id is required");
            }
            if (text == null || text.isEmpty()) {
                throw new IllegalStateException("Button text is required");
            }
            if (onClick == null && command == null) {
                throw new IllegalStateException("Either onClick or command must be set");
            }
            return new MenuButton(this);
        }
    }
}

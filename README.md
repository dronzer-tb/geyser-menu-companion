# GeyserMenu Companion

[![Modrinth](https://img.shields.io/modrinth/v/geysermenu-companion?logo=modrinth&label=Modrinth)](https://modrinth.com/plugin/geysermenu-companion)
[![GitHub Release](https://img.shields.io/github/v/release/yourusername/geyser-menu-companion)](https://github.com/yourusername/geyser-menu-companion/releases)
[![License](https://img.shields.io/github/license/yourusername/geyser-menu-companion)](LICENSE)

A multi-platform companion plugin for [GeyserMenu](https://github.com/yourusername/geyser-menu) that provides a server-side API for registering menu buttons and handling player interactions.

## Features

- **Button Registration API**: Register custom buttons to appear in GeyserMenu
- **Form Builder API**: Create and send custom forms to Bedrock players
- **Player Events**: Listen for Bedrock player join/leave events
- **Multi-Platform**: Supports Spigot/Paper and Velocity
- **Auto-Reconnect**: Automatically reconnects to GeyserMenu extension
- **Permission-Based Buttons**: Show/hide buttons based on player permissions

## Requirements

- Spigot/Paper 1.20.4+ or Velocity 3.3.0+
- Java 21+
- Floodgate (for Bedrock player detection)
- GeyserMenu Extension running on Geyser

## Installation

1. Download the appropriate JAR for your platform from [Modrinth](https://modrinth.com/plugin/geysermenu-companion) or [GitHub Releases](https://github.com/yourusername/geyser-menu-companion/releases)
2. Place in your `plugins/` folder
3. Configure `config.yml` with your GeyserMenu extension details
4. Restart your server

## Configuration

```yaml
# config.yml
extension:
  host: "localhost"
  port: 19133
  secret-key: "your-secret-key-here"

connection:
  auto-reconnect: true
  reconnect-delay: 5

server:
  identifier: "lobby"
```

---

# API Documentation

## Getting Started

Add the companion as a dependency (or shade the common module):

```java
// Get the API instance
GeyserMenuAPI api = GeyserMenuAPI.getInstance();
```

---

## Registering Buttons

Buttons registered with the API will appear in the main GeyserMenu for all Bedrock players.

### Basic Button

```java
import com.geysermenu.companion.api.GeyserMenuAPI;
import com.geysermenu.companion.api.MenuButton;

MenuButton button = MenuButton.builder()
    .id("my-plugin-main")           // Unique identifier
    .text("My Plugin")              // Button label
    .onClick((player, session) -> {
        player.sendMessage("Hello from my plugin!");
    })
    .build();

GeyserMenuAPI.getInstance().registerButton(button);
```

### Button with Icon

```java
MenuButton button = MenuButton.builder()
    .id("my-plugin-shop")
    .text("Shop")
    .imageUrl("https://example.com/shop-icon.png")  // URL to image
    .onClick((player, session) -> {
        openShopMenu(player);
    })
    .build();
```

Or use a resource pack path:

```java
MenuButton button = MenuButton.builder()
    .id("my-plugin-shop")
    .text("Shop")
    .imagePath("textures/items/emerald")  // Resource pack path
    .onClick((player, session) -> {
        openShopMenu(player);
    })
    .build();
```

### Button with Priority

Lower priority values appear first in the menu:

```java
MenuButton button = MenuButton.builder()
    .id("my-plugin-important")
    .text("Important Button")
    .priority(1)  // Will appear near the top (default is 100)
    .onClick((player, session) -> { /* ... */ })
    .build();
```

### Conditional Visibility (Permissions)

Show buttons only to players with specific permissions:

```java
MenuButton button = MenuButton.builder()
    .id("my-plugin-admin")
    .text("Admin Panel")
    .condition(player -> {
        // Cast to your platform's Player type
        return ((Player) player).hasPermission("myplugin.admin");
    })
    .onClick((player, session) -> {
        openAdminPanel((Player) player);
    })
    .build();
```

### Button with Command

Execute a command instead of using onClick:

```java
MenuButton button = MenuButton.builder()
    .id("my-plugin-spawn")
    .text("Teleport to Spawn")
    .command("spawn")  // Executed as the player (without /)
    .build();
```

### Unregistering a Button

```java
GeyserMenuAPI.getInstance().unregisterButton("my-plugin-main");
```

---

## MenuButton Builder Reference

| Method | Description | Required |
|--------|-------------|----------|
| `.id(String)` | Unique button identifier | ✅ Yes |
| `.text(String)` | Button label text | ✅ Yes |
| `.onClick(BiConsumer)` | Click handler (Player, Session) | ⚠️ One of onClick/command |
| `.command(String)` | Command to execute on click | ⚠️ One of onClick/command |
| `.imageUrl(String)` | URL to button icon | ❌ No |
| `.imagePath(String)` | Resource pack path to icon | ❌ No |
| `.priority(int)` | Sort order (lower = first) | ❌ No (default: 100) |
| `.condition(Predicate)` | Visibility condition | ❌ No (default: always visible) |

---

## Creating Custom Forms

### Simple Form (Button Menu)

```java
GeyserMenuAPI api = GeyserMenuAPI.getInstance();

api.createSimpleMenu("My Menu", player.getUniqueId())
    .content("Choose an option:")
    .button("Option 1")
    .button("Option 2")
    .button("Option 3", "https://example.com/icon.png")  // With icon
    .send(response -> {
        if (!response.wasClosed()) {
            int clicked = response.getButtonId();
            String buttonText = response.getButtonText();
            player.sendMessage("You clicked: " + buttonText);
        }
    });
```

### Modal Form (Yes/No Dialog)

```java
api.createModalMenu("Confirm Action", player.getUniqueId())
    .content("Are you sure you want to continue?")
    .button("Yes")
    .button("No")
    .send(response -> {
        if (response.getButtonId() == 0) {
            // Yes clicked
            doAction();
        } else {
            // No clicked or closed
            cancelAction();
        }
    });
```

### Custom Form (Inputs, Toggles, Sliders)

```java
api.createCustomMenu("Settings", player.getUniqueId())
    .label("Configure your preferences:")
    .input("nickname", "Nickname", "Enter nickname...", "")
    .toggle("notifications", "Enable Notifications", true)
    .slider("volume", "Volume", 0, 100, 1, 50)
    .dropdown("difficulty", "Difficulty", List.of("Easy", "Normal", "Hard"))
    .send(response -> {
        if (!response.wasClosed()) {
            String nickname = response.getString("nickname");
            Boolean notifications = response.getBoolean("notifications");
            Number volume = response.getNumber("volume");
            int difficulty = response.getDropdown("difficulty");

            player.sendMessage("Saved settings!");
        }
    });
```

---

## FormBuilder API

For more complex forms, use the FormBuilder directly:

### Simple Form

```java
import com.geysermenu.companion.api.FormBuilder;

FormBuilder.Form form = FormBuilder.simple()
    .title("My Menu")
    .content("Choose wisely:")
    .button("Option A", () -> player.sendMessage("A!"))
    .button("Option B", () -> player.sendMessage("B!"))
    .buttonWithUrl("Option C", "https://example.com/icon.png", () -> {
        player.sendMessage("C!");
    })
    .buttonCommand("Run Command", "spawn")
    .onClose(() -> player.sendMessage("Menu closed"))
    .build();

GeyserMenuAPI.getInstance().sendForm(player.getUniqueId(), form);
```

### Modal Form

```java
FormBuilder.Form form = FormBuilder.modal()
    .title("Confirm")
    .content("Delete all items?")
    .button1("Yes, delete")
    .button2("No, cancel")
    .onConfirm(() -> deleteItems())
    .onDeny(() -> player.sendMessage("Cancelled"))
    .build();

api.sendForm(player.getUniqueId(), form);
```

### Custom Form

```java
FormBuilder.Form form = FormBuilder.custom()
    .title("Player Report")
    .label("Report a player for rule violations")
    .input("player_name", "Player Name", "Enter username...")
    .dropdown("reason", "Reason", List.of("Cheating", "Harassment", "Spam", "Other"))
    .input("details", "Details", "Describe what happened...")
    .toggle("evidence", "I have evidence", false)
    .onResponse(response -> {
        String playerName = response.getString("player_name");
        int reason = response.getDropdown("reason");
        // Process report...
    })
    .build();

api.sendForm(player.getUniqueId(), form);
```

---

## Player Events

### Listen for Bedrock Players

```java
GeyserMenuAPI api = GeyserMenuAPI.getInstance();

api.onPlayerJoin(bedrockPlayer -> {
    System.out.println("Bedrock player joined: " + bedrockPlayer.getName());
    System.out.println("XUID: " + bedrockPlayer.getXuid());
});

api.onPlayerLeave(bedrockPlayer -> {
    System.out.println("Bedrock player left: " + bedrockPlayer.getName());
});
```

### Check if Player is Bedrock

```java
if (api.isBedrockPlayer(player.getUniqueId())) {
    // This is a Bedrock player
    showBedrockMenu(player);
} else {
    // This is a Java player
    showJavaMenu(player);
}
```

### Get All Online Bedrock Players

```java
List<BedrockPlayer> bedrockPlayers = api.getOnlineBedrockPlayers();
for (BedrockPlayer bp : bedrockPlayers) {
    System.out.println(bp.getName() + " - " + bp.getXuid());
}
```

---

## Connection Status

```java
if (api.isConnected()) {
    // Connected to GeyserMenu extension
} else {
    // Not connected (buttons won't appear)
}
```

---

## Complete Plugin Example

```java
import com.geysermenu.companion.api.GeyserMenuAPI;
import com.geysermenu.companion.api.MenuButton;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class MyPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        GeyserMenuAPI api = GeyserMenuAPI.getInstance();

        if (api == null) {
            getLogger().warning("GeyserMenu Companion not found!");
            return;
        }

        // Register a main menu button
        api.registerButton(MenuButton.builder()
            .id("myplugin-main")
            .text("My Plugin Menu")
            .imageUrl("https://example.com/icon.png")
            .priority(50)
            .onClick((playerObj, session) -> {
                Player player = (Player) playerObj;
                openMainMenu(player);
            })
            .build());

        // Register an admin-only button
        api.registerButton(MenuButton.builder()
            .id("myplugin-admin")
            .text("Admin Panel")
            .priority(200)
            .condition(p -> ((Player) p).hasPermission("myplugin.admin"))
            .onClick((playerObj, session) -> {
                Player player = (Player) playerObj;
                openAdminPanel(player);
            })
            .build());

        getLogger().info("Registered GeyserMenu buttons!");
    }

    @Override
    public void onDisable() {
        GeyserMenuAPI api = GeyserMenuAPI.getInstance();
        if (api != null) {
            api.unregisterButton("myplugin-main");
            api.unregisterButton("myplugin-admin");
        }
    }

    private void openMainMenu(Player player) {
        GeyserMenuAPI.getInstance()
            .createSimpleMenu("My Plugin", player.getUniqueId())
            .content("What would you like to do?")
            .button("View Stats")
            .button("Settings")
            .button("Help")
            .send(response -> {
                if (!response.wasClosed()) {
                    switch (response.getButtonId()) {
                        case 0 -> showStats(player);
                        case 1 -> openSettings(player);
                        case 2 -> showHelp(player);
                    }
                }
            });
    }

    private void openAdminPanel(Player player) { /* ... */ }
    private void showStats(Player player) { /* ... */ }
    private void openSettings(Player player) { /* ... */ }
    private void showHelp(Player player) { /* ... */ }
}
```

---

## Building

```bash
./gradlew build
```

Outputs:
- `spigot/build/libs/GeyserMenuCompanion-Spigot-1.1.4.jar`
- `velocity/build/libs/GeyserMenuCompanion-Velocity-1.1.4.jar`

## Project Structure

```
geyser-menu-companion/
├── common/          # Shared code (API, network, protocol)
├── spigot/          # Spigot/Paper implementation
├── velocity/        # Velocity implementation
└── build.gradle.kts # Root build file
```

## License

MIT License

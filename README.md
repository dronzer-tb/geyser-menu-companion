# GeyserMenu Companion

A multi-platform companion plugin for [GeyserMenu](https://github.com/yourusername/geyser-menu) that provides a server-side API for registering menu buttons and handling player interactions.

## Features

- **Button Registration API**: Register custom buttons to appear in GeyserMenu
- **Form Builder API**: Create and send custom forms to Bedrock players
- **Player Events**: Listen for Bedrock player join/leave events
- **Multi-Platform**: Supports Spigot/Paper, Velocity, and BungeeCord
- **Auto-Reconnect**: Automatically reconnects to GeyserMenu extension

## Requirements

- Spigot/Paper 1.20.4+ (or Velocity/BungeeCord)
- Java 21+
- Floodgate (for Bedrock player detection)
- GeyserMenu Extension running on Geyser

## Installation

1. Download the appropriate JAR for your platform
2. Place in your `plugins/` folder
3. Configure `config.yml` with your GeyserMenu extension details
4. Restart your server

## Configuration

```yaml
# config.yml
extension:
  host: "localhost"
  port: 19135
  secret-key: "your-secret-key-here"
  
connection:
  auto-reconnect: true
  reconnect-delay: 5
  enable-ssl: false

server:
  identifier: "lobby"
```

## API Usage

### Registering a Button

```java
import com.geysermenu.companion.api.GeyserMenuAPI;
import com.geysermenu.companion.api.MenuButton;

// Create a button
MenuButton myButton = new MenuButton("my-plugin-button")
    .text("My Button")
    .imageUrl("https://example.com/icon.png")
    .priority(10)
    .onClick((player, session) -> {
        // Handle click
        player.sendMessage("Button clicked!");
    });

// Register it
GeyserMenuAPI.get().registerButton(myButton);
```

### Sending a Custom Form

```java
GeyserMenuAPI api = GeyserMenuAPI.get();

api.createSimpleMenu("My Menu", playerUuid)
    .content("Choose an option:")
    .button("Option 1")
    .button("Option 2")
    .send(response -> {
        if (response.isSuccess()) {
            int clicked = response.getClickedButton();
            // Handle response
        }
    });
```

### Listening for Player Events

```java
GeyserMenuAPI.get().onPlayerJoin(player -> {
    System.out.println("Bedrock player joined: " + player.getName());
});
```

## Building

```bash
./gradlew build
```

Outputs:
- `spigot/build/libs/spigot-1.0.0-SNAPSHOT.jar` (Spigot/Paper)
- `velocity/build/libs/velocity-1.0.0-SNAPSHOT.jar` (Velocity)
- `bungee/build/libs/bungee-1.0.0-SNAPSHOT.jar` (BungeeCord)

## Project Structure

```
geyser-menu-companion/
├── common/          # Shared code (API, network, protocol)
├── spigot/          # Spigot/Paper implementation
├── velocity/        # Velocity implementation  
├── bungee/          # BungeeCord implementation
└── build.gradle.kts # Root build file
```

## License

MIT License

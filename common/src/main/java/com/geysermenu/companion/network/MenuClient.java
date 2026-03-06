package com.geysermenu.companion.network;

import com.geysermenu.companion.menu.MenuData;
import com.geysermenu.companion.menu.MenuResponse;
import com.geysermenu.companion.protocol.AuthData;
import com.geysermenu.companion.protocol.ButtonData;
import com.geysermenu.companion.protocol.Packet;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.CharsetUtil;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * TCP Client for connecting to the GeyserMenu extension
 */
public class MenuClient {

    private static final Gson GSON = new GsonBuilder().create();

    private final String host;
    private final int port;
    private final String secretKey;
    private final String serverIdentifier;
    private final Logger logger;
    private final boolean enableSsl;

    private EventLoopGroup workerGroup;
    private Channel channel;
    private SslContext sslContext;

    private boolean connected = false;
    private boolean authenticated = false;

    private final Map<String, Consumer<MenuResponse>> responseCallbacks = new ConcurrentHashMap<>();
    private Consumer<PlayerEvent> playerJoinListener;
    private Consumer<PlayerEvent> playerLeaveListener;
    private Consumer<String> errorListener;
    private Runnable connectionLostListener;
    private Runnable authSuccessListener;

    private ScheduledExecutorService reconnectExecutor;
    private boolean autoReconnect = true;
    private int reconnectDelaySeconds = 5;

    public MenuClient(String host, int port, String secretKey, String serverIdentifier, Logger logger, boolean enableSsl) {
        this.host = host;
        this.port = port;
        this.secretKey = secretKey;
        this.serverIdentifier = serverIdentifier;
        this.logger = logger;
        this.enableSsl = enableSsl;
    }

    /**
     * Constructor with SSL disabled by default for compatibility
     */
    public MenuClient(String host, int port, String secretKey, String serverIdentifier, Logger logger) {
        this(host, port, secretKey, serverIdentifier, logger, false);
    }

    /**
     * Connect to the GeyserMenu extension
     */
    public CompletableFuture<Boolean> connect() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        try {
            // Trust all certificates (for self-signed cert on extension) if SSL enabled
            if (enableSsl) {
                sslContext = SslContextBuilder.forClient()
                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                        .build();
            }

            workerGroup = new NioEventLoopGroup();

            final boolean useSsl = enableSsl;
            final SslContext finalSslContext = sslContext;

            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(workerGroup)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();

                            if (useSsl && finalSslContext != null) {
                                pipeline.addLast(finalSslContext.newHandler(ch.alloc(), host, port));
                            }
                            pipeline.addLast(new LengthFieldBasedFrameDecoder(1048576, 0, 4, 0, 4));
                            pipeline.addLast(new LengthFieldPrepender(4));
                            pipeline.addLast(new StringDecoder(CharsetUtil.UTF_8));
                            pipeline.addLast(new StringEncoder(CharsetUtil.UTF_8));
                            pipeline.addLast(new ClientPacketHandler(MenuClient.this, future));
                        }
                    });

            ChannelFuture channelFuture = bootstrap.connect(host, port);
            channelFuture.addListener((ChannelFutureListener) cf -> {
                if (cf.isSuccess()) {
                    channel = cf.channel();
                    connected = true;
                    logger.info("Connected to GeyserMenu at " + host + ":" + port);

                    // Send authentication
                    authenticate();
                } else {
                    logger.warning("Failed to connect to GeyserMenu: " + cf.cause().getMessage());
                    future.complete(false);
                    scheduleReconnect();
                }
            });

        } catch (Exception e) {
            logger.severe("Error connecting to GeyserMenu: " + e.getMessage());
            future.complete(false);
        }

        return future;
    }

    private void authenticate() {
        AuthData authData = AuthData.request(secretKey, serverIdentifier);
        Packet packet = new Packet(Packet.PacketType.AUTH_REQUEST, GSON.toJson(authData));
        sendPacket(packet);
    }

    /**
     * Disconnect from the extension
     */
    public void disconnect() {
        autoReconnect = false;
        connected = false;
        authenticated = false;

        if (reconnectExecutor != null) {
            reconnectExecutor.shutdown();
            try {
                reconnectExecutor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {}
        }

        if (channel != null && channel.isActive()) {
            try {
                channel.close().sync();
            } catch (InterruptedException ignored) {}
        }

        if (workerGroup != null) {
            try {
                workerGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).sync();
            } catch (InterruptedException ignored) {}
        }

        logger.info("Disconnected from GeyserMenu");
    }

    /**
     * Send a menu to a player
     */
    public void sendMenu(MenuData menuData, Consumer<MenuResponse> callback) {
        if (!authenticated) {
            logger.warning("Cannot send menu - not authenticated");
            return;
        }

        // Store callback for response
        responseCallbacks.put(menuData.getFormId(), callback);

        Packet packet = new Packet(Packet.PacketType.SEND_MENU, GSON.toJson(menuData));
        sendPacket(packet);
    }

    /**
     * Request the list of online Bedrock players
     */
    public void requestPlayerList(Consumer<java.util.List<Map<String, String>>> callback) {
        if (!authenticated) {
            logger.warning("Cannot request player list - not authenticated");
            return;
        }

        // This is a simplified implementation - you might want to add proper future handling
        Packet packet = new Packet(Packet.PacketType.PLAYER_LIST, "");
        sendPacket(packet);
    }

    void handlePacket(Packet packet) {
        switch (packet.getType()) {
            case AUTH_RESPONSE -> handleAuthResponse(packet);
            case MENU_RESPONSE -> handleMenuResponse(packet);
            case PLAYER_JOIN -> handlePlayerJoin(packet);
            case PLAYER_LEAVE -> handlePlayerLeave(packet);
            case ERROR -> handleError(packet);
            case BUTTON_CLICKED -> handleButtonClick(packet);
            case PONG -> {} // Keepalive response
        }
    }

    private void handleAuthResponse(Packet packet) {
        AuthData authData = GSON.fromJson(packet.getPayload(), AuthData.class);
        if (authData.isSuccess()) {
            authenticated = true;
            logger.info("Authentication successful: " + authData.getMessage());
            // Notify listener that authentication completed
            if (authSuccessListener != null) {
                authSuccessListener.run();
            }
        } else {
            authenticated = false;
            logger.warning("Authentication failed: " + authData.getMessage());
            disconnect();
        }
    }

    private void handleMenuResponse(Packet packet) {
        MenuResponse response = GSON.fromJson(packet.getPayload(), MenuResponse.class);

        Consumer<MenuResponse> callback = responseCallbacks.remove(response.getFormId());
        if (callback != null) {
            callback.accept(response);
        }
    }

    private void handlePlayerJoin(Packet packet) {
        if (playerJoinListener != null) {
            @SuppressWarnings("unchecked")
            Map<String, String> data = GSON.fromJson(packet.getPayload(), Map.class);
            PlayerEvent event = new PlayerEvent(
                    UUID.fromString(data.get("uuid")),
                    data.get("name"),
                    data.get("xuid")
            );
            playerJoinListener.accept(event);
        }
    }

    private void handlePlayerLeave(Packet packet) {
        if (playerLeaveListener != null) {
            @SuppressWarnings("unchecked")
            Map<String, String> data = GSON.fromJson(packet.getPayload(), Map.class);
            PlayerEvent event = new PlayerEvent(
                    UUID.fromString(data.get("uuid")),
                    null,
                    null
            );
            playerLeaveListener.accept(event);
        }
    }

    private void handleError(Packet packet) {
        logger.warning("GeyserMenu error: " + packet.getPayload());
        if (errorListener != null) {
            errorListener.accept(packet.getPayload());
        }
    }

    private void handleButtonClick(Packet packet) {
        ButtonData.ButtonClick click = GSON.fromJson(packet.getPayload(), ButtonData.ButtonClick.class);
        if (buttonClickListener != null) {
            buttonClickListener.accept(click);
        }
    }

    void onConnectionLost() {
        connected = false;
        authenticated = false;
        logger.warning("Connection to GeyserMenu lost");

        if (connectionLostListener != null) {
            connectionLostListener.run();
        }

        scheduleReconnect();
    }

    private void scheduleReconnect() {
        if (!autoReconnect) return;

        if (reconnectExecutor == null || reconnectExecutor.isShutdown()) {
            reconnectExecutor = Executors.newSingleThreadScheduledExecutor();
        }

        logger.info("Scheduling reconnect in " + reconnectDelaySeconds + " seconds...");
        reconnectExecutor.schedule(this::connect, reconnectDelaySeconds, TimeUnit.SECONDS);
    }

    private void sendPacket(Packet packet) {
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(GSON.toJson(packet));
        }
    }

    // ==================== Button Registration ====================
    
    private Consumer<ButtonData.ButtonClick> buttonClickListener;
    
    /**
     * Send registered buttons to the GeyserMenu extension.
     * Should be called after buttons are registered/unregistered.
     */
    public void sendButtons(List<ButtonData> buttons) {
        if (!authenticated) {
            logger.warning("Cannot send buttons - not authenticated");
            return;
        }
        
        ButtonData.ButtonList buttonList = new ButtonData.ButtonList(buttons);
        Packet packet = new Packet(Packet.PacketType.REGISTER_BUTTONS, GSON.toJson(buttonList));
        sendPacket(packet);
        logger.info("Sent " + buttons.size() + " buttons to GeyserMenu extension");
    }
    
    /**
     * Set listener for button click events from the extension.
     */
    public void onButtonClick(Consumer<ButtonData.ButtonClick> listener) {
        this.buttonClickListener = listener;
    }
    
    /**
     * Get the button click listener.
     */
    public Consumer<ButtonData.ButtonClick> getButtonClickListener() {
        return buttonClickListener;
    }
    
    /**
     * Request the GeyserMenu extension to open the main menu for a player.
     * This is used as a fallback when double-click inventory detection fails.
     * 
     * @param playerUuid The UUID of the player to open the menu for
     */
    public void requestOpenMainMenu(UUID playerUuid) {
        if (!authenticated) {
            logger.warning("Cannot request open menu - not authenticated");
            return;
        }
        
        Map<String, String> data = new java.util.HashMap<>();
        data.put("playerUuid", playerUuid.toString());
        
        Packet packet = new Packet(Packet.PacketType.OPEN_MAIN_MENU, GSON.toJson(data));
        sendPacket(packet);
        logger.info("Requested main menu open for player: " + playerUuid);
    }
    
    /**
     * Request the GeyserMenu extension to reorder a button to a specific position.
     * 
     * @param buttonName The name or ID of the button to reorder
     * @param position The desired position (1-based, 1 = first)
     */
    public void requestReorderButton(String buttonName, int position) {
        if (!authenticated) {
            logger.warning("Cannot reorder button - not authenticated");
            return;
        }
        
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("buttonName", buttonName);
        data.put("position", position);
        
        Packet packet = new Packet(Packet.PacketType.REORDER_BUTTON, GSON.toJson(data));
        sendPacket(packet);
        logger.info("Requested button reorder: " + buttonName + " -> position " + position);
    }

    // Listeners
    public void onPlayerJoin(Consumer<PlayerEvent> listener) {
        this.playerJoinListener = listener;
    }

    public void onPlayerLeave(Consumer<PlayerEvent> listener) {
        this.playerLeaveListener = listener;
    }

    public void onError(Consumer<String> listener) {
        this.errorListener = listener;
    }

    public void onConnectionLost(Runnable listener) {
        this.connectionLostListener = listener;
    }

    public void onAuthSuccess(Runnable listener) {
        this.authSuccessListener = listener;
    }

    public void setAutoReconnect(boolean autoReconnect) {
        this.autoReconnect = autoReconnect;
    }

    public void setReconnectDelaySeconds(int seconds) {
        this.reconnectDelaySeconds = seconds;
    }

    public boolean isConnected() {
        return connected;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public record PlayerEvent(UUID uuid, String name, String xuid) {}
}

// PATCH: Add this import at the top

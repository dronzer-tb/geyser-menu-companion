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
import io.netty.util.concurrent.EventExecutor;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
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

    /** Pending menu callbacks are dropped after this long so an unanswered form cannot leak. */
    private static final long CALLBACK_TTL_MILLIS = TimeUnit.MINUTES.toMillis(5);

    // Everything below is written on Netty IO threads and read from server threads (on Folia,
    // from several region threads concurrently), so every cross-thread field is volatile and
    // every collection is concurrent.
    private volatile EventLoopGroup workerGroup;
    private volatile Channel channel;
    private volatile SslContext sslContext;

    private volatile boolean connected = false;
    private volatile boolean authenticated = false;
    private volatile boolean shuttingDown = false;

    private final Map<String, PendingCallback> responseCallbacks = new ConcurrentHashMap<>();
    private volatile Consumer<PlayerEvent> playerJoinListener;
    private volatile Consumer<PlayerEvent> playerLeaveListener;
    private volatile Consumer<String> errorListener;
    private volatile Runnable connectionLostListener;
    private volatile Runnable authSuccessListener;

    private volatile ScheduledExecutorService reconnectExecutor;
    private final AtomicBoolean reconnectPending = new AtomicBoolean(false);
    private volatile boolean autoReconnect = true;
    private volatile int reconnectDelaySeconds = 5;

    private record PendingCallback(Consumer<MenuResponse> callback, long createdAtMillis) {}

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

        if (shuttingDown) {
            future.complete(false);
            return future;
        }

        try {
            // Trust all certificates (for self-signed cert on extension) if SSL enabled
            if (enableSsl && sslContext == null) {
                sslContext = SslContextBuilder.forClient()
                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                        .build();
            }

            // Reuse one event loop group for the life of this client. The previous code allocated
            // a fresh NioEventLoopGroup on *every* reconnect attempt and never shut the old one
            // down, so a flapping connection leaked 2*cores threads every reconnect-delay seconds.
            // One IO thread is plenty for a single client socket, and on Folia every extra idle
            // thread competes with the region threads for cores.
            EventLoopGroup group = workerGroup;
            if (group == null || group.isShuttingDown() || group.isShutdown() || group.isTerminated()) {
                group = new NioEventLoopGroup(1, runnable -> {
                    Thread thread = new Thread(runnable, "GeyserMenu-Netty");
                    thread.setDaemon(true);
                    return thread;
                });
                workerGroup = group;
            }

            final boolean useSsl = enableSsl;
            final SslContext finalSslContext = sslContext;

            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
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
                    logger.warning("Failed to connect to GeyserMenu: "
                            + (cf.cause() == null ? "unknown error" : cf.cause().getMessage()));
                    connected = false;
                    authenticated = false;
                    future.complete(false);
                    scheduleReconnect();
                }
            });

        } catch (Exception e) {
            logger.severe("Error connecting to GeyserMenu: " + e.getMessage());
            future.complete(false);
            scheduleReconnect();
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
        shuttingDown = true;
        autoReconnect = false;
        connected = false;
        authenticated = false;
        responseCallbacks.clear();

        ScheduledExecutorService executor = this.reconnectExecutor;
        this.reconnectExecutor = null;
        reconnectPending.set(false);
        if (executor != null) {
            executor.shutdownNow();
        }

        Channel currentChannel = this.channel;
        EventLoopGroup group = this.workerGroup;
        this.channel = null;
        this.workerGroup = null;

        // handleAuthResponse() calls disconnect() straight off the Netty IO thread. Blocking that
        // thread on its own channel close / group shutdown throws BlockingOperationException (or
        // deadlocks), so only wait when we are NOT on a Netty thread.
        boolean onNettyThread = inEventLoop(group)
                || (currentChannel != null && currentChannel.eventLoop().inEventLoop());

        if (currentChannel != null) {
            ChannelFuture closeFuture = currentChannel.close();
            if (!onNettyThread) {
                closeFuture.awaitUninterruptibly(3, TimeUnit.SECONDS);
            }
        }

        if (group != null) {
            io.netty.util.concurrent.Future<?> shutdownFuture = group.shutdownGracefully(0, 3, TimeUnit.SECONDS);
            if (!onNettyThread) {
                shutdownFuture.awaitUninterruptibly(4, TimeUnit.SECONDS);
            }
        }

        logger.info("Disconnected from GeyserMenu");
    }

    private static boolean inEventLoop(EventLoopGroup group) {
        if (group == null) {
            return false;
        }
        for (EventExecutor executor : group) {
            if (executor.inEventLoop()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Send a menu to a player
     */
    public void sendMenu(MenuData menuData, Consumer<MenuResponse> callback) {
        if (!authenticated) {
            logger.warning("Cannot send menu - not authenticated");
            return;
        }

        // Store callback for response. Responses arrive over the network and may never arrive at
        // all, so evict stale entries instead of growing this map without bound.
        long now = System.currentTimeMillis();
        responseCallbacks.values().removeIf(pending -> now - pending.createdAtMillis() > CALLBACK_TTL_MILLIS);
        responseCallbacks.put(menuData.getFormId(), new PendingCallback(callback, now));

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
        if (packet == null || packet.getType() == null) {
            return;
        }
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
            // Notify listener that authentication completed. Fires on the Netty IO thread -
            // platform implementations are responsible for hopping to a server thread if they
            // touch the server API.
            Runnable listener = authSuccessListener;
            if (listener != null) {
                try {
                    listener.run();
                } catch (Throwable t) {
                    logger.warning("Error in auth-success listener: " + t);
                }
            }
        } else {
            authenticated = false;
            logger.warning("Authentication failed: " + authData.getMessage());
            disconnect();
        }
    }

    private void handleMenuResponse(Packet packet) {
        MenuResponse response = GSON.fromJson(packet.getPayload(), MenuResponse.class);

        PendingCallback pending = responseCallbacks.remove(response.getFormId());
        if (pending != null) {
            pending.callback().accept(response);
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
        Consumer<ButtonData.ButtonClick> listener = buttonClickListener;
        if (click != null && listener != null) {
            // Netty IO thread. The platform listener must hop onto a server thread itself.
            try {
                listener.accept(click);
            } catch (Throwable t) {
                logger.warning("Error in button-click listener: " + t);
            }
        }
    }

    void onConnectionLost() {
        if (shuttingDown) {
            return;
        }
        connected = false;
        authenticated = false;
        logger.warning("Connection to GeyserMenu lost");

        Runnable listener = connectionLostListener;
        if (listener != null) {
            try {
                listener.run();
            } catch (Throwable t) {
                logger.warning("Error in connection-lost listener: " + t);
            }
        }

        scheduleReconnect();
    }

    /**
     * Schedule a reconnect attempt on this client's own daemon executor.
     *
     * <p>Deliberately NOT a Bukkit/BukkitRunnable task: {@code Bukkit.getScheduler()} throws
     * {@link UnsupportedOperationException} on Folia, and this module is also used by the Velocity
     * build, which has no Bukkit at all. A plain single daemon thread works identically on Folia,
     * Paper, Spigot and Velocity, and reconnect work never touches the server API anyway.
     */
    private void scheduleReconnect() {
        if (!autoReconnect || shuttingDown) return;

        // Connection-lost and connect-failure can both fire for the same drop; keep exactly one
        // attempt in flight rather than doubling the reconnect rate every failure.
        if (!reconnectPending.compareAndSet(false, true)) {
            return;
        }

        ScheduledExecutorService executor = this.reconnectExecutor;
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "GeyserMenu-Reconnect");
                thread.setDaemon(true);
                return thread;
            });
            this.reconnectExecutor = executor;
        }

        logger.info("Scheduling reconnect in " + reconnectDelaySeconds + " seconds...");
        try {
            executor.schedule(() -> {
                reconnectPending.set(false);
                if (!shuttingDown && autoReconnect) {
                    connect();
                }
            }, reconnectDelaySeconds, TimeUnit.SECONDS);
        } catch (RejectedExecutionException e) {
            reconnectPending.set(false);
        }
    }

    private void sendPacket(Packet packet) {
        Channel currentChannel = this.channel;
        if (currentChannel != null && currentChannel.isActive()) {
            // writeAndFlush is thread-safe: Netty hands the write to the channel's event loop.
            currentChannel.writeAndFlush(GSON.toJson(packet));
        }
    }

    // ==================== Button Registration ====================
    
    private volatile Consumer<ButtonData.ButtonClick> buttonClickListener;
    
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

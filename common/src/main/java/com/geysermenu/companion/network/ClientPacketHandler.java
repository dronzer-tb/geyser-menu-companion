package com.geysermenu.companion.network;

import com.geysermenu.companion.protocol.Packet;
import com.google.gson.Gson;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.util.concurrent.CompletableFuture;

/**
 * Handles incoming packets from the GeyserMenu extension
 */
public class ClientPacketHandler extends SimpleChannelInboundHandler<String> {

    private static final Gson GSON = new Gson();

    private final MenuClient client;
    private final CompletableFuture<Boolean> connectionFuture;

    public ClientPacketHandler(MenuClient client, CompletableFuture<Boolean> connectionFuture) {
        this.client = client;
        this.connectionFuture = connectionFuture;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String message) {
        try {
            Packet packet = GSON.fromJson(message, Packet.class);
            client.handlePacket(packet);

            // Complete the connection future on auth response
            if (packet.getType() == Packet.PacketType.AUTH_RESPONSE && !connectionFuture.isDone()) {
                connectionFuture.complete(client.isAuthenticated());
            }
        } catch (Exception e) {
            // Log error
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        client.onConnectionLost();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // Connection error
        ctx.close();
    }
}

package com.gmail.tracebachi.SockExchange.Velocity;

import com.gmail.tracebachi.SockExchange.Messages.ReceivedMessage;
import com.gmail.tracebachi.SockExchange.SockExchangeConstants;
import com.gmail.tracebachi.SockExchange.Utilities.Registerable;
import com.google.common.base.Preconditions;
import com.google.common.io.ByteArrayDataInput;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;

import java.util.Optional;
import java.util.function.Consumer;

public class MovePlayersChannelListener implements Consumer<ReceivedMessage>, Registerable {
    private final SockExchangePlugin plugin;
    private final SockExchangeApi api;

    MovePlayersChannelListener(SockExchangePlugin plugin, SockExchangeApi api) {
        Preconditions.checkNotNull(plugin, "plugin");
        Preconditions.checkNotNull(api, "api");

        this.plugin = plugin;
        this.api = api;
    }

    @Override
    public void register() {
        api.getMessageNotifier().register(SockExchangeConstants.Channels.MOVE_PLAYERS, this);
    }

    @Override
    public void unregister() {
        api.getMessageNotifier().unregister(SockExchangeConstants.Channels.MOVE_PLAYERS, this);
    }

    @Override
    public void accept(ReceivedMessage message) {
        ByteArrayDataInput in = message.getDataInput();
        String serverName = in.readUTF();
        ProxyServer proxy = plugin.getProxy();

        int count = in.readInt();
        for (int i = 0; i < count; i++) {
            String playerName = in.readUTF();

            Optional<Player> player = proxy.getPlayer(playerName);
            if (!player.isPresent()) {
                continue;
            }

            Optional<RegisteredServer> serverInfo = proxy.getServer(serverName);
            if (!serverInfo.isPresent()) {
                continue;
            }

            player.get().createConnectionRequest(serverInfo.get());
        }
    }
}


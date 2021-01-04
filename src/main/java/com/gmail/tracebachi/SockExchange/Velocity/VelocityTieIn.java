package com.gmail.tracebachi.SockExchange.Velocity;

import com.gmail.tracebachi.SockExchange.Netty.BungeeToSpigotConnection;
import com.gmail.tracebachi.SockExchange.SpigotServerInfo;

import java.util.Collection;
import java.util.List;

public interface VelocityTieIn {
    boolean doesRegistrationPasswordMatch(String password);

    BungeeToSpigotConnection getConnection(String spigotServerName);

    Collection<BungeeToSpigotConnection> getConnections();

    SpigotServerInfo getServerInfo(String serverName);

    List<SpigotServerInfo> getServerInfos();

    String getServerNameForPlayer(String playerName);

    void sendChatMessagesToPlayer(String playerName, List<String> messages);

    void sendChatMessagesToConsole(List<String> messages);
}

package com.gmail.tracebachi.SockExchange.Velocity;

import com.gmail.tracebachi.SockExchange.Utilities.CaseInsensitiveSet;
import com.gmail.tracebachi.SockExchange.Utilities.MessageFormatMap;
import com.google.gson.reflect.TypeToken;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SockExchangeConfiguration {
    private int port;
    private int connectionThreads;
    private String registrationPassword;
    private MessageFormatMap messageFormatMap;
    private boolean debugMode;
    private CaseInsensitiveSet privateServers = new CaseInsensitiveSet(new HashSet<>());

    public void load(CommentedConfigurationNode root) throws SerializationException {
        this.port = root.node("SockExchangeServer", "Port").getInt();
        this.connectionThreads = root.node("SockExchangeServer", "Threads").getInt();
        this.registrationPassword = root.node("SockExchangeServer", "Password").getString();

        this.privateServers = new CaseInsensitiveSet(new HashSet<>(root.node("PrivateServers").getList(String.class)));

        this.messageFormatMap = new MessageFormatMap();

        Map<String, String> formats = (Map<String, String>) root.node("Formats").get(new TypeToken<Map<String, String>>(){}.getType());

        formats.forEach((k, v) -> this.messageFormatMap.put(k, v));

        this.debugMode = root.node("DebugMode").getBoolean();
    }

    int getPort() {
        return port;
    }

    int getConnectionThreads() {
        return this.connectionThreads;
    }

    boolean doesRegistrationPasswordMatch(String input) {
        return Objects.equals(this.registrationPassword, input);
    }

    MessageFormatMap getMessageFormatMap() {
        return this.messageFormatMap;
    }

    boolean inDebugMode() {
        return this.debugMode;
    }

    boolean isPrivateServer(String serverName) {
        return this.privateServers.contains(serverName);
    }
}

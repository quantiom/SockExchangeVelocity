package com.gmail.tracebachi.SockExchange.Velocity;

import com.gmail.tracebachi.SockExchange.SockExchangeConstants;
import com.gmail.tracebachi.SockExchange.SpigotServerInfo;
import com.gmail.tracebachi.SockExchange.Utilities.MessageFormatMap;
import com.gmail.tracebachi.SockExchange.Utilities.Registerable;
import com.google.common.base.Preconditions;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RunCmdVelocityCommand implements Registerable, SimpleCommand {
    private static final String COMMAND_NAME = "runcmdvelocity";
    private static final String COMMAND_PERM = "SockExchange.Command.RunCmd";
    private static final String DEST_SPIGOT_SERVERS = "ALL";
    private static final String[] COMMAND_ALIASES = new String[]{"rcvelocity"};

    private final SockExchangePlugin plugin;
    private final MessageFormatMap formatMap;
    private final SockExchangeApi api;

    RunCmdVelocityCommand(SockExchangePlugin plugin, MessageFormatMap formatMap, SockExchangeApi api) {
        this.plugin = plugin;
        this.formatMap = formatMap;
        this.api = api;
    }

    @Override
    public void register() {
        plugin.getProxy().getCommandManager().register(plugin.getProxy().getCommandManager().metaBuilder(COMMAND_NAME).aliases(COMMAND_ALIASES).build(), this);
    }

    @Override
    public void unregister() {
        plugin.getProxy().getCommandManager().unregister(COMMAND_NAME);
    }

    @Override
    public void execute(final Invocation invocation) {
        CommandSource sender = invocation.source();
        String[] args = invocation.arguments();

        if (!sender.hasPermission(COMMAND_PERM)) {
            sender.sendMessage(Component.text(formatMap.format(SockExchangeConstants.FormatNames.NO_PERM, COMMAND_PERM)));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text(formatMap.format(SockExchangeConstants.FormatNames.USAGE, "/runcmd server[,server,..] command")));
            sender.sendMessage(Component.text(formatMap.format(SockExchangeConstants.FormatNames.USAGE, "/runcmd ALL command")));
            return;
        }

        String[] argServers = args[0].split(",");
        String commandStr = joinArgsForCommand(args);

        if (doesArrayContain(argServers, DEST_SPIGOT_SERVERS)) {
            api.sendCommandsToServers(Collections.singletonList(commandStr), Collections.emptyList());

            sender.sendMessage(Component.text(formatMap.format(SockExchangeConstants.FormatNames.COMMAND_SENT, DEST_SPIGOT_SERVERS)));
            return;
        }

        List<String> serverNames = new ArrayList<>(2);

        for (String dest : argServers) {
            SpigotServerInfo serverInfo = api.getServerInfo(dest);

            if (serverInfo == null) {
                sender.sendMessage(Component.text(formatMap.format(SockExchangeConstants.FormatNames.SERVER_NOT_FOUND, dest)));
            } else if (!serverInfo.isOnline()) {
                sender.sendMessage(Component.text(formatMap.format(SockExchangeConstants.FormatNames.SERVER_NOT_ONLINE, dest)));
            } else {
                serverNames.add(serverInfo.getServerName());
            }
        }

        // Send the command to the servers that could be matched
        api.sendCommandsToServers(Collections.singletonList(commandStr), serverNames);

        for (String destServerName : serverNames) {
            sender.sendMessage(Component.text(formatMap.format(SockExchangeConstants.FormatNames.COMMAND_SENT, destServerName)));
        }
    }

    private String joinArgsForCommand(String[] args) {
        StringBuilder builder = new StringBuilder();

        builder.append(args[1]);
        for (int i = 2; i < args.length; i++) {
            builder.append(" ");
            builder.append(args[i]);
        }

        return builder.toString();
    }

    private boolean doesArrayContain(String[] array, String source) {
        for (String item : array) {
            if (item.equalsIgnoreCase(source)) {
                return true;
            }
        }
        return false;
    }
}


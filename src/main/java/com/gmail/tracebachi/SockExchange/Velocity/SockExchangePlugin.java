package com.gmail.tracebachi.SockExchange.Velocity;

import com.gmail.tracebachi.SockExchange.ExpirableConsumer;
import com.gmail.tracebachi.SockExchange.Messages.ReceivedMessageNotifier;
import com.gmail.tracebachi.SockExchange.Messages.ResponseMessage;
import com.gmail.tracebachi.SockExchange.Messages.ResponseStatus;
import com.gmail.tracebachi.SockExchange.Netty.BungeeToSpigotConnection;
import com.gmail.tracebachi.SockExchange.Netty.SockExchangeServer;
import com.gmail.tracebachi.SockExchange.Scheduler.AwaitableExecutor;
import com.gmail.tracebachi.SockExchange.Scheduler.ScheduledExecutorServiceWrapper;
import com.gmail.tracebachi.SockExchange.SpigotServerInfo;
import com.gmail.tracebachi.SockExchange.Utilities.CaseInsensitiveMap;
import com.gmail.tracebachi.SockExchange.Utilities.LongIdCounterMap;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.loader.ConfigurationLoader;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;

@Plugin(
        id = "sockexchange",
        name = "SockExchange",
        version = "1.0.0",
        authors = {"quantiom"}
)
public class SockExchangePlugin implements VelocityTieIn {
    private final ProxyServer proxy;
    private final Logger logger;

    private ConfigurationLoader<CommentedConfigurationNode> loader;
    private CommentedConfigurationNode root;
    private SockExchangeConfiguration configuration;

    private CaseInsensitiveMap<BungeeToSpigotConnection> spigotConnectionMap;
    private ScheduledThreadPoolExecutor threadPoolExecutor;
    private ScheduledFuture<?> consumerTimeoutCleanupFuture;
    private LongIdCounterMap<ExpirableConsumer<ResponseMessage>> responseConsumerMap;
    private AwaitableExecutor awaitableExecutor;
    private ReceivedMessageNotifier messageNotifier;
    private SockExchangeServer sockExchangeServer;

    private ChatMessageChannelListener chatMessageChannelListener;
    private OnlinePlayerUpdateSender onlinePlayerUpdateSender;
    private VelocityKeepAliveSender velocityKeepAliveSender;
    private RunCmdChannelListener runCmdChannelListener;
    private MovePlayersChannelListener movePlayersChannelListener;
    private RunCmdVelocityCommand runCmdVelocityCommand;

    @Inject
    public SockExchangePlugin(ProxyServer server, Logger logger) {
        this.proxy = server;
        this.logger = logger;
    }

    @Subscribe
    public void onInitialization(ProxyInitializeEvent event) {
        this.logger.info("Initialized!");

        this.loader = this.initializeConfigurationLoader();

        try {
            this.root = this.loader.load();
        } catch (IOException e) {
            this.logger.error("An error occurred while loading this configuration: " + e.getMessage());

            if (e.getCause() != null) {
                e.getCause().printStackTrace();
            }

            System.exit(1);
        }

        this.configuration = new SockExchangeConfiguration();

        try {
            this.configuration.load(this.root);
        } catch (SerializationException e) {
            e.printStackTrace();
        }

        CommandManager commandManager = this.proxy.getCommandManager();

        // Create the shared thread pool executor
        buildThreadPoolExecutor();
        ScheduledExecutorServiceWrapper wrappedThreadPool = new ScheduledExecutorServiceWrapper(threadPoolExecutor);

        // Create the AwaitableExecutor
        this.awaitableExecutor = new AwaitableExecutor(wrappedThreadPool);

        // Create the message notifier which will run consumers on SockExchange messages
        this.messageNotifier = new ReceivedMessageNotifier(awaitableExecutor);

        // Create the map that manages consumers for responses to sent message
        responseConsumerMap = new LongIdCounterMap<>();

        // Schedule a task to clean up the responseConsumerMap (handling timeouts)
        consumerTimeoutCleanupFuture = threadPoolExecutor.scheduleWithFixedDelay(
                this::checkForConsumerTimeouts, 5, 5, TimeUnit.SECONDS);

        // Create the API
        SockExchangeApi api = new SockExchangeApi(this, wrappedThreadPool, messageNotifier);
        SockExchangeApi.setInstance(api);

        this.spigotConnectionMap = new CaseInsensitiveMap<>(new ConcurrentHashMap<>());

        for (RegisteredServer server : this.proxy.getAllServers()) {
            String serverName = server.getServerInfo().getName();

            BungeeToSpigotConnection connection = new BungeeToSpigotConnection(serverName, this.awaitableExecutor, this.messageNotifier, this.responseConsumerMap, this.logger, this);

            this.spigotConnectionMap.put(serverName, connection);
        }

        this.sockExchangeServer = new SockExchangeServer(this.configuration.getPort(), this.configuration.getConnectionThreads(), this);

        try {
            this.sockExchangeServer.start();
        } catch (Exception e) {
            e.printStackTrace();
        }

        chatMessageChannelListener = new ChatMessageChannelListener(api);
        chatMessageChannelListener.register();

        onlinePlayerUpdateSender = new OnlinePlayerUpdateSender(this, api, 5000);
        onlinePlayerUpdateSender.register();

        velocityKeepAliveSender = new VelocityKeepAliveSender(this, api, 2000);
        velocityKeepAliveSender.register();

        runCmdChannelListener = new RunCmdChannelListener(this, logger, api, commandManager);
        runCmdChannelListener.register();

        movePlayersChannelListener = new MovePlayersChannelListener(this, api);
        movePlayersChannelListener.register();

        runCmdVelocityCommand = new RunCmdVelocityCommand(this, configuration.getMessageFormatMap(), api);
        runCmdVelocityCommand.register();
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        try {
            this.loader.save(this.root);
        } catch (final ConfigurateException e) {
            System.err.println("Unable to save your messages configuration! Sorry! " + e.getMessage());
            System.exit(1);
        }

        if (awaitableExecutor != null) {
            shutdownAwaitableExecutor();
            awaitableExecutor = null;
        }

        if (sockExchangeServer != null) {
            sockExchangeServer.shutdown();
            sockExchangeServer = null;
        }

        if (chatMessageChannelListener != null) {
            chatMessageChannelListener.unregister();
            chatMessageChannelListener = null;
        }

        if (runCmdChannelListener != null) {
            runCmdChannelListener.unregister();
            runCmdChannelListener = null;
        }

        if (movePlayersChannelListener != null) {
            movePlayersChannelListener.unregister();
            movePlayersChannelListener = null;
        }

        if (runCmdVelocityCommand != null) {
            runCmdVelocityCommand.unregister();
            runCmdVelocityCommand = null;
        }

        if (velocityKeepAliveSender != null) {
            velocityKeepAliveSender.unregister();
            velocityKeepAliveSender = null;
        }

        if (onlinePlayerUpdateSender != null) {
            onlinePlayerUpdateSender.unregister();
            onlinePlayerUpdateSender = null;
        }

        SockExchangeApi.setInstance(null);

        if (spigotConnectionMap != null) {
            spigotConnectionMap.clear();
            spigotConnectionMap = null;
        }

        if (consumerTimeoutCleanupFuture != null) {
            consumerTimeoutCleanupFuture.cancel(false);
            consumerTimeoutCleanupFuture = null;
        }

        if (responseConsumerMap != null) {
            responseConsumerMap.clear();
            responseConsumerMap = null;
        }

        if (threadPoolExecutor != null) {
            shutdownThreadPoolExecutor();
            threadPoolExecutor = null;
        }
    }

    private ConfigurationLoader<CommentedConfigurationNode> initializeConfigurationLoader() {
        final File configFilesLocation = Paths.get("plugins/sockexchange").toFile();
        final Path configFileLocation = Paths.get(configFilesLocation + "/config.yml");

        if (!configFilesLocation.exists()) {
            if (!configFilesLocation.mkdirs()) {
                throw new IllegalStateException("Unable to create config directory");
            }
        }

        if (!Files.exists(configFileLocation)) {
            try {
                try (InputStream is = getClass().getClassLoader().getResourceAsStream("velocity-config.yml")) {
                    Files.copy(is, configFileLocation);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Unable to create default config");
            }
        }

        return YamlConfigurationLoader.builder().path(configFileLocation).nodeStyle(NodeStyle.BLOCK).build();
    }


    public ProxyServer getProxy() {
        return this.proxy;
    }

    public Logger getLogger() {
        return this.logger;
    }

    private void buildThreadPoolExecutor() {
        ThreadFactoryBuilder factoryBuilder = new ThreadFactoryBuilder();
        factoryBuilder.setNameFormat("SockExchange-Scheduler-Thread-%d");

        ThreadFactory threadFactory = factoryBuilder.build();
        threadPoolExecutor = new ScheduledThreadPoolExecutor(2, threadFactory);

        threadPoolExecutor.setMaximumPoolSize(8);
        threadPoolExecutor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        threadPoolExecutor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    }

    private void checkForConsumerTimeouts() {
        long currentTimeMillis = System.currentTimeMillis();

        responseConsumerMap.removeIf((entry) -> {
            ExpirableConsumer<ResponseMessage> responseConsumer = entry.getValue();

            if (responseConsumer.getExpiresAtMillis() > currentTimeMillis) {
                // Keep the entry
                return false;
            }

            awaitableExecutor.execute(() ->
            {
                ResponseMessage responseMessage = new ResponseMessage(ResponseStatus.TIMED_OUT);
                responseConsumer.accept(responseMessage);
            });

            // Remove the entry
            return true;
        });
    }

    @Override
    public boolean doesRegistrationPasswordMatch(String password) {
        return this.configuration.doesRegistrationPasswordMatch(password);
    }

    @Override
    public BungeeToSpigotConnection getConnection(String spigotServerName) {
        return spigotConnectionMap.get(spigotServerName);
    }

    @Override
    public Collection<BungeeToSpigotConnection> getConnections() {
        return Collections.unmodifiableCollection(spigotConnectionMap.values());
    }

    @Override
    public SpigotServerInfo getServerInfo(String serverName) {
        BungeeToSpigotConnection connection = spigotConnectionMap.get(serverName);

        if (connection == null) {
            return null;
        }

        boolean isPrivate = configuration.isPrivateServer(connection.getServerName());
        return new SpigotServerInfo(connection.getServerName(), connection.hasChannel(), isPrivate);
    }

    @Override
    public List<SpigotServerInfo> getServerInfos() {
        Collection<BungeeToSpigotConnection> connections = spigotConnectionMap.values();
        List<SpigotServerInfo> result = new ArrayList<>(connections.size());

        for (BungeeToSpigotConnection connection : connections) {
            boolean isPrivate = configuration.isPrivateServer(connection.getServerName());
            SpigotServerInfo serverInfo = new SpigotServerInfo(connection.getServerName(),
                    connection.hasChannel(), isPrivate);

            result.add(serverInfo);
        }

        return Collections.unmodifiableList(result);
    }

    @Override
    public String getServerNameForPlayer(String playerName) {
        Optional<Player> player = this.proxy.getPlayer(playerName);

        if (!player.isPresent()) {
            return null;
        }

        Optional<ServerConnection> server = player.get().getCurrentServer();

        return server.map(serverConnection -> serverConnection.getServer().getServerInfo().getName()).orElse(null);

    }

    @Override
    public void sendChatMessagesToPlayer(String playerName, List<String> messages) {
        Optional<Player> proxyPlayer = this.proxy.getPlayer(playerName);
        if (proxyPlayer.isPresent()) {
            for (String message : messages) {
                proxyPlayer.get().sendMessage(Component.text(message));
            }
        }
    }

    @Override
    public void sendChatMessagesToConsole(List<String> messages) {
        for (String message : messages) {
            this.proxy.getConsoleCommandSource().sendMessage(Component.text(message));
        }
    }

    private void shutdownAwaitableExecutor() {
        try {
            this.awaitableExecutor.setAcceptingTasks(false);
            this.awaitableExecutor.awaitTasksWithSleep(10, 1000);
            this.awaitableExecutor.shutdown();
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
    }

    private void shutdownThreadPoolExecutor() {
        if (!threadPoolExecutor.isShutdown()) {
            // Disable new tasks from being submitted to service
            threadPoolExecutor.shutdown();

            this.logger.info("ScheduledThreadPoolExecutor being shutdown()");

            try {
                // Await termination for a minute
                if (!threadPoolExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                    // Force shutdown
                    threadPoolExecutor.shutdownNow();

                    this.logger.info("ScheduledThreadPoolExecutor being shutdownNow()");

                    // Await termination again for another minute
                    if (!threadPoolExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                        this.logger.info("ScheduledThreadPoolExecutor not shutdown after shutdownNow()");
                    }
                }
            } catch (InterruptedException ex) {
                this.logger.info("ScheduledThreadPoolExecutor shutdown interrupted");

                // Re-cancel if current thread also interrupted
                threadPoolExecutor.shutdownNow();

                this.logger.info("ScheduledThreadPoolExecutor being shutdownNow()");

                // Preserve interrupt status
                Thread.currentThread().interrupt();
            }
        }
    }
}

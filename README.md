# SockExchange
Netty-based server and client for communicating with Velocity and Spigot servers

## Installation
Copy the same JAR into the plugins directory of your Velocity and Spigot installations. The
[default Velocity configuration](https://github.com/quantiom/SockExchangeVelocity/blob/master/src/main/resources/velocity-config.yml)
should be fine unless you want to edit something. However, the [default Spigot configuration](https://github.com/quantiom/SockExchangeVelocity/blob/master/src/main/resources/config.yml)
sets the `ServerName` to `world` which should be changed to the name of that server as configured
in the general Velocity configuration.

## Commands
`/moveto`
  - Permission: `SockExchange.MoveTo` for the normal command. `SockExchange.MoveTo.<server name>` for the permission to move to a private server.
  - Description: Use this command to switch servers.

`/moveotherto`
  - Permission: `SockExchange.MoveOtherTo`
  - Description: Use this command to move another player to a server. Use `@all` as the name to move everyone in the server to a different server.

`/runcmd`
  - Permission: `SockExchange.RunCmd`
  - Description: Use this command to send commands to run on other servers (commands will run as console)

## [Spigot API](https://github.com/GeeItsZee/SockExchange/blob/master/src/main/java/com/gmail/tracebachi/SockExchange/Spigot/SockExchangeApi.java)
Use this API for plugins running on Spigot.

```java
class Example {
  public void foo() {
    SockExchangeApi api = SockExchangeApi.instance();

    // Get the current server's name
    String serverName = api.getServerName();

    // Get SpigotServerInfo for the server with the specified name
    SpigotServerInfo spigotServerInfo = api.getServerInfo("...");

    // Get all SpigotServerInfos
    Collection<SpigotServerInfo> serverInfos = api.getServerInfos();

    // Get the names of all the players last known to be online
    api.getOnlinePlayerNames();

    // Use the shared ScheduledExecutorService
    Runnable runnable = () -> { /* Do some work */ };
    ScheduledExecutorService executorService = api.getScheduledExecutorService();
    executorService.submit(runnable);
    executorService.schedule(runnable, 1, TimeUnit.SECONDS);
    executorService.scheduleAtFixedRate(runnable, 1, 2, TimeUnit.SECONDS);
    executorService.scheduleWithFixedDelay(runnable, 2, 1, TimeUnit.SECONDS);

    // Build an AwaitableExecutor that can be used to wait for submitted tasks to finish
    int maxLoops = 10;
    int millisToSleep = 1000;
    AwaitableExecutor awaitableExecutor = new AwaitableExecutor(executorService);
    awaitableExecutor.execute(runnable);
    awaitableExecutor.execute(runnable);
    awaitableExecutor.setAcceptingTasks(false);
    awaitableExecutor.awaitTasksWithSleep(maxLoops, millisToSleep); // Throws InterruptedException

    // Initialize some variables
    String channelName = "...";
    Consumer<ReceivedMessage> requestConsumer = rm -> {
      ByteArrayDataInput in = rm.getDataInput();
      byte myByte = in.readByte();

      // Using respond(), the bytes will be sent back to the server that made the request
      rm.respond(new byte[] { 1, myByte, 3, 4});
    };

    // Get the request notifier which will run a provided Consumer when
    // there is a new message on a specific channel
    ReceivedMessageNotifier messageNotifier = api.getMessageNotifier();
    messageNotifier.register(channelName, requestConsumer);
    messageNotifier.unregister(channelName, requestConsumer);

    // Initialize some more variables
    byte[] bytes = ByteStreams.newDataOutput().toByteArray();
    long timeoutInMillis = TimeUnit.SECONDS.toMillis(5);
    Consumer<ResponseMessage> responseConsumer = rm -> {
      if (rm.getResponseStatus().isOk())
      {
        // Do stuff with the response that was sent with .respond() by the other server
        ByteArrayDataInput in = rm.getDataInput();
        String str = in.readUTF();
        int myInt = in.readInt();
      }
    };

    // Send a message to Velocity (with or without a consumer for a response)
    api.sendToVelocity(channelName, bytes);
    api.sendToVelocity(channelName, bytes, responseConsumer, timeoutInMillis);

    // Send a message to Spigot (with or without a consumer for a response)
    String destServerName = "...";
    api.sendToServer(channelName, bytes, destServerName);
    api.sendToServer(channelName, bytes, destServerName, responseConsumer, timeoutInMillis);

    // Send a message to a Spigot server with a certain player (with or without a consumer)
    String playerToFind = "GeeItsZee";
    api.sendToServerOfPlayer(channelName, bytes, playerToFind);
    api.sendToServerOfPlayer(channelName, bytes, playerToFind, responseConsumer, timeoutInMillis);

    // Send the same message to all Spigot servers
    api.sendToServers(channelName, bytes);

    // Send the same message to a list of Spigot servers
    List<String> listOfServerNames = new ArrayList<>();
    api.sendToServers(channelName, bytes, listOfServerNames);

    // Send a chat message to an online player or console on any server
    List<String> chatMessages = new ArrayList<>();
    api.sendChatMessages(chatMessages, playerToFind, null);
    api.sendChatMessages(chatMessages, null, destServerName);
    api.sendChatMessages(chatMessages, null, "Velocity");
    api.sendChatMessages(chatMessages, playerToFind, destServerName);

    // Move players to a different server
    Set<String> playersToMove = new HashSet<>();
    api.movePlayers(playersToMove, destServerName);

    // Run commands on a single/multiple servers or Velocity
    List<String> commands = new ArrayList<>();
    api.sendCommandsToServers(commands, Collections.emptyList());
    api.sendCommandsToServers(commands, listOfServerNames);
    api.sendCommandsToVelocity(commands);

    // Send chat messages to a player
    List<String> messages = new ArrayList<>();
    api.sendChatMessages(messages, "GeeItsZee", serverName);
    api.sendChatMessages(messages, "GeeItsZee", null);

    // Send chat messages to a server console
    api.sendChatMessages(messages, null, serverName);
    api.sendChatMessages(messages, null, "Velocity");
  }
}
```

## [Velocity API](https://github.com/quantiom/SockExchangeVelocity/blob/master/src/main/java/com/gmail/tracebachi/SockExchange/Velocity/SockExchangeApi.java)
Use this API for plugins running on Velocity.

```java
class Example {
  public void foo() {
    SockExchangeApi api = SockExchangeApi.instance();

    // Get SpigotServerInfo for the server with the specified name
    api.getServerInfo("...");

    // Get all SpigotServerInfos
    api.getServerInfos();

    // Use the shared ScheduledExecutorService
    Runnable runnable = () -> { /* Do some work */ };
    ScheduledExecutorService executorService = api.getScheduledExecutorService();
    executorService.submit(runnable);
    executorService.schedule(runnable, 1, TimeUnit.SECONDS);
    executorService.scheduleAtFixedRate(runnable, 1, 2, TimeUnit.SECONDS);
    executorService.scheduleWithFixedDelay(runnable, 2, 1, TimeUnit.SECONDS);

    // Build an AwaitableExecutor that can be used to wait for submitted tasks to finish
    int maxLoops = 10;
    int millisToSleep = 1000;
    AwaitableExecutor awaitableExecutor = new AwaitableExecutor(executorService);
    awaitableExecutor.execute(runnable);
    awaitableExecutor.execute(runnable);
    awaitableExecutor.setAcceptingTasks(false);
    awaitableExecutor.awaitTasksWithSleep(maxLoops, millisToSleep); // Throws InterruptedException

    // Initialize some variables
    String channelName = "...";
    Consumer<ReceivedMessage> requestConsumer = rm -> {
      ByteArrayDataInput in = rm.getDataInput();
      byte myByte = in.readByte();

      // Using respond(), the bytes will be sent back to the server that made the request
      rm.respond(new byte[] { 1, myByte, 3, 4});
    };

    // Get the request notifier which will run a provided Consumer when
    // there is a new message on a specific channel
    ReceivedMessageNotifier messageNotifier = api.getMessageNotifier();
    messageNotifier.register(channelName, requestConsumer);
    messageNotifier.unregister(channelName, requestConsumer);

    // Initialize some more variables
    byte[] bytes = ByteStreams.newDataOutput().toByteArray();
    long timeoutInMillis = TimeUnit.SECONDS.toMillis(5);
    Consumer<ResponseMessage> responseConsumer = rm -> {
      ResponseStatus responseStatus = rm.getResponseStatus();
      if (responseStatus.isOk())
      {
        // Do stuff with the response
        ByteArrayDataInput in = rm.getDataInput();
        String str = in.readUTF();
        int myInt = in.readInt();
      }
    };

    // Send a message to Spigot (with or without a consumer for a response)
    String destServerName = "...";
    api.sendToServer(channelName, bytes, destServerName);
    api.sendToServer(channelName, bytes, destServerName, responseConsumer, timeoutInMillis);

    // Send the same message to all Spigot servers
    api.sendToServers(channelName, bytes);

    // Run commands on a single/multiple servers
    List<String> commands = new ArrayList<>();
    List<String> serverList = new ArrayList<>();
    api.sendCommandsToServers(commands, Collections.emptyList());
    api.sendCommandsToServers(commands, serverList);

    // Send a chat message to an online player or console on any server
    String playerToFind = "quantiom";
    List<String> chatMessages = new ArrayList<>();
    api.sendChatMessages(chatMessages, playerToFind, null);
    api.sendChatMessages(chatMessages, null, destServerName);
    api.sendChatMessages(chatMessages, null, "Velocity");
    api.sendChatMessages(chatMessages, playerToFind, destServerName);
  }
}
```

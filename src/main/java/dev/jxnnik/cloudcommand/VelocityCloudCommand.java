package dev.jxnnik.cloudcommand;

import com.google.inject.Inject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.jxnnik.cloudcommand.config.Config;
import eu.thesimplecloud.api.CloudAPI;
import eu.thesimplecloud.api.network.component.ManagerComponent;
import eu.thesimplecloud.api.player.IOfflineCloudPlayer;
import eu.thesimplecloud.api.service.ICloudService;
import eu.thesimplecloud.api.servicegroup.ICloudServiceGroup;
import eu.thesimplecloud.module.permission.PermissionPool;
import eu.thesimplecloud.module.permission.player.PlayerPermissionGroupInfo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Plugin(
        id = "cloudcommand-module",
        name = "CloudCommand",
        version = "1.0-RELEASE",
        description = "A cloud command like cloudnet for simplecloud",
        url = "https://jxnnik.dev",
        authors = {"ByRaudy"}
)
public class VelocityCloudCommand {

    private final ProxyServer proxyServer;
    private final Config moduleConfig;

    @Inject
    public VelocityCloudCommand(ProxyServer proxyServer) {
        this.proxyServer = proxyServer;
        this.moduleConfig = new Config();
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        proxyServer.getScheduler().buildTask(this, () -> proxyServer.getCommandManager().register(this.build())).delay(1, TimeUnit.SECONDS).schedule();
    }

    public BrigadierCommand build() {
        LiteralCommandNode<CommandSource> node = LiteralArgumentBuilder.<CommandSource>literal("cloud")
                .requires(commandSource -> commandSource.hasPermission("cloudcommand.command.cloud"))
                .executes(this::sendHelp)
                .then(LiteralArgumentBuilder.<CommandSource>literal("create")
                        .then(LiteralArgumentBuilder.<CommandSource>literal("by")
                                .then(RequiredArgumentBuilder.<CommandSource, String>argument("cloudGroup", StringArgumentType.string())
                                        .suggests(this::setCloudGroupsSuggestions)
                                        .then(RequiredArgumentBuilder.<CommandSource, String>argument("count", StringArgumentType.string())
                                                .then(LiteralArgumentBuilder.<CommandSource>literal("--start")
                                                        .executes(context -> executeStartService(context, CloudAPI.getInstance().getCloudServiceGroupManager().getServiceGroupByName(context.getArgument("cloudGroup", String.class)), Integer.parseInt(context.getArgument("count", String.class)))))))))
                .then(LiteralArgumentBuilder.<CommandSource>literal("players")
                        .then(LiteralArgumentBuilder.<CommandSource>literal("player")
                                .then(RequiredArgumentBuilder.<CommandSource, String>argument("player", StringArgumentType.string())
                                        .suggests(this::setOnlinePlayerSuggestions)
                                        .then(LiteralArgumentBuilder.<CommandSource>literal("info")
                                                .executes(context -> sendCloudPlayerInfo(context, CloudAPI.getInstance().getCloudPlayerManager().getOfflineCloudPlayer(context.getArgument("player", String.class)).getBlockingOrNull())))
                                        .then(LiteralArgumentBuilder.<CommandSource>literal("setgroup")
                                                .then(RequiredArgumentBuilder.<CommandSource, String>argument("rank", StringArgumentType.string())
                                                        .suggests(this::setRanksSuggestions)
                                                        .executes(context -> setCloudPlayersRank(context, context.getArgument("player", String.class), context.getArgument("rank", String.class)))))))
                        .then(LiteralArgumentBuilder.<CommandSource>literal("list")
                                .then(LiteralArgumentBuilder.<CommandSource>literal("online")
                                        .executes(this::sendOnlineCloudPlayers))))
                .then(LiteralArgumentBuilder.<CommandSource>literal("service")
                        .then(LiteralArgumentBuilder.<CommandSource>literal("listall")
                                .executes(this::listOnlineCloudServices))
                        .then(LiteralArgumentBuilder.<CommandSource>literal("get")
                                .then(RequiredArgumentBuilder.<CommandSource, String>argument("service", StringArgumentType.string())
                                        .suggests(this::setCloudServicesSuggestions)
                                        .then(LiteralArgumentBuilder.<CommandSource>literal("stop")
                                                .executes(context -> shutdownCloudService(context, CloudAPI.getInstance().getCloudServiceManager().getCloudServiceByName(context.getArgument("service", String.class)))))
                                        .then(LiteralArgumentBuilder.<CommandSource>literal("info")
                                                .executes(context -> listInfoCloudService(context, CloudAPI.getInstance().getCloudServiceManager().getCloudServiceByName(context.getArgument("service", String.class))))))))
                .then(LiteralArgumentBuilder.<CommandSource>literal("delete")
                        .then(LiteralArgumentBuilder.<CommandSource>literal("by")
                                .then(RequiredArgumentBuilder.<CommandSource, String>argument("serviceGroup", StringArgumentType.string())
                                        .suggests(this::setCloudGroupsSuggestions)
                                        .then(LiteralArgumentBuilder.<CommandSource>literal("--stop")
                                                .executes(context -> shutdownCloudGroupService(context, CloudAPI.getInstance().getCloudServiceGroupManager().getServiceGroupByName(context.getArgument("serviceGroup", String.class))))))))
                .build();

        return new BrigadierCommand(node);
    }

    private CompletableFuture<Suggestions> setCloudGroupsSuggestions(CommandContext<CommandSource> context, SuggestionsBuilder suggestionsBuilder) {
        CloudAPI.getInstance().getCloudServiceGroupManager().getAllCachedObjects().forEach(iCloudServiceGroup -> suggestionsBuilder.suggest(iCloudServiceGroup.getName()));
        return suggestionsBuilder.buildFuture();
    }

    private CompletableFuture<Suggestions> setOnlinePlayerSuggestions(CommandContext<CommandSource> context, SuggestionsBuilder suggestionsBuilder) {
        proxyServer.getAllPlayers().forEach(player -> suggestionsBuilder.suggest(player.getUsername()));
        return suggestionsBuilder.buildFuture();
    }

    private CompletableFuture<Suggestions> setRanksSuggestions(CommandContext<CommandSource> context, SuggestionsBuilder suggestionsBuilder) {
        PermissionPool.getInstance().getPermissionGroupManager().getAllPermissionGroups().forEach(iPermissionGroup -> suggestionsBuilder.suggest(iPermissionGroup.getName()));
        return suggestionsBuilder.buildFuture();
    }

    private CompletableFuture<Suggestions> setCloudServicesSuggestions(CommandContext<CommandSource> context, SuggestionsBuilder suggestionsBuilder) {
        CloudAPI.getInstance().getCloudServiceManager().getAllCachedObjects().forEach(iCloudService -> suggestionsBuilder.suggest(iCloudService.getName()));
        return suggestionsBuilder.buildFuture();
    }

    private int sendHelp(CommandContext<CommandSource> context) {
        context.getSource().sendMessage(MiniMessage.miniMessage().deserialize(moduleConfig.getString("prefix") + "CloudCommand by <red>ByRaudy"));
        context.getSource().sendMessage(MiniMessage.miniMessage().deserialize(moduleConfig.getString("prefix") + ""));
        context.getSource().sendMessage(MiniMessage.miniMessage().deserialize(moduleConfig.getString("prefix") + "/cloud create by <service-group> <count> --start"));
        context.getSource().sendMessage(MiniMessage.miniMessage().deserialize(moduleConfig.getString("prefix") + "/cloud delete by <service-group> <count> --stop"));
        context.getSource().sendMessage(MiniMessage.miniMessage().deserialize(moduleConfig.getString("prefix") + "/cloud players list online"));
        context.getSource().sendMessage(MiniMessage.miniMessage().deserialize(moduleConfig.getString("prefix") + "/cloud players player <player-name> <info/setgroup> <group>"));
        context.getSource().sendMessage(MiniMessage.miniMessage().deserialize(moduleConfig.getString("prefix") + "/cloud service listall"));
        context.getSource().sendMessage(MiniMessage.miniMessage().deserialize(moduleConfig.getString("prefix") + "/cloud service get <service-name> <info/stop>"));
        return 1;
    }

    private int sendCloudPlayerInfo(CommandContext<CommandSource> context, IOfflineCloudPlayer iCloudPlayer) {
        SimpleDateFormat simpleDateFormatDate = new SimpleDateFormat("dd:MM:yy");
        PlayerPermissionGroupInfo playerPermissionGroupInfo = PermissionPool.getInstance().getPermissionPlayerManager().getPermissionPlayer(iCloudPlayer.getName()).getBlockingOrNull().getPermissionGroupInfoList().stream().toList().get(0);
        System.out.println(PermissionPool.getInstance().getPermissionPlayerManager().getPermissionPlayer(iCloudPlayer.getName()).getBlockingOrNull().getPermissionGroupInfoList().stream().toList().size());
        if (PermissionPool.getInstance().getPermissionPlayerManager().getPermissionPlayer(iCloudPlayer.getName()).getBlockingOrNull().getPermissionGroupInfoList().stream().toList().size() >= 2 && PermissionPool.getInstance().getPermissionPlayerManager().getPermissionPlayer(iCloudPlayer.getName()).getBlockingOrNull().getPermissionGroupInfoList().stream().toList().get(0).getPermissionGroupName().equals("default")) {
            playerPermissionGroupInfo = PermissionPool.getInstance().getPermissionPlayerManager().getPermissionPlayer(iCloudPlayer.getName()).getBlockingOrNull().getPermissionGroupInfoList().stream().toList().get(1);
        }

        context.getSource().sendMessage(MiniMessage.miniMessage().deserialize(moduleConfig.getString("prefix") + "<gray>" + iCloudPlayer.getName() + " <dark_gray>/ <gray>" + iCloudPlayer.getUniqueId()));
        if (playerPermissionGroupInfo.getTimeoutTimestamp() == -1) {
            context.getSource().sendMessage(MiniMessage.miniMessage().deserialize(moduleConfig.getString("prefix") + "<gray>" + playerPermissionGroupInfo.getPermissionGroupName() + " <dark_gray>/ <dark_red><bold>Permanent</bold>"));
        } else {
            context.getSource().sendMessage(MiniMessage.miniMessage().deserialize(moduleConfig.getString("prefix") + "<gray>" + playerPermissionGroupInfo.getPermissionGroupName() + " <dark_gray>/ <yellow>" + simpleDateFormatDate.format(new Date(playerPermissionGroupInfo.getTimeoutTimestamp())).replace(":", ".")));
        }
        context.getSource().sendMessage(MiniMessage.miniMessage().deserialize(moduleConfig.getString("prefix") + "<gray>First login<dark_gray>: <gray>" + simpleDateFormatDate.format(new Date(iCloudPlayer.getFirstLogin())).replace(":", ".") + " <dark_gray>/ <gray>Last login<dark_gray>: <gray>" + simpleDateFormatDate.format(new Date(iCloudPlayer.getLastLogin())).replace(":", ".")));
        return 1;
    }

    private int sendOnlineCloudPlayers(CommandContext<CommandSource> context) {
        CloudAPI.getInstance().getCloudPlayerManager().getAllCachedObjects().forEach(iCloudPlayer -> context.getSource().sendMessage(MiniMessage.miniMessage().deserialize(moduleConfig.getString("prefix") + "<gray>" + iCloudPlayer.getName() + " <dark_gray>/ <gray>" + iCloudPlayer.getUniqueId() + " <dark_gray>/ <gray>" + iCloudPlayer.getConnectedServerName())));
        return 1;
    }

    private int setCloudPlayersRank(CommandContext<CommandSource> context, String playerName, String rank) {
        context.getSource().sendMessage(MiniMessage.miniMessage().deserialize(moduleConfig.getString("prefix") + "<gray>The rank of <yellow>" + playerName + " <gray>was set to <gold>" + rank + "<dark_gray>."));
        ManagerComponent.INSTANCE.executeCommand("perms user " + playerName + " group set " + rank);

        if(proxyServer.getPlayer(playerName).isPresent()) {
            proxyServer.getPlayer(playerName).get().disconnect(MiniMessage.miniMessage().deserialize(moduleConfig.getString("module.kickscreen.newrank", "%rank%", rank)));
        }
        return 1;
    }

    private int executeStartService(CommandContext<CommandSource> context, ICloudServiceGroup iCloudServiceGroup, int count) {
        for (int i = 0; i < count; i++) {
            iCloudServiceGroup.startNewService();
        }
        context.getSource().sendMessage(MiniMessage.miniMessage().deserialize(moduleConfig.getString("prefix") + "<gray>Please wait until all services started successfully<dark_gray>!"));
        return 1;
    }

    private int shutdownCloudService(CommandContext<CommandSource> context, ICloudService iCloudService) {
        iCloudService.shutdown();
        context.getSource().sendMessage(MiniMessage.miniMessage().deserialize(moduleConfig.getString("prefix") + "<yellow>" + iCloudService.getName() + " <gray>will be stopped<dark_gray>."));
        return 1;
    }

    private int shutdownCloudGroupService(CommandContext<CommandSource> context, ICloudServiceGroup iCloudServiceGroup) {
        iCloudServiceGroup.getAllServices().forEach(iCloudService -> iCloudService.shutdown());
        return 1;
    }

    private int listInfoCloudService(CommandContext<CommandSource> context, ICloudService iCloudService) {
        context.getSource().sendMessage(MiniMessage.miniMessage().deserialize(moduleConfig.getString("prefix") + ""));
        context.getSource().sendMessage(MiniMessage.miniMessage().deserialize(moduleConfig.getString("prefix") + "<gray>Info of <yellow>" + iCloudService.getName() + "<dark_gray>(<yellow>" + iCloudService.getServiceGroup().getName() + "<dark_gray>)"));
        context.getSource().sendMessage(MiniMessage.miniMessage().deserialize(moduleConfig.getString("prefix") + ""));
        context.getSource().sendMessage(MiniMessage.miniMessage().deserialize(moduleConfig.getString("prefix") + "<gray>IP<dark_gray>: <yellow>" + iCloudService.getHost() + " <dark_gray>/ <gray>Port<dark_gray>: <yellow>" + iCloudService.getPort()));
        context.getSource().sendMessage(MiniMessage.miniMessage().deserialize(moduleConfig.getString("prefix") + "<gray>Online players<dark_gray>: <yellow>" + iCloudService.getOnlineCount() + " <dark_gray>/ <gray>Max players<dark_gray>: <yellow>" + iCloudService.getMaxPlayers()));
        context.getSource().sendMessage(MiniMessage.miniMessage().deserialize(moduleConfig.getString("prefix") + "<gray>Used Memory<dark_gray>: <yellow>" + iCloudService.getUsedMemory() + " <dark_gray>/ <gray>Max Memory<dark_gray>: <yellow>" + iCloudService.getMaxMemory()));
        context.getSource().sendMessage(MiniMessage.miniMessage().deserialize(moduleConfig.getString("prefix") + "<gray>MOTD<dark_gray>: <yellow>" + iCloudService.getMOTD()));
        context.getSource().sendMessage(MiniMessage.miniMessage().deserialize(moduleConfig.getString("prefix") + "<gray>Node<dark_gray>: <yellow>" + iCloudService.getWrapper().getName() + " <dark_gray>(<gray>IP<dark_gray>: <yellow>" + iCloudService.getWrapper().getHost() + " <dark_gray>| <gray>Used Memory<dark_gray>: <yellow>" + iCloudService.getWrapper().getUsedMemory() + " <dark_gray>| <gray>Max Memory<dark_gray>: <yellow>" + iCloudService.getWrapper().getMaxMemory() + "<dark_gray>)"));
        context.getSource().sendMessage(MiniMessage.miniMessage().deserialize(moduleConfig.getString("prefix") + "<gray>ServiceType<dark_gray>: <yellow>" + iCloudService.getServiceType() + " <dark_gray>/ <gray>State<dark_gray>: <yellow>" + iCloudService.getState()));
        context.getSource().sendMessage(MiniMessage.miniMessage().deserialize(moduleConfig.getString("prefix") + ""));
        return 1;
    }

    private int listOnlineCloudServices(CommandContext<CommandSource> context) {
        context.getSource().sendMessage(MiniMessage.miniMessage().deserialize(moduleConfig.getString("prefix") + ""));
        context.getSource().sendMessage(MiniMessage.miniMessage().deserialize(moduleConfig.getString("prefix") + "<gray>List of all online ICloudServices"));
        context.getSource().sendMessage(MiniMessage.miniMessage().deserialize(moduleConfig.getString("prefix") + ""));
        CloudAPI.getInstance().getCloudServiceManager().getAllCachedObjects().forEach(iCloudService -> context.getSource().sendMessage(MiniMessage.miniMessage().deserialize(moduleConfig.getString("prefix") + "<gray>" + iCloudService.getName() + "<dark_gray>(<gray>" + iCloudService.getGroupName() + "<dark_gray>) <dark_gray>/ <gray>ServiceState<dark_gray>: <yellow>" + iCloudService.getState() + " <dark_gray>/ <gray>Online players<dark_gray>: <yellow>" + iCloudService.getOnlineCount() + " <dark_gray>| <gray>Max players<dark_gray>: <yellow>" + iCloudService.getMaxPlayers())));
        context.getSource().sendMessage(MiniMessage.miniMessage().deserialize(moduleConfig.getString("prefix") + ""));
        return 1;
    }
}
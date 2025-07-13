package org.ledat.leDatAntiOP;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import net.luckperms.api.event.EventBus;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.event.node.NodeAddEvent;
import net.luckperms.api.event.node.NodeRemoveEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.configuration.file.FileConfiguration;

import java.awt.*;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;

public class PlayerJoinListener implements Listener {
    private final IPCacheManager ipCacheManager;
    private final JDA jda;
    private final String discordChannelId;
    private final LuckPerms luckPerms;
    private final LeDatAntiOP plugin;
    private final Set<String> restrictedPlayers = new HashSet<>();
    private List<String> dangerousPermissions;
    private List<String> dangerousCommands;

    public PlayerJoinListener(IPCacheManager ipCacheManager, JDA jda, String discordChannelId, LuckPerms luckPerms, LeDatAntiOP plugin) {
        this.ipCacheManager = ipCacheManager;
        this.jda = jda;
        this.discordChannelId = discordChannelId;
        this.luckPerms = luckPerms;
        this.plugin = plugin;
        loadDangerousPermissions();
        loadDangerousCommands();
        
        // Đăng ký LuckPerms event listeners
        registerLuckPermsEvents();
    }
    
    private void registerLuckPermsEvents() {
        if (luckPerms != null) {
            EventBus eventBus = luckPerms.getEventBus();
            
            // Lắng nghe khi dữ liệu người chơi được tính toán lại (bao gồm thay đổi permission)
            eventBus.subscribe(plugin, UserDataRecalculateEvent.class, this::onUserDataRecalculate);
            
            // Lắng nghe khi node permission được thêm
            eventBus.subscribe(plugin, NodeAddEvent.class, this::onNodeAdd);
            
            // Lắng nghe khi node permission được xóa
            eventBus.subscribe(plugin, NodeRemoveEvent.class, this::onNodeRemove);
            
            plugin.getLogger().info("✅ Đã đăng ký LuckPerms event listeners!");
        }
    }
    
    // Event handler cho UserDataRecalculateEvent
    private void onUserDataRecalculate(UserDataRecalculateEvent event) {
        UUID playerUUID = event.getUser().getUniqueId();
        Player player = Bukkit.getPlayer(playerUUID);
        
        if (player != null && player.isOnline()) {
            // Delay 1 tick để đảm bảo dữ liệu đã được cập nhật
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                checkPlayerSecurity(player, "PERMISSION_RECALCULATE");
            }, 1L);
        }
    }
    
    // Event handler cho NodeAddEvent
    private void onNodeAdd(NodeAddEvent event) {
        // Kiểm tra xem target có phải là User không
        if (event.getTarget() instanceof User) {
            User user = (User) event.getTarget();
            UUID playerUUID = user.getUniqueId();
            Player player = Bukkit.getPlayer(playerUUID);
            
            if (player != null && player.isOnline()) {
                String nodeKey = event.getNode().getKey();
                
                // Kiểm tra nếu node được thêm là quyền nguy hiểm
                if (isDangerousNode(nodeKey)) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        checkPlayerSecurity(player, "PERMISSION_ADD: " + nodeKey);
                    }, 1L);
                }
            }
        }
    }
    
    // Event handler cho NodeRemoveEvent
    private void onNodeRemove(NodeRemoveEvent event) {
        // Kiểm tra xem target có phải là User không
        if (event.getTarget() instanceof User) {
            User user = (User) event.getTarget();
            UUID playerUUID = user.getUniqueId();
            Player player = Bukkit.getPlayer(playerUUID);
            
            if (player != null && player.isOnline()) {
                String nodeKey = event.getNode().getKey();
                
                // Nếu quyền nguy hiểm bị xóa, xóa khỏi danh sách hạn chế
                if (isDangerousNode(nodeKey)) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        // Kiểm tra lại sau khi xóa quyền
                        if (!player.isOp() && !hasDangerousPermissions(player)) {
                            restrictedPlayers.remove(player.getName());
                            player.sendMessage(ChatColor.GREEN + "✅ Quyền nguy hiểm đã được xóa. Bạn có thể hoạt động bình thường.");
                        }
                    }, 1L);
                }
            }
        }
    }
    
    // Kiểm tra xem node có phải là quyền nguy hiểm không
    private boolean isDangerousNode(String nodeKey) {
        if (nodeKey.equals("*")) return true;
        
        for (String dangerousPerm : dangerousPermissions) {
            if (nodeKey.startsWith(dangerousPerm.replace("*", ""))) {
                return true;
            }
        }
        return false;
    }

    private void loadDangerousPermissions() {
        FileConfiguration config = plugin.getConfig();
        dangerousPermissions = config.getStringList("dangerous-permissions");
        if (dangerousPermissions.isEmpty()) {
            // Default dangerous permissions
            dangerousPermissions = List.of(
                "bukkit.*", "cmi.*", "worldguard.*", "worldedit.*", "fawe.*",
                "permissions.*", "luckperms.*", "luckperms.editor", "luckperms.applyedits",
                "towny.*", "protocol.admin", "placeholderapi.admin", "playerpoints.*",
                "serverprotector.admin", "essentials.*", "fawe.bypass",
                "essentials.powertool.append", "essentials.gamemode.creative",
                "luckperms.sync", "essentials.sudo", "essentials.sudo.multiple",
                "essentials.sudo.exempt", "essentials.clearinventory",
                "essentials.clearinventory.all", "essentials.clearinventory.multiple",
                "essentials.clearinventory.others", "essentials.eco",
                "essentials.powertool", "worldedit.generation.sphere", "essentials.gamemode"
            );
        }
    }

    private void loadDangerousCommands() {
        FileConfiguration config = plugin.getConfig();
        dangerousCommands = config.getStringList("dangerous-commands");
        if (dangerousCommands.isEmpty()) {
            // Default dangerous commands
            dangerousCommands = List.of(
                "//sphere", "/sudo", "/eco reset", "/eco set", "/p giveall",
                "/playerpoints giveall", "/lp", "/luckperms", "/perm", "/permissions",
                "/op", "/deop", "/stop", "/restart", "/reload", "/gamemode", "/gm",
                "/give", "/clear", "/clearinventory", "/powertool", "/pt",
                "//set", "//replace", "//fill", "//fixwater", "//fixlava",
                "//drain", "//green", "//snow", "//thaw", "/worldedit",
                "/we", "/fawe", "/cmi", "/essentials", "/ess"
            );
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        checkPlayerSecurity(player, "JOIN");
    }

    // Thêm event listener cho lệnh OP
    @EventHandler
    public void onServerCommand(ServerCommandEvent event) {
        String command = event.getCommand().toLowerCase();
        
        // Kiểm tra lệnh op
        if (command.startsWith("op ")) {
            String[] parts = command.split(" ");
            if (parts.length >= 2) {
                String playerName = parts[1];
                Player player = Bukkit.getPlayer(playerName);
                
                if (player != null && player.isOnline()) {
                    // Delay 1 tick để đảm bảo OP đã được cấp
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        checkPlayerSecurity(player, "OP_GRANTED");
                    }, 1L);
                }
            }
        }
    }

    // Thêm event listener cho player command (nếu player tự op)
    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String command = event.getMessage().toLowerCase();
        
        // Kiểm tra nếu có người dùng lệnh op (cần quyền)
        if (command.startsWith("/op ")) {
            String[] parts = command.split(" ");
            if (parts.length >= 2) {
                String targetName = parts[1];
                Player target = Bukkit.getPlayer(targetName);
                
                if (target != null && target.isOnline()) {
                    // Delay để đảm bảo OP đã được cấp
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        checkPlayerSecurity(target, "OP_GRANTED");
                    }, 1L);
                }
            }
        }
        
        // Kiểm tra lệnh bị hạn chế cho người chơi có IP không hợp lệ
        if (restrictedPlayers.contains(player.getName())) {
            // Kiểm tra nếu người chơi có quyền OP hoặc quyền nguy hiểm
            boolean hasOP = player.isOp();
            boolean hasDangerousPerms = hasDangerousPermissions(player);
            
            if (hasOP || hasDangerousPerms) {
                // Chặn các lệnh nguy hiểm
                boolean isDangerousCommand = false;
                for (String dangerousCmd : dangerousCommands) {
                    if (command.startsWith(dangerousCmd.toLowerCase())) {
                        isDangerousCommand = true;
                        break;
                    }
                }
                
                if (isDangerousCommand) {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "⚠️ Lệnh nguy hiểm bị chặn vì IP không hợp lệ!");
                    player.sendMessage(ChatColor.YELLOW + "Vui lòng xác thực IP qua Discord trước khi sử dụng lệnh này.");
                    
                    // Gửi cảnh báo đến Discord
                    String warningMessage = "🚨 **" + player.getName() + "** đã cố gắng sử dụng lệnh nguy hiểm `" + command + "` với IP không hợp lệ!";
                    sendDiscordMessage(warningMessage, Color.ORANGE);
                    return;
                }
            }
            
            // Cho phép các lệnh cơ bản
            if (!command.startsWith("/help") && !command.startsWith("/list") && !command.startsWith("/who") && 
                !command.startsWith("/msg") && !command.startsWith("/tell") && !command.startsWith("/r") &&
                !command.startsWith("/spawn") && !command.startsWith("/home") && !command.startsWith("/tpa") &&
                !command.startsWith("/tpaccept") && !command.startsWith("/tpdeny")) {
                
                // Kiểm tra nếu không phải lệnh nguy hiểm thì cho phép
                boolean isDangerousCommand = false;
                for (String dangerousCmd : dangerousCommands) {
                    if (command.startsWith(dangerousCmd.toLowerCase())) {
                        isDangerousCommand = true;
                        break;
                    }
                }
                
                if (!isDangerousCommand) {
                    // Cho phép lệnh an toàn khác
                    return;
                }
                
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Bạn không thể sử dụng lệnh này vì IP không hợp lệ!");
            }
        }
    }

    // Tách logic kiểm tra thành method riêng
    private void checkPlayerSecurity(Player player, String trigger) {
        String playerName = player.getName();
        
        // Delay kiểm tra để tránh xung đột với các plugin khác
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Kiểm tra player vẫn còn online
            if (!player.isOnline()) {
                return;
            }
            
            String ip = player.getAddress().getAddress().getHostAddress();
            String allowedIP = ipCacheManager.getPlayerIP(playerName);
            boolean hasOP = player.isOp();
            boolean hasDangerousPerms = hasDangerousPermissions(player);
    
            if (hasOP || hasDangerousPerms) {
                if (allowedIP == null) {
                    // Kiểm tra ban chỉ khi chưa đăng ký IP
                    if (ipCacheManager.getDatabaseManager().isPlayerBanned(playerName)) {
                        player.kickPlayer(ChatColor.RED + "Bạn đã bị ban khỏi server vì vi phạm quy định!");
                        return;
                    }
                    
                    kickUnregisteredOp(player, trigger);
                    // Ban người chơi có OP/dangerous perms mà chưa đăng ký IP
                    ipCacheManager.getDatabaseManager().banPlayer(playerName, "Có quyền OP/* nhưng chưa đăng ký IP (" + trigger + ")");
                    return;
                }
    
                boolean isValidIP = allowedIP.equals(ip);
                
                // Gửi thông báo Discord
                sendIPCheckNotification(player, ip, allowedIP, isValidIP, hasOP, hasDangerousPerms, trigger);
    
                if (!isValidIP) {
                    // Thêm vào danh sách hạn chế
                    restrictedPlayers.add(playerName);
                    player.sendMessage(ChatColor.RED + "⚠️ IP không hợp lệ! Bạn bị hạn chế di chuyển và sử dụng lệnh.");
                    player.sendMessage(ChatColor.YELLOW + "Vui lòng liên hệ admin qua Discord để cập nhật IP.");
                    
                    // Ban người chơi có IP không hợp lệ
                    ipCacheManager.getDatabaseManager().banPlayer(playerName, "IP không hợp lệ: " + ip + " (đăng ký: " + allowedIP + ", " + trigger + ")");
                    
                    // Delay thêm cho việc kick để đảm bảo player đã load hoàn toàn
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline()) {
                            player.kickPlayer(ChatColor.RED + "IP không hợp lệ!\nIP hiện tại: " + ip + "\nIP đăng ký: " + allowedIP + "\n\nVui lòng liên hệ admin qua Discord.");
                        }
                    }, 60L); // Kick sau 3 giây (60 ticks)
                } else {
                    // IP hợp lệ, xóa khỏi danh sách hạn chế nếu có
                    restrictedPlayers.remove(playerName);
                }
            }
        }, 40L); // Delay 2 giây (40 ticks) trước khi kiểm tra
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (restrictedPlayers.contains(player.getName())) {
            // Hủy di chuyển
            event.setCancelled(true);
            if (Math.random() < 0.2) { // Chỉ gửi message 20% thời gian để tránh spam
                player.sendMessage(ChatColor.RED + "Bạn không thể di chuyển vì IP không hợp lệ!");
            }
        }
    }

    @EventHandler
    public void onPlayerChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        if (restrictedPlayers.contains(player.getName())) {
            // Cho phép chat nhưng thêm prefix cảnh báo
            event.setFormat(ChatColor.RED + "[IP INVALID] " + ChatColor.RESET + event.getFormat());
        }
    }

    private boolean hasDangerousPermissions(Player player) {
        if (luckPerms == null) return false;
        
        try {
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user == null) return false;
            
            // Kiểm tra quyền * trước
            if (user.getCachedData().getPermissionData(QueryOptions.defaultContextualOptions())
                    .checkPermission("*").asBoolean()) {
                return true;
            }
            
            // Kiểm tra từng quyền nguy hiểm cụ thể
            for (String permission : dangerousPermissions) {
                // Kiểm tra quyền chính xác (không dùng startsWith)
                if (permission.endsWith("*")) {
                    // Đối với quyền wildcard như "bukkit.*", kiểm tra prefix
                    String prefix = permission.substring(0, permission.length() - 1);
                    if (user.getCachedData().getPermissionData(QueryOptions.defaultContextualOptions())
                            .checkPermission(permission).asBoolean()) {
                        return true;
                    }
                    // Kiểm tra thêm các quyền con có prefix này
                    for (String userPerm : user.getCachedData().getPermissionData(QueryOptions.defaultContextualOptions()).getPermissionMap().keySet()) {
                        if (userPerm.startsWith(prefix) && 
                            user.getCachedData().getPermissionData(QueryOptions.defaultContextualOptions())
                                .checkPermission(userPerm).asBoolean()) {
                            return true;
                        }
                    }
                } else {
                    // Đối với quyền cụ thể, kiểm tra chính xác
                    if (user.getCachedData().getPermissionData(QueryOptions.defaultContextualOptions())
                            .checkPermission(permission).asBoolean()) {
                        return true;
                    }
                }
            }
            
            return false;
        } catch (Exception e) {
            Bukkit.getLogger().severe("[LeDatAntiOP] Lỗi khi kiểm tra quyền: " + e.getMessage());
            return false;
        }
    }

    private void kickUnregisteredOp(Player player, String trigger) {
        String message = "**" + player.getName() + "** vừa được cấp OP hoặc quyền nguy hiểm (" + trigger + ") nhưng chưa đăng ký IP! Đã bị kick và ban.";
        sendDiscordMessage(message, Color.ORANGE);
    
        // Tăng delay để đảm bảo player đã load hoàn toàn
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.kickPlayer(ChatColor.RED + "Bạn đã được cấp OP/quyền nguy hiểm nhưng chưa đăng ký IP!\nVui lòng sử dụng /antiop <tên> <ip> trên Discord để xác thực.");
            }
        }, 60L); // Tăng từ 5L lên 60L (3 giây)
    }

    private void sendIPCheckNotification(Player player, String currentIP, String allowedIP, boolean isValid, boolean hasOP, boolean hasDangerousPerms, String trigger) {
        TextChannel channel = jda.getTextChannelById(discordChannelId);
        if (channel != null) {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(isValid ? "✅ Xác thực IP thành công" : "⚠️ Cảnh báo IP không hợp lệ")
                    .setColor(isValid ? Color.GREEN : Color.RED)
                    .addField("Người chơi", player.getName(), false)
                    .addField("IP hiện tại", "`" + currentIP + "`", false)
                    .addField("IP đăng ký", "`" + allowedIP + "`", false)
                    .addField("Quyền", (hasOP ? "OP " : "") + (hasDangerousPerms ? "+ Dangerous Perms" : ""), false)
                    .addField("Trigger", trigger, false)
                    .setFooter("LeDatAntiOP v2.0", null);
            
            if (!isValid) {
                embed.addField("Hành động", "Đã hạn chế di chuyển và lệnh + kick", false);
            }
            
            channel.sendMessageEmbeds(embed.build()).queue();
        }
    }

    private void sendDiscordMessage(String message, Color color) {
        if (jda == null) {
            Bukkit.getLogger().warning("[LeDatAntiOP] JDA chưa được khởi tạo.");
            return;
        }

        TextChannel channel = jda.getTextChannelById(discordChannelId);
        if (channel == null) {
            Bukkit.getLogger().warning("[LeDatAntiOP] Không tìm thấy kênh Discord với ID: " + discordChannelId);
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setDescription(message)
                .setColor(color)
                .setFooter("LeDatAntiOP", null);

        channel.sendMessageEmbeds(embed.build()).queue();
    }
}

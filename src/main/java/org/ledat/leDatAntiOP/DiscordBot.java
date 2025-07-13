package org.ledat.leDatAntiOP;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.awt.*;
import java.util.Map;

public class DiscordBot extends ListenerAdapter {
    private JDA jda;
    private final String channelId;
    private final String commandChannelId;
    private final DatabaseManager databaseManager;
    private final IPCacheManager ipCacheManager;

    public DiscordBot(LeDatAntiOP plugin, org.bukkit.configuration.file.FileConfiguration config, DatabaseManager databaseManager, IPCacheManager ipCacheManager) {
        this.databaseManager = databaseManager;
        this.channelId = config.getString("discord.channel-id");
        this.commandChannelId = config.getString("discord.command-channel-id");
        this.ipCacheManager = ipCacheManager;
        String token = config.getString("discord.token");

        if (token == null || token.isEmpty()) {
            plugin.getLogger().severe("Lỗi: Token Discord không được cấu hình! Kiểm tra `config.yml`.");
            return;
        }

        try {
            jda = net.dv8tion.jda.api.JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_MEMBERS)
                    .addEventListeners(this)
                    .build()
                    .awaitReady();

            registerCommands();
            plugin.getLogger().info("Bot Discord đã kết nối thành công!");

        } catch (InterruptedException e) {
            plugin.getLogger().severe("Lỗi: Bot bị gián đoạn khi kết nối.");
            Thread.currentThread().interrupt();
        }
    }

    private void registerCommands() {
        jda.updateCommands().addCommands(
                Commands.slash("listips", "Xem toàn bộ danh sách IP đã đăng ký"),
                Commands.slash("antiop", "Thêm IP cho một người chơi")
                        .addOption(OptionType.STRING, "player", "Tên người chơi", true)
                        .addOption(OptionType.STRING, "ip", "Địa chỉ IP", true),
                Commands.slash("checkip", "Kiểm tra IP của một người chơi")
                        .addOption(OptionType.STRING, "player", "Tên người chơi", true),
                Commands.slash("resetip", "Xóa IP của một người chơi")
                        .addOption(OptionType.STRING, "player", "Tên người chơi", true),
                Commands.slash("unban", "Unban một người chơi khỏi hệ thống AntiOP")
                        .addOption(OptionType.STRING, "player", "Tên người chơi", true)
        ).queue();
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        MessageChannel channel = jda.getChannelById(MessageChannel.class, commandChannelId);
        if (channel == null) {
            System.out.println("Không tìm thấy kênh Discord có ID: " + commandChannelId);
            return;
        }

        switch (event.getName()) {
            case "antiop":
                handleAntiOp(event, channel);
                break;
            case "checkip":
                handleCheckIP(event, channel);
                break;
            case "resetip":
                handleResetIP(event, channel);
                break;
            case "listips":
                handleListIPs(channel);
                break;
            case "unban":
                handleUnban(event, channel);
                break;
        }
    }

    private void handleAntiOp(SlashCommandInteractionEvent event, MessageChannel channel) {
        String playerName = event.getOption("player").getAsString();
        String ip = event.getOption("ip").getAsString();
        databaseManager.addPlayerIP(playerName, ip);
        ipCacheManager.reloadCache();

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("✅ Đã thêm IP mới")
                .setDescription("**" + playerName + "** → `" + ip + "`")
                .setColor(Color.GREEN)
                .setFooter("LeDatAntiOP", null);
        channel.sendMessageEmbeds(embed.build()).queue();
    }

    private void handleCheckIP(SlashCommandInteractionEvent event, MessageChannel channel) {
        String playerName = event.getOption("player").getAsString();
        String ip = databaseManager.getPlayerIP(playerName);

        EmbedBuilder embed = new EmbedBuilder()
                .setFooter("LeDatAntiOP", null);

        if (ip != null) {
            embed.setTitle("🔍 IP đã đăng ký").setDescription("**" + playerName + "** → `" + ip + "`").setColor(Color.YELLOW);
        } else {
            embed.setTitle("Không tìm thấy").setDescription("Không có IP nào được đăng ký cho **" + playerName + "**.").setColor(Color.RED);
        }
        channel.sendMessageEmbeds(embed.build()).queue();
    }

    private void handleResetIP(SlashCommandInteractionEvent event, MessageChannel channel) {
        String playerName = event.getOption("player").getAsString();
        boolean success = databaseManager.removePlayerIP(playerName);
        ipCacheManager.reloadCache();

        EmbedBuilder embed = new EmbedBuilder()
                .setFooter("LeDatAntiOP", null);

        if (success) {
            embed.setTitle("Đã xoá IP")
                    .setDescription("Thông tin IP của **" + playerName + "** đã được xoá.")
                    .setColor(Color.GREEN);
        } else {
            embed.setTitle("Xoá thất bại")
                    .setDescription("Không tìm thấy IP đã đăng ký cho **" + playerName + "**.")
                    .setColor(Color.RED);
        }
        channel.sendMessageEmbeds(embed.build()).queue();
    }

    private void handleUnban(SlashCommandInteractionEvent event, MessageChannel channel) {
        String playerName = event.getOption("player").getAsString();
        boolean success = databaseManager.unbanPlayer(playerName);

        EmbedBuilder embed = new EmbedBuilder()
                .setFooter("LeDatAntiOP", null);

        if (success) {
            embed.setTitle("✅ Đã unban thành công")
                    .setDescription("**" + playerName + "** đã được unban khỏi hệ thống AntiOP.")
                    .setColor(Color.GREEN);
        } else {
            embed.setTitle("❌ Unban thất bại")
                    .setDescription("Không tìm thấy **" + playerName + "** trong danh sách ban hoặc đã được unban trước đó.")
                    .setColor(Color.RED);
        }
        
        channel.sendMessageEmbeds(embed.build()).queue();
        event.reply("Đã xử lý lệnh unban!").setEphemeral(true).queue();
    }

    public void sendAlert(String message) {
        MessageChannel channel = jda.getChannelById(MessageChannel.class, channelId);
        if (channel != null) {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("Thông báo hệ thống")
                    .setDescription(message)
                    .setColor(Color.ORANGE)
                    .setFooter("LeDatAntiOP", null);
            channel.sendMessageEmbeds(embed.build()).queue();
        }
    }

    private void handleListIPs(MessageChannel channel) {
        Map<String, String> all = databaseManager.getAllPlayerIPs();

        if (all.isEmpty()) {
            channel.sendMessageEmbeds(createEmbed("Không có IP nào trong cơ sở dữ liệu.", Color.RED).build()).queue();
            return;
        }

        StringBuilder content = new StringBuilder();
        for (Map.Entry<String, String> entry : all.entrySet()) {
            content.append("**").append(entry.getKey()).append("**: `").append(entry.getValue()).append("`\n");
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📋 Danh sách người chơi đã đăng ký IP")
                .setDescription(content.toString())
                .setColor(Color.CYAN)
                .setFooter("LeDatAntiOP", null);

        channel.sendMessageEmbeds(embed.build()).queue();
    }

    private EmbedBuilder createEmbed(String message, Color color) {
        return new EmbedBuilder()
                .setDescription(message)
                .setColor(color)
                .setFooter("LeDatAntiOP", null);
    }

    public void shutdown() {
        if (jda != null) jda.shutdown();
    }

    public JDA getJDA() {
        return jda;
    }
}

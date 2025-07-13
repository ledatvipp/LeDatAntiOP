package org.ledat.leDatAntiOP;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.ledat.leDatAntiOP.commands.ReloadCommand;

public class LeDatAntiOP extends JavaPlugin {
    private DatabaseManager databaseManager;
    private IPCacheManager ipCacheManager;
    private DiscordBot discordBot;
    private static LeDatAntiOP instance;
    private LuckPerms luckPerms;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        setupLuckPerms();
        checkLibraries();
        databaseManager = new DatabaseManager();
        databaseManager.connect(getConfig());
        ipCacheManager = new IPCacheManager(databaseManager);
        discordBot = new DiscordBot(this, getConfig(), databaseManager, ipCacheManager);

        getServer().getPluginManager().registerEvents(
                new PlayerJoinListener(ipCacheManager, discordBot.getJDA(), getConfig().getString("discord.channel-id"), getLuckPerms(), this),
                this
        );
        getCommand("antiopreload").setExecutor(new ReloadCommand(this));
    }

    @Override
    public void onDisable() {
        databaseManager.disconnect();
        discordBot.shutdown();
    }

    private void checkLibraries() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver"); // Kiểm tra MySQL
            Class.forName("org.sqlite.JDBC"); // Kiểm tra SQLite
            Class.forName("com.google.protobuf.Descriptors"); // Kiểm tra Protobuf
            Class.forName("org.slf4j.Logger"); // Kiểm tra SLF4J

        } catch (ClassNotFoundException e) {
            getLogger().severe("Không thể tải một số thư viện cần thiết! Plugin có thể không hoạt động đúng.");
            e.printStackTrace();
        }
    }

    public static LeDatAntiOP getInstance() {
        return instance;
    }

    private void setupLuckPerms() {
        try {
            luckPerms = LuckPermsProvider.get();
            getLogger().info("Đã hook vào LuckPerms!");
        } catch (Exception e) {
            getLogger().warning("Không tìm thấy LuckPerms! Một số tính năng có thể không hoạt động.");
            luckPerms = null;
        }
    }

    public LuckPerms getLuckPerms() {
        return luckPerms;
    }
}

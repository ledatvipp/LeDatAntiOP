package org.ledat.leDatAntiOP;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.Bukkit;

import java.io.File;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class DatabaseManager {
    private HikariDataSource dataSource;
    private boolean useSQLite;

    public void connect(FileConfiguration config) {
        if (dataSource != null) {
            Bukkit.getLogger().warning("[LeDatAntiOP] Database đã kết nối, bỏ qua việc kết nối lại.");
            return;
        }

        // Kiểm tra loại database từ config
        String dbType = config.getString("database.type", "sqlite").toLowerCase();
        useSQLite = dbType.equals("sqlite");

        try {
            HikariConfig hikariConfig = new HikariConfig();
            
            if (useSQLite) {
                setupSQLite(hikariConfig, config);
            } else {
                setupMySQL(hikariConfig, config);
            }

            dataSource = new HikariDataSource(hikariConfig);
            Bukkit.getLogger().info("[LeDatAntiOP] Kết nối " + (useSQLite ? "SQLite" : "MySQL") + " thành công!");

            // Tạo bảng nếu chưa có
            createTables();
        } catch (Exception e) {
            Bukkit.getLogger().severe("[LeDatAntiOP] Lỗi kết nối database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setupSQLite(HikariConfig config, FileConfiguration pluginConfig) {
        File dataFolder = new File(pluginConfig.getString("database.sqlite.path", "plugins/LeDatAntiOP/data.db"));
        dataFolder.getParentFile().mkdirs();
        
        config.setJdbcUrl("jdbc:sqlite:" + dataFolder.getAbsolutePath());
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(1); // SQLite chỉ hỗ trợ 1 connection
    }

    private void setupMySQL(HikariConfig config, FileConfiguration pluginConfig) {
        String host = pluginConfig.getString("database.mysql.host");
        int port = pluginConfig.getInt("database.mysql.port");
        String database = pluginConfig.getString("database.mysql.database");
        String username = pluginConfig.getString("database.mysql.username");
        String password = pluginConfig.getString("database.mysql.password");
        boolean useSSL = pluginConfig.getBoolean("database.mysql.use-ssl", false);

        if (host == null || database == null || username == null || password == null) {
            throw new RuntimeException("Cấu hình MySQL không đầy đủ. Kiểm tra file config.yml.");
        }

        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=" + useSSL + "&autoReconnect=true");
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(10);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
    }

    private void createTables() {
        String allowedIPsTable;
        String bannedPlayersTable;
        
        if (useSQLite) {
            allowedIPsTable = "CREATE TABLE IF NOT EXISTS AllowedIPs (player TEXT PRIMARY KEY, ip TEXT NOT NULL);";
            bannedPlayersTable = "CREATE TABLE IF NOT EXISTS BannedPlayers (player TEXT PRIMARY KEY, reason TEXT, banned_at INTEGER);";
        } else {
            allowedIPsTable = "CREATE TABLE IF NOT EXISTS AllowedIPs (player VARCHAR(50) PRIMARY KEY, ip VARCHAR(50) NOT NULL);";
            bannedPlayersTable = "CREATE TABLE IF NOT EXISTS BannedPlayers (player VARCHAR(50) PRIMARY KEY, reason TEXT, banned_at BIGINT);";
        }

        try (Connection connection = getConnection()) {
            // Tạo bảng AllowedIPs
            try (PreparedStatement stmt = connection.prepareStatement(allowedIPsTable)) {
                stmt.executeUpdate();
            }
            
            // Tạo bảng BannedPlayers
            try (PreparedStatement stmt = connection.prepareStatement(bannedPlayersTable)) {
                stmt.executeUpdate();
            }
            
            Bukkit.getLogger().info("[LeDatAntiOP] Đã kiểm tra/tạo các bảng database.");
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[LeDatAntiOP] Lỗi khi tạo bảng: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public Map<String, String> getAllPlayerIPs() {
        Map<String, String> map = new HashMap<>();
        String sql = "SELECT player, ip FROM AllowedIPs";

        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                map.put(rs.getString("player"), rs.getString("ip"));
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[LeDatAntiOP] Lỗi khi lấy danh sách IP: " + e.getMessage());
            e.printStackTrace();
        }

        return map;
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("Kết nối MySQL chưa được thiết lập.");
        }
        return dataSource.getConnection();
    }

    public void disconnect() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
            Bukkit.getLogger().info("[LeDatAntiOP] Đã đóng kết nối MySQL.");
        }
    }

    public void addPlayerIP(String player, String ip) {
        String sql = useSQLite ? 
            "INSERT OR REPLACE INTO AllowedIPs (player, ip) VALUES (?, ?)" :
            "INSERT INTO AllowedIPs (player, ip) VALUES (?, ?) ON DUPLICATE KEY UPDATE ip = ?";
            
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, player);
            ps.setString(2, ip);
            if (!useSQLite) {
                ps.setString(3, ip);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[LeDatAntiOP] ❌ Lỗi khi thêm IP: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public String getPlayerIP(String player) {
        String sql = "SELECT ip FROM AllowedIPs WHERE player = ?";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, player);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("ip");
            }
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[LeDatAntiOP] Lỗi khi lấy IP: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    public boolean removePlayerIP(String player) {
        String sql = "DELETE FROM AllowedIPs WHERE player = ?";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, player);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[LeDatAntiOP] Lỗi khi xoá IP: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    public void banPlayer(String player, String reason) {
        String sql = "INSERT OR REPLACE INTO BannedPlayers (player, reason, banned_at) VALUES (?, ?, ?)";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, player);
            ps.setString(2, reason);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[LeDatAntiOP] Lỗi khi ban người chơi: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public boolean isPlayerBanned(String player) {
        String sql = "SELECT 1 FROM BannedPlayers WHERE player = ?";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, player);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[LeDatAntiOP] Lỗi khi kiểm tra ban: " + e.getMessage());
            return false;
        }
    }

    public boolean unbanPlayer(String player) {
        String sql = "DELETE FROM BannedPlayers WHERE player = ?";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, player);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            Bukkit.getLogger().severe("[LeDatAntiOP] Lỗi khi unban: " + e.getMessage());
            return false;
        }
    }
}

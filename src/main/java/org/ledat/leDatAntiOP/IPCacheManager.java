package org.ledat.leDatAntiOP;

import org.bukkit.Bukkit;

import java.util.HashMap;
import java.util.Map;

public class IPCacheManager {
    private final Map<String, String> ipCache = new HashMap<>();
    private final DatabaseManager databaseManager;

    public IPCacheManager(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        loadCache();
    }

    private void loadCache() {
        try {
            ipCache.putAll(databaseManager.getAllPlayerIPs());
            Bukkit.getLogger().info("[LeDatAntiOP] Đã tải " + ipCache.size() + " IP vào cache.");
        } catch (Exception e) {
            Bukkit.getLogger().severe("[LeDatAntiOP] Lỗi khi tải cache: " + e.getMessage());
        }
    }

    public void addPlayerIP(String player, String ip) {
        ipCache.put(player, ip);
        databaseManager.addPlayerIP(player, ip);
    }

    public String getPlayerIP(String player) {
        return ipCache.get(player);
    }

    public boolean removePlayerIP(String player) {
        ipCache.remove(player);
        return databaseManager.removePlayerIP(player);
    }

    public Map<String, String> getAllPlayerIPs() {
        return new HashMap<>(ipCache);
    }

    public void reloadCache() {
        ipCache.clear();
        loadCache();
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
}

package org.ledat.leDatAntiOP.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.ledat.leDatAntiOP.LeDatAntiOP;

public class ReloadCommand implements CommandExecutor {
    private final LeDatAntiOP plugin;

    public ReloadCommand(LeDatAntiOP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        plugin.reloadConfig();
        sender.sendMessage(ChatColor.GREEN + "Plugin đã reload!");
        return true;
    }
}

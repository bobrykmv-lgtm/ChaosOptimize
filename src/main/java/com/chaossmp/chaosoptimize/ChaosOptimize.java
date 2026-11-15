package com.chaossmp.chaosoptimize;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class ChaosOptimize extends JavaPlugin implements Listener, CommandExecutor {

    private final Map<UUID, String> lastPlayerMessages = new HashMap<>();
    private final Map<UUID, Long> lastMessageTimes = new HashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        
        // Регистрация команд
        if (getCommand("chaosoptimize") != null) {
            getCommand("chaosoptimize").setExecutor(this);
        }

        startSmartCleanerTask();
        getLogger().info("ChaosOptimize успешно запущен!");
    }

    @Override
    public void onDisable() {
        lastPlayerMessages.clear();
        lastMessageTimes.clear();
        getLogger().info("ChaosOptimize выключен.");
    }

    // --- MODULE 1: AUTO PICKUP (Только для мобов, блоки падают реально на землю) ---

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        List<ItemStack> drops = event.getDrops();
        Iterator<ItemStack> iterator = drops.iterator();

        while (iterator.hasNext()) {
            ItemStack stack = iterator.next();
            HashMap<Integer, ItemStack> leftover = killer.getInventory().addItem(stack);
            if (leftover.isEmpty()) {
                iterator.remove();
            } else {
                stack.setAmount(leftover.get(0).getAmount());
            }
        }

        if (event.getDroppedExp() > 0) {
            killer.giveExp(event.getDroppedExp());
            event.setDroppedExp(0);
        }
    }

    // --- MODULE 2: SMART CLEANER (Безопасная очистка мусора) ---

    private void startSmartCleanerTask() {
        // Каждые 10 минут (12000 тиков)
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            
            sendActionBarToAll("§e[ChaosOptimize] §fОчистка мусора через §c30 секунд§f!");

            Bukkit.getScheduler().runTaskLater(this, () -> {
                sendActionBarToAll("§e[ChaosOptimize] §fОчистка мусора через §c10 секунд§f!");
            }, 400L); // на 20 секунд позже

            Bukkit.getScheduler().runTaskLater(this, () -> {
                int removed = executeClean();
                sendActionBarToAll("§a[ChaosOptimize] §fОчистка завершена! Удалено предметов: §e" + removed);
            }, 600L); // на 30 секунд позже

        }, 12000L, 12000L);
    }

    public int executeClean() {
        int count = 0;
        for (var world : Bukkit.getWorlds()) {
            for (Item item : world.getEntitiesByClass(Item.class)) {
                ItemStack stack = item.getItemStack();
                Material type = stack.getType();

                // Не удаляем ценные вещи и кастомные предметы с названиями
                if (isValuable(type) || (stack.hasItemMeta() && stack.getItemMeta().hasDisplayName())) {
                    continue;
                }

                item.remove();
                count++;
            }
        }
        return count;
    }

    private boolean isValuable(Material type) {
        String name = type.name();
        return name.contains("DIAMOND") || name.contains("NETHERITE") ||
               type == Material.NETHER_STAR || type == Material.TOTEM_OF_UNDYING ||
               type == Material.ELYTRA || type == Material.ENCHANTED_GOLDEN_APPLE ||
               type == Material.BEACON || type == Material.SHULKER_BOX;
    }

    private void sendActionBarToAll(String text) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(text));
        }
    }

    // --- MODULE 3: ANTI-SPAM CHAT (Защита от спама и обхода мута) ---

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String currentMsg = event.getMessage().trim();

        String lastMsg = lastPlayerMessages.get(uuid);
        long lastTime = lastMessageTimes.getOrDefault(uuid, 0L);
        long currentTime = System.currentTimeMillis();

        // Если игрок отправляет то же сообщение чаще чем раз в 4 секунды — блокируем
        if (currentMsg.equalsIgnoreCase(lastMsg) && (currentTime - lastTime < 4000)) {
            event.setCancelled(true);
            player.sendMessage("§c[ChaosOptimize] §fНе спамь одинаковыми сообщениями!");
            return;
        }

        lastPlayerMessages.put(uuid, currentMsg);
        lastMessageTimes.put(uuid, currentTime);
    }

    // --- MODULE 4: NEW FEATURE (Админ-команда для управления оптимизацией) ---

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("chaosoptimize.admin")) {
            sender.sendMessage("§cУ вас нет прав на выполнение этой команды.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("clean")) {
            int removed = executeClean();
            sender.sendMessage("§a[ChaosOptimize] §fПринудительная очистка завершена. Удалено предметов: §e" + removed);
            return true;
        }

        sender.sendMessage("§e=== ChaosOptimize Commands ===");
        sender.sendMessage("§f/chaosoptimize clean §7- Принудительно очистить мусор на сервере");
        return true;
    }
}

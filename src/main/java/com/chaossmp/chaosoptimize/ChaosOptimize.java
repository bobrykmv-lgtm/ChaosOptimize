package com.chaossmp.chaosoptimize;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class ChaosOptimize extends JavaPlugin implements Listener {

    private final Map<UUID, String> lastPlayerMessages = new HashMap<>();
    private final Map<UUID, Integer> lastMessageCounts = new HashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        startSmartCleanerTask();
        getLogger().info("ChaosOptimize успешно запущен!");
    }

    @Override
    public void onDisable() {
        lastPlayerMessages.clear();
        lastMessageCounts.clear();
        getLogger().info("ChaosOptimize выключен.");
    }

    // --- MODULE 1: AUTO PICKUP (Авто-збір у інвентар) ---

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDrop(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        List<Item> items = event.getItems();

        Iterator<Item> iterator = items.iterator();
        while (iterator.hasNext()) {
            Item itemEntity = iterator.next();
            ItemStack stack = itemEntity.getItemStack();

            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            if (leftover.isEmpty()) {
                iterator.remove();
            } else {
                itemEntity.setItemStack(leftover.get(0));
            }
        }
    }

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

    // --- MODULE 2: SMART CLEANER (Безпечне очищення сміття) ---

    private void startSmartCleanerTask() {
        // Кожні 10 хвилин (12000 тиків)
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            
            sendActionBarToAll("§e[ChaosOptimize] §fОчистка мусора через §c30 секунд§f!");

            Bukkit.getScheduler().runTaskLater(this, () -> {
                sendActionBarToAll("§e[ChaosOptimize] §fОчистка мусора через §c10 секунд§f!");
            }, 400L); // 20 секунд пізніше

            Bukkit.getScheduler().runTaskLater(this, () -> {
                int removed = executeClean();
                sendActionBarToAll("§a[ChaosOptimize] §fОчистка завершена! Удалено предметов: §e" + removed);
            }, 600L); // 30 секунд пізніше

        }, 12000L, 12000L);
    }

    private int executeClean() {
        int count = 0;
        for (var world : Bukkit.getWorlds()) {
            for (Item item : world.getEntitiesByClass(Item.class)) {
                ItemStack stack = item.getItemStack();
                Material type = stack.getType();

                // Не видаляємо цінні речі та кастомні предмети з назвами
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

    // --- MODULE 3: COMPACT CHAT (Групування однакових повідомлень) ---

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String currentMsg = event.getMessage().trim();

        String lastMsg = lastPlayerMessages.get(uuid);
        if (currentMsg.equalsIgnoreCase(lastMsg)) {
            int count = lastMessageCounts.getOrDefault(uuid, 1) + 1;
            lastMessageCounts.put(uuid, count);

            event.setFormat(player.getDisplayName() + "§7: §f" + currentMsg + " §8(x" + count + ")");
        } else {
            lastPlayerMessages.put(uuid, currentMsg);
            lastMessageCounts.put(uuid, 1);
        }
    }
}

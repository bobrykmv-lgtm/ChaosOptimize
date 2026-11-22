package com.chaossmp.chaosoptimize;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class ChaosOptimize extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Map<UUID, String> lastPlayerMessages = new HashMap<>();
    private final Map<UUID, Long> lastMessageTimes = new HashMap<>();

    // Состояние модулей (критический режим отключен)
    private final boolean criticalMode = false;
    private boolean autoPickupEnabled = true;
    private boolean smartCleanerEnabled = true;
    private boolean antiSpamEnabled = true;

    private static final String GUI_TITLE_RAW = "<gradient:#ff4b2b:#ff416c><bold>ChaosOptimize</bold></gradient> <dark_gray>| <gray>Панель";

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);

        var cmd = getCommand("chaosoptimize");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }

        startSmartCleanerTask();
        getLogger().info("ChaosOptimize запущен! Режим критического состояния: ВЫКЛ.");
    }

    @Override
    public void onDisable() {
        lastPlayerMessages.clear();
        lastMessageTimes.clear();
        getLogger().info("ChaosOptimize выключен.");
    }

    // --- MODULE 1: AUTO PICKUP (Только лут с мобов) ---

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!autoPickupEnabled) return;

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

    // --- MODULE 2: SMART CLEANER (Мягкая очистка мусора с красивым ActionBar) ---

    private void startSmartCleanerTask() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!smartCleanerEnabled) return;

            sendActionBarToAll("<gradient:#ff7b00:#ffcc00><bold>⚡ ChaosOptimize</bold></gradient> <dark_gray>» <gray>Очистка мусора через <red><bold>30 сек.</bold></red>");

            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!smartCleanerEnabled) return;
                sendActionBarToAll("<gradient:#ff7b00:#ffcc00><bold>⚡ ChaosOptimize</bold></gradient> <dark_gray>» <gray>Очистка мусора через <red><bold>10 сек.</bold></red>");
            }, 400L);

            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!smartCleanerEnabled) return;
                int removed = executeClean();
                sendActionBarToAll("<gradient:#00f260:#0575e6><bold>✔ ChaosOptimize</bold></gradient> <dark_gray>» <gray>Очищено предметов: <yellow><bold>" + removed + "</bold></yellow>");
            }, 600L);

        }, 12000L, 12000L);
    }

    public int executeClean() {
        int count = 0;
        for (var world : Bukkit.getWorlds()) {
            for (Item item : world.getEntitiesByClass(Item.class)) {
                ItemStack stack = item.getItemStack();
                Material type = stack.getType();

                // Защита ценных и кастомных предметов (ExecutableItems и т.д.)
                if (isValuable(type) || (stack.hasItemMeta() && (stack.getItemMeta().hasDisplayName() || stack.getItemMeta().hasLore()))) {
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
        return name.contains("DIAMOND") || name.contains("NETHERITE") || name.contains("SHULKER_BOX") ||
               type == Material.NETHER_STAR || type == Material.TOTEM_OF_UNDYING ||
               type == Material.ELYTRA || type == Material.ENCHANTED_GOLDEN_APPLE ||
               type == Material.BEACON || type == Material.MACE || type == Material.HEAVY_CORE;
    }

    private void sendActionBarToAll(String miniMessageText) {
        Component comp = mm.deserialize(miniMessageText);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendActionBar(comp);
        }
    }

    // --- MODULE 3: ANTI-SPAM CHAT + Очистка памяти при выходе ---

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        if (!antiSpamEnabled) return;

        Player player = event.getPlayer();
        if (player.hasPermission("chaosoptimize.bypass")) return;

        UUID uuid = player.getUniqueId();
        String currentMsg = event.getMessage().trim();

        String lastMsg = lastPlayerMessages.get(uuid);
        long lastTime = lastMessageTimes.getOrDefault(uuid, 0L);
        long currentTime = System.currentTimeMillis();

        if (currentMsg.equalsIgnoreCase(lastMsg) && (currentTime - lastTime < 4000)) {
            event.setCancelled(true);
            player.sendMessage(mm.deserialize("<gradient:#ff4b2b:#ff416c><bold>✖ ChaosOptimize</bold></gradient> <dark_gray>» <gray>Подождите немного перед повтором сообщения."));
            return;
        }

        lastPlayerMessages.put(uuid, currentMsg);
        lastMessageTimes.put(uuid, currentTime);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastPlayerMessages.remove(uuid);
        lastMessageTimes.remove(uuid);
    }

    // --- MODULE 4: ПРИЯТНЫЙ GUI-ИНТЕРФЕЙС И КОМАНДЫ ---

    public void openMainMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, mm.deserialize(GUI_TITLE_RAW));

        // Декоративная рамка из тёмного стекла
        ItemStack glass = createGuiItem(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < 27; i++) {
            gui.setItem(i, glass);
        }

        // Расчёт показателей сервера
        double tps = Math.min(20.0, Bukkit.getTPS()[0]);
        Runtime rt = Runtime.getRuntime();
        long usedMem = (rt.totalMemory() - rt.freeMemory()) / 1048576L;
        long maxMem = rt.maxMemory() / 1048576L;
        int itemsCount = Bukkit.getWorlds().stream().mapToInt(w -> w.getEntitiesByClass(Item.class).size()).sum();

        // Слот 10: Статус сервера (Критический режим выключен)
        gui.setItem(10, createGuiItem(
                Material.HEART_OF_THE_SEA,
                "<gradient:#00f260:#0575e6><bold>Состояние сервера</bold></gradient>",
                List.of(
                        "<dark_gray>────────────────────",
                        "<gray>Текущий TPS: <green>" + String.format("%.2f", tps),
                        "<gray>Память RAM: <yellow>" + usedMem + " MB <dark_gray>/ <gray>" + maxMem + " MB",
                        "<gray>Предметов на земле: <aqua>" + itemsCount,
                        "<gray>Критический режим: <red><bold>ВЫКЛЮЧЕН</bold></red>",
                        "<dark_gray>────────────────────"
                )
        ));

        // Слот 12: Авто-подбор лута
        gui.setItem(12, createGuiItem(
                autoPickupEnabled ? Material.HOPPER : Material.BARRIER,
                "<gold><bold>Авто-подбор лута</bold></gold>",
                List.of(
                        "<gray>Лут и опыт с мобов сразу",
                        "<gray>попадают в инвентарь игрока.",
                        "",
                        "<gray>Статус: " + (autoPickupEnabled ? "<green><bold>ВКЛЮЧЕНО</bold>" : "<red><bold>ВЫКЛЮЧЕНО</bold>"),
                        "<yellow>▸ Нажмите, чтобы переключить"
                )
        ));

        // Слот 13: Принудительная очистка
        gui.setItem(13, createGuiItem(
                Material.BRUSH,
                "<gradient:#ff7b00:#ffcc00><bold>Очистить мусор сейчас</bold></gradient>",
                List.of(
                        "<gray>Удаляет выброшенный хлам,",
                        "<gray>сохраняя алмазы, незерит,",
                        "<gray>булавы и кастомные вещи.",
                        "",
                        "<yellow>▸ Нажмите для очистки"
                )
        ));

        // Слот 14: Авто-очистка по таймеру
        gui.setItem(14, createGuiItem(
                smartCleanerEnabled ? Material.CLOCK : Material.BARRIER,
                "<aqua><bold>Авто-очистка (10 мин)</bold></aqua>",
                List.of(
                        "<gray>Плановая очистка предметов",
                        "<gray>с уведомлением в ActionBar.",
                        "",
                        "<gray>Статус: " + (smartCleanerEnabled ? "<green><bold>ВКЛЮЧЕНО</bold>" : "<red><bold>ВЫКЛЮЧЕНО</bold>"),
                        "<yellow>▸ Нажмите, чтобы переключить"
                )
        ));

        // Слот 16: Анти-спам чата
        gui.setItem(16, createGuiItem(
                antiSpamEnabled ? Material.WRITABLE_BOOK : Material.BARRIER,
                "<light_purple><bold>Анти-спам чата</bold></light_purple>",
                List.of(
                        "<gray>Защита от повторяющихся",
                        "<gray>сообщений чаще раза в 4 сек.",
                        "",
                        "<gray>Статус: " + (antiSpamEnabled ? "<green><bold>ВКЛЮЧЕНО</bold>" : "<red><bold>ВЫКЛЮЧЕНО</bold>"),
                        "<yellow>▸ Нажмите, чтобы переключить"
                )
        ));

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
    }

    private ItemStack createGuiItem(Material material, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(mm.deserialize("<!italic>" + name));
            if (!loreLines.isEmpty()) {
                List<Component> lore = new ArrayList<>();
                for (String line : loreLines) {
                    lore.add(mm.deserialize("<!italic>" + line));
                }
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().title().equals(mm.deserialize(GUI_TITLE_RAW))) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!player.hasPermission("chaosoptimize.admin")) return;

        int slot = event.getRawSlot();
        switch (slot) {
            case 12 -> {
                autoPickupEnabled = !autoPickupEnabled;
                openMainMenu(player);
            }
            case 13 -> {
                int removed = executeClean();
                player.closeInventory();
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.3f);
                player.sendMessage(mm.deserialize("<gradient:#00f260:#0575e6><bold>✔ ChaosOptimize</bold></gradient> <dark_gray>» <gray>Очистка завершена! Удалено предметов: <yellow><bold>" + removed + "</bold></yellow>"));
            }
            case 14 -> {
                smartCleanerEnabled = !smartCleanerEnabled;
                openMainMenu(player);
            }
            case 16 -> {
                antiSpamEnabled = !antiSpamEnabled;
                openMainMenu(player);
            }
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("chaosoptimize.admin")) {
            sender.sendMessage(mm.deserialize("<gradient:#ff4b2b:#ff416c><bold>✖ ChaosOptimize</bold></gradient> <dark_gray>» <gray>У вас нет прав на эту команду."));
            return true;
        }

        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("clean")) {
                int removed = executeClean();
                sender.sendMessage(mm.deserialize("<gradient:#00f260:#0575e6><bold>✔ ChaosOptimize</bold></gradient> <dark_gray>» <gray>Принудительная очистка завершена! Удалено: <yellow><bold>" + removed + "</bold></yellow>"));
                return true;
            }
            if (args[0].equalsIgnoreCase("menu") && sender instanceof Player p) {
                openMainMenu(p);
                return true;
            }
        }

        if (sender instanceof Player player) {
            openMainMenu(player);
        } else {
            sender.sendMessage("Используйте /chaosoptimize clean в консоли.");
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1 && sender.hasPermission("chaosoptimize.admin")) {
            return List.of("menu", "clean");
        }
        return List.of();
    }
}

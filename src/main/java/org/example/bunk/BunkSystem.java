package org.example.bunk;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class BunkSystem extends JavaPlugin implements Listener, TabCompleter {
    private Material serverCurrency = Material.DIAMOND;
    private Map<UUID, Integer> playerBalances = new HashMap<>();
    private Map<Player, Boolean> awaitingDeposit = new HashMap<>();
    private Map<Player, Boolean> awaitingWithdraw = new HashMap<>();
    private String bankName = "§aБанк";
    private File balanceFile;
    private FileConfiguration balanceConfig;
    private File transactionsFile;
    private FileConfiguration transactionsConfig;
    private final int TRANSACTIONS_PER_PAGE = 45;
    private BankAPI bankAPI;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("bank").setTabCompleter(this);
        getCommand("setcurrency").setTabCompleter(this);
        loadConfig();
        loadBalances();
        loadTransactions();
        this.bankAPI = new BankAPI(this);
        getLogger().info("BunkSystem включен!");
    }

    @Override
    public void onDisable() {
        saveBalances();
        saveTransactions();
        saveConfig();
    }

    private void loadConfig() {
        FileConfiguration config = getConfig();
        this.bankName = config.getString("bankName", "§aБанк");
        String currencyName = config.getString("currency", "DIAMOND");
        try {
            this.serverCurrency = Material.valueOf(currencyName);
        } catch (IllegalArgumentException e) {
            getLogger().warning("Неверный тип валюты в конфиге, установлен DIAMOND!");
            this.serverCurrency = Material.DIAMOND;
        }
        saveConfig();
    }

    private void loadBalances() {
        this.balanceFile = new File(getDataFolder(), "balances.yml");
        if (!this.balanceFile.exists()) {
            try {
                this.balanceFile.getParentFile().mkdirs();
                this.balanceFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Не удалось создать balances.yml: " + e.getMessage());
            }
        }
        this.balanceConfig = YamlConfiguration.loadConfiguration(this.balanceFile);
        this.playerBalances.clear();
        for (String key : this.balanceConfig.getKeys(false)) {
            this.playerBalances.put(UUID.fromString(key), this.balanceConfig.getInt(key));
        }
    }

    private void saveBalances() {
        for (Map.Entry<UUID, Integer> entry : this.playerBalances.entrySet()) {
            this.balanceConfig.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            this.balanceConfig.save(this.balanceFile);
        } catch (IOException e) {
            getLogger().severe("Не удалось сохранить balances.yml: " + e.getMessage());
        }
    }

    private void loadTransactions() {
        this.transactionsFile = new File(getDataFolder(), "transactions.yml");
        if (!this.transactionsFile.exists()) {
            try {
                this.transactionsFile.getParentFile().mkdirs();
                this.transactionsFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Не удалось создать transactions.yml: " + e.getMessage());
            }
        }
        this.transactionsConfig = YamlConfiguration.loadConfiguration(this.transactionsFile);
    }

    private void saveTransactions() {
        try {
            this.transactionsConfig.save(this.transactionsFile);
        } catch (IOException e) {
            getLogger().severe("Не удалось сохранить transactions.yml: " + e.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("bank")) {
            if (args.length > 0) {
                if (args[0].equalsIgnoreCase("reload")) {
                    if (!sender.hasPermission("bankplugin.admin")) {
                        sender.sendMessage("§cУ вас нет прав!");
                        return true;
                    }
                    reloadPlugin();
                    sender.sendMessage("§aПлагин перезагружен!");
                    return true;
                }
                if (args[0].equalsIgnoreCase("top")) {
                    showTopBalances(sender);
                    return true;
                }
            }
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cТолько для игроков!");
                return true;
            }
            Player player = (Player) sender;
            openBankMenu(player);
            return true;
        }

        if (command.getName().equalsIgnoreCase("bhelp")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cТолько для игроков!");
                return true;
            }
            Player player = (Player) sender;
            showHelpMessage(player);
            return true;
        }

        if (command.getName().equalsIgnoreCase("setcurrency")) {
            if (!(sender instanceof Player) || !sender.hasPermission("bankplugin.admin")) {
                sender.sendMessage("§cНет прав или не игрок!");
                return true;
            }
            Player player = (Player) sender;
            if (args.length != 1) {
                player.sendMessage("§c/setcurrency <diamond|emerald|gold|diamond_ore>");
                return true;
            }
            switch (args[0].toLowerCase()) {
                case "diamond":
                    this.serverCurrency = Material.DIAMOND;
                    break;
                case "emerald":
                    this.serverCurrency = Material.EMERALD;
                    break;
                case "gold":
                    this.serverCurrency = Material.GOLD_INGOT;
                    break;
                case "diamond_ore":
                    this.serverCurrency = Material.DIAMOND_ORE;
                    break;
                default:
                    player.sendMessage("§cУкажи diamond, emerald, gold или diamond_ore!");
                    return true;
            }
            getConfig().set("currency", this.serverCurrency.name());
            saveConfig();
            player.sendMessage("§aВалюта установлена: " + this.serverCurrency);
            Bukkit.broadcastMessage("§aВалюта сервера изменена на: " + this.serverCurrency);
            return true;
        }

        if (command.getName().equalsIgnoreCase("setbankname")) {
            if (!(sender instanceof Player) || !sender.hasPermission("bankplugin.admin")) {
                sender.sendMessage("§cНет прав или не игрок!");
                return true;
            }
            Player player = (Player) sender;
            if (args.length < 1) {
                player.sendMessage("§c/setbankname <новое имя>");
                return true;
            }
            this.bankName = String.join(" ", args);
            getConfig().set("bankName", this.bankName);
            saveConfig();
            player.sendMessage("§aИмя банка: " + this.bankName);
            return true;
        }

        if (command.getName().equalsIgnoreCase("balance")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cТолько для игроков!");
                return true;
            }
            Player player = (Player) sender;
            int balance = playerBalances.getOrDefault(player.getUniqueId(), 0);
            player.sendMessage("§aВаш баланс: " + balance + " " + this.serverCurrency);
            return true;
        }

        if (command.getName().equalsIgnoreCase("transfer")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cТолько для игроков!");
                return true;
            }
            Player player = (Player) sender;
            if (args.length != 2) {
                player.sendMessage("§c/transfer <игрок> <сумма>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendMessage("§cИгрок " + args[0] + " не найден!");
                return true;
            }
            try {
                int amount = Integer.parseInt(args[1]);
                processTransfer(player, target, amount);
            } catch (NumberFormatException e) {
                player.sendMessage("§cНеверная сумма!");
            }
            return true;
        }
        return false;
    }

    private void showHelpMessage(Player player) {
        player.sendMessage("§aКоманды банка:");
        player.sendMessage("§e/bank §7- Открыть меню банка");
        player.sendMessage("§e/bank top §7- Топ-10 по балансу");
        player.sendMessage("§e/balance §7- Ваш баланс");
        player.sendMessage("§e/transfer <игрок> <сумма> §7- Перевод денег");
        if (player.hasPermission("bankplugin.admin")) {
            player.sendMessage("§e/setcurrency <diamond|emerald|gold|diamond_ore> §7- Сменить валюту");
            player.sendMessage("§e/setbankname <имя> §7- Сменить имя банка");
            player.sendMessage("§e/bank reload §7- Перезагрузить плагин");
        }
    }

    private void showTopBalances(CommandSender sender) {
        List<Map.Entry<UUID, Integer>> topList = playerBalances.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .limit(10)
                .collect(Collectors.toList());
        sender.sendMessage("§aТоп-10 богатых игроков:");
        int rank = 1;
        for (Map.Entry<UUID, Integer> entry : topList) {
            String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            if (name == null) name = "Неизвестный";
            sender.sendMessage("§e" + rank + ". " + name + " - " + entry.getValue() + " " + serverCurrency);
            rank++;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("setcurrency") && args.length == 1) {
            return Arrays.asList("diamond", "emerald", "gold", "diamond_ore");
        }
        if (command.getName().equalsIgnoreCase("bank") && args.length == 1) {
            List<String> options = new ArrayList<>(Arrays.asList("top"));
            if (sender.hasPermission("bankplugin.admin")) options.add("reload");
            return options;
        }
        return null;
    }

    private void reloadPlugin() {
        reloadConfig();
        loadConfig();
        loadBalances();
        loadTransactions();
    }

    private void openBankMenu(Player player) {
        Inventory bankMenu = Bukkit.createInventory(null, 27, this.bankName);
        ItemStack depositItem = createCustomItem(Material.GREEN_WOOL, "§aПополнить " + this.serverCurrency, 1001);
        ItemStack withdrawItem = createCustomItem(Material.RED_WOOL, "§cСнять " + this.serverCurrency, 1002);
        ItemStack balanceItem = createCustomItem(this.serverCurrency, "§eБаланс: " + playerBalances.getOrDefault(player.getUniqueId(), 0) + " " + this.serverCurrency, 1003);
        ItemStack transactionsItem = createCustomItem(Material.BOOK, "§bИстория транзакций", 1004);
        bankMenu.setItem(11, depositItem);
        bankMenu.setItem(13, balanceItem);
        bankMenu.setItem(15, withdrawItem);
        bankMenu.setItem(22, transactionsItem);
        player.openInventory(bankMenu);
    }

    private ItemStack createCustomItem(Material material, String name, int customModelData) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name + ChatColor.WHITE);
        meta.setCustomModelData(customModelData);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals(this.bankName)) {
            event.setCancelled(true);
            Player player = (Player) event.getWhoClicked();
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null) return;
            if (clickedItem.getType() == Material.GREEN_WOOL) {
                player.closeInventory();
                askForDepositAmount(player);
            } else if (clickedItem.getType() == Material.RED_WOOL) {
                player.closeInventory();
                askForWithdrawAmount(player);
            } else if (clickedItem.getType() == Material.BOOK) {
                openTransactionsMenu(player, 0);
            }
        } else if (event.getView().getTitle().startsWith("§9История транзакций (Страница ")) {
            event.setCancelled(true);
            Player player = (Player) event.getWhoClicked();
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem != null && clickedItem.getType() == Material.ARROW) {
                int currentPage = Integer.parseInt(event.getView().getTitle().split(" ")[2]) - 1;
                if (clickedItem.getItemMeta().getDisplayName().equals("§aСледующая страница")) {
                    openTransactionsMenu(player, currentPage + 1);
                } else if (clickedItem.getItemMeta().getDisplayName().equals("§cПредыдущая страница")) {
                    openTransactionsMenu(player, currentPage - 1);
                }
            }
        }
    }

    private void askForDepositAmount(Player player) {
        player.sendMessage("§aВведите сумму для пополнения:");
        this.awaitingDeposit.put(player, true);
    }

    private void askForWithdrawAmount(Player player) {
        player.sendMessage("§aВведите сумму для снятия:");
        this.awaitingWithdraw.put(player, true);
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (this.awaitingDeposit.getOrDefault(player, false)) {
            event.setCancelled(true);
            try {
                int amount = Integer.parseInt(event.getMessage());
                processDeposit(player, amount);
            } catch (NumberFormatException e) {
                player.sendMessage("§cНеверная сумма!");
            }
            this.awaitingDeposit.remove(player);
        } else if (this.awaitingWithdraw.getOrDefault(player, false)) {
            event.setCancelled(true);
            try {
                int amount = Integer.parseInt(event.getMessage());
                processWithdraw(player, amount);
            } catch (NumberFormatException e) {
                player.sendMessage("§cНеверная сумма!");
            }
            this.awaitingWithdraw.remove(player);
        }
    }

    private void processDeposit(Player player, int amount) {
        ItemStack currencyItem = new ItemStack(this.serverCurrency);
        int availableAmount = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == this.serverCurrency) {
                availableAmount += item.getAmount();
            }
        }
        int depositAmount = Math.min(amount, availableAmount);
        if (depositAmount > 0) {
            player.getInventory().removeItem(new ItemStack(this.serverCurrency, depositAmount));
            int newBalance = playerBalances.getOrDefault(player.getUniqueId(), 0) + depositAmount;
            playerBalances.put(player.getUniqueId(), newBalance);
            player.sendMessage("§aПополнено на " + depositAmount + " " + this.serverCurrency + ". Баланс: " + newBalance);
            logTransaction(player.getUniqueId(), "Пополнение", depositAmount);
        } else {
            player.sendMessage("§cНе хватает " + this.serverCurrency + "!");
        }
    }

    private void processWithdraw(Player player, int amount) {
        int currentBalance = playerBalances.getOrDefault(player.getUniqueId(), 0);
        if (currentBalance >= amount) {
            playerBalances.put(player.getUniqueId(), currentBalance - amount);
            player.getInventory().addItem(new ItemStack(this.serverCurrency, amount));
            player.sendMessage("§aСнято " + amount + " " + this.serverCurrency + ". Баланс: " + (currentBalance - amount));
            logTransaction(player.getUniqueId(), "Снятие", amount);
        } else {
            player.sendMessage("§cНедостаточно средств!");
        }
    }

    private void processTransfer(Player sender, Player target, int amount) {
        UUID senderId = sender.getUniqueId();
        UUID targetId = target.getUniqueId();
        int senderBalance = playerBalances.getOrDefault(senderId, 0);
        if (senderBalance >= amount) {
            int targetBalance = playerBalances.getOrDefault(targetId, 0);
            playerBalances.put(senderId, senderBalance - amount);
            playerBalances.put(targetId, targetBalance + amount);
            sender.sendMessage("§aПеревод " + target.getName() + ": " + amount + " " + this.serverCurrency + ". Баланс: " + (senderBalance - amount));
            target.sendMessage("§aПолучено от " + sender.getName() + ": " + amount + " " + this.serverCurrency + ". Баланс: " + (targetBalance + amount));
            logTransaction(senderId, "Перевод " + target.getName(), amount);
            logTransaction(targetId, "Получено от " + sender.getName(), amount);
        } else {
            sender.sendMessage("§cНедостаточно средств!");
        }
    }

    private void logTransaction(UUID playerId, String type, int amount) {
        String transactionId = UUID.randomUUID().toString();
        String path = "transactions." + playerId + "." + transactionId;
        this.transactionsConfig.set(path + ".type", type);
        this.transactionsConfig.set(path + ".amount", amount);
        this.transactionsConfig.set(path + ".timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        saveTransactions();
    }

    private void openTransactionsMenu(Player player, int page) {
        UUID playerId = player.getUniqueId();
        String playerTransactionsPath = "transactions." + playerId;
        List<String> transactionKeys = new ArrayList<>(this.transactionsConfig.getConfigurationSection(playerTransactionsPath).getKeys(false));
        Collections.reverse(transactionKeys);
        int totalTransactions = transactionKeys.size();
        int totalPages = (int) Math.ceil((double) totalTransactions / this.TRANSACTIONS_PER_PAGE);
        if (page >= totalPages && totalPages > 0) page = totalPages - 1;
        if (page < 0) page = 0;

        Inventory transactionsMenu = Bukkit.createInventory(null, 54, "§9История транзакций (Страница " + (page + 1) + " из " + totalPages + ")");
        int startIndex = page * this.TRANSACTIONS_PER_PAGE;
        int endIndex = Math.min(startIndex + this.TRANSACTIONS_PER_PAGE, totalTransactions);

        for (int i = startIndex; i < endIndex; i++) {
            String transactionKey = transactionKeys.get(i);
            String path = playerTransactionsPath + "." + transactionKey;
            String type = this.transactionsConfig.getString(path + ".type");
            int amount = this.transactionsConfig.getInt(path + ".amount");
            String timestamp = this.transactionsConfig.getString(path + ".timestamp");
            ItemStack transactionItem = createTransactionItem(type, amount, timestamp);
            transactionsMenu.setItem(i - startIndex, transactionItem);
        }

        if (page > 0) {
            transactionsMenu.setItem(45, createNavigationItem(Material.ARROW, "§cПредыдущая страница"));
        }
        if (page < totalPages - 1) {
            transactionsMenu.setItem(53, createNavigationItem(Material.ARROW, "§aСледующая страница"));
        }
        player.openInventory(transactionsMenu);
    }

    private ItemStack createTransactionItem(String type, int amount, String timestamp) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§eТранзакция");
        meta.setLore(Arrays.asList(
                "§7Тип: " + type,
                "§7Сумма: " + amount + " " + this.serverCurrency,
                "§7Время: " + timestamp
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createNavigationItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    // API для внешнего доступа
    public class BankAPI {
        private final BunkSystem plugin;

        public BankAPI(BunkSystem plugin) {
            this.plugin = plugin;
        }

        public int getBalance(UUID playerId) {
            return plugin.playerBalances.getOrDefault(playerId, 0);
        }

        public boolean withdraw(UUID playerId, int amount) {
            int currentBalance = plugin.playerBalances.getOrDefault(playerId, 0);
            if (currentBalance >= amount) {
                plugin.playerBalances.put(playerId, currentBalance - amount);
                plugin.logTransaction(playerId, "Снятие (магазин)", amount);
                plugin.saveBalances();
                return true;
            }
            return false;
        }

        public void deposit(UUID playerId, int amount) {
            int currentBalance = plugin.playerBalances.getOrDefault(playerId, 0);
            plugin.playerBalances.put(playerId, currentBalance + amount);
            plugin.logTransaction(playerId, "Пополнение (магазин)", amount);
            plugin.saveBalances();
        }

        public Material getCurrency() {
            return plugin.serverCurrency;
        }
    }

    public BankAPI getBankAPI() {
        return bankAPI;
    }
}
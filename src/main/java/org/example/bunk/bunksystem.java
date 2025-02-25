package org.example.bunk;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class bunksystem extends JavaPlugin implements Listener, TabCompleter {
    private void showHelpMessage(Player player) {
        player.sendMessage("§aСписок команд банка:");
        player.sendMessage("§e/bank §7- Открывает меню банка.");
        player.sendMessage("§e/balance §7- Показывает ваш баланс.");
        player.sendMessage("§e/transfer <игрок> <сумма> §7- Перевести деньги другому игроку.");
        if (player.hasPermission("bankplugin.admin")) {
            player.sendMessage("§e/setcurrency <diamond|emerald|gold|diamond_ore> §7- Устанавливает валюту сервера.");
            player.sendMessage("§e/setbankname <новое имя> §7- Устанавливает имя банка.");
            player.sendMessage("§e/bank reload §7- Перезагружает плагин.");
        }
    }

    private Material serverCurrency = Material.DIAMOND;

    private final Map<UUID, Integer> playerBalances = new HashMap<>();

    private final Map<Player, Boolean> awaitingDeposit = new HashMap<>();

    private final Map<Player, Boolean> awaitingWithdraw = new HashMap<>();

    private String bankName = "§aБанк";

    private File balanceFile;

    private FileConfiguration balanceConfig;

    private File transactionsFile;

    private FileConfiguration transactionsConfig;

    private final int TRANSACTIONS_PER_PAGE = 45;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, (Plugin) this);
        getCommand("bank").setTabCompleter(this);
        getCommand("setcurrency").setTabCompleter(this);
        loadConfig();
        loadBalances();
        loadTransactions();
        getLogger().info("BankPlugin включен!");
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
        if (!this.balanceFile.exists())
            try {
                this.balanceFile.getParentFile().mkdirs();
                this.balanceFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Не удалось создать файл balances.yml: " + e.getMessage());
            }
        this.balanceConfig = (FileConfiguration) YamlConfiguration.loadConfiguration(this.balanceFile);
        this.playerBalances.clear();
        for (String key : this.balanceConfig.getKeys(false))
            this.playerBalances.put(UUID.fromString(key), Integer.valueOf(this.balanceConfig.getInt(key)));
    }

    private void saveBalances() {
        for (Map.Entry<UUID, Integer> entry : this.playerBalances.entrySet())
            this.balanceConfig.set(((UUID) entry.getKey()).toString(), entry.getValue());
        try {
            this.balanceConfig.save(this.balanceFile);
        } catch (IOException e) {
            getLogger().severe("Не удалось сохранить файл balances.yml: " + e.getMessage());
        }
    }

    private void loadTransactions() {
        this.transactionsFile = new File(getDataFolder(), "transactions.yml");
        if (!this.transactionsFile.exists())
            try {
                this.transactionsFile.getParentFile().mkdirs();
                this.transactionsFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Не удалось создать файл transactions.yml: " + e.getMessage());
            }
        this.transactionsConfig = (FileConfiguration) YamlConfiguration.loadConfiguration(this.transactionsFile);
    }

    private void saveTransactions() {
        try {
            this.transactionsConfig.save(this.transactionsFile);
        } catch (IOException e) {
            getLogger().severe("Не удалось сохранить файл transactions.yml: " + e.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("bank")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                if (sender.hasPermission("bankplugin.admin")) {
                    reloadPlugin();
                    sender.sendMessage("§aПлагин перезагружен!");
                } else {
                    sender.sendMessage("§cУ вас нет прав для выполнения этой команды!");
                }
                return true;
            }
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cЭта команда может быть выполнена только игроком!");
                return true;
            }
            Player player = (Player) sender;
            openBankMenu(player);
            return true;
        }
        if (command.getName().equalsIgnoreCase("Bhelp")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cЭта команда может быть выполнена только игроком!");
                return true;
            }
            Player player = (Player) sender;
            showHelpMessage(player);
            return true;
        }
        if (command.getName().equalsIgnoreCase("setcurrency")) {
            Material newCurrency;
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cЭта команда может быть выполнена только игроком!");
                return true;
            }
            Player player = (Player) sender;
            if (!player.hasPermission("bankplugin.admin")) {
                player.sendMessage("§cУ вас нет прав для выполнения этой команды!");
                return true;
            }
            if (args.length != 1) {
                player.sendMessage("§cИспользуйте: /setcurrency <diamond | emerald | gold | diamond_ore>");
                return true;
            }
            switch (args[0].toLowerCase()) {
                case "diamond":
                    newCurrency = Material.DIAMOND;
                    this.serverCurrency = newCurrency;
                    getConfig().set("currency", this.serverCurrency.name());
                    saveConfig();
                    player.sendMessage("§aВалюта сервера установлена на: " + this.serverCurrency.toString());
                    Bukkit.broadcastMessage("§aВалюта сервера изменена на: " + this.serverCurrency.toString());
                    return true;
                case "emerald":
                    newCurrency = Material.EMERALD;
                    this.serverCurrency = newCurrency;
                    getConfig().set("currency", this.serverCurrency.name());
                    saveConfig();
                    player.sendMessage("§aВалюта сервера установлена на: " + this.serverCurrency.toString());
                    Bukkit.broadcastMessage("§aВалюта сервера изменена на: " + this.serverCurrency.toString());
                    return true;
                case "gold":
                    newCurrency = Material.GOLD_INGOT;
                    this.serverCurrency = newCurrency;
                    getConfig().set("currency", this.serverCurrency.name());
                    saveConfig();
                    player.sendMessage("§aВалюта сервера установлена на: " + this.serverCurrency.toString());
                    Bukkit.broadcastMessage("§aВалюта сервера изменена на: " + this.serverCurrency.toString());
                    return true;
                case "diamond_ore":
                    newCurrency = Material.DIAMOND_ORE;
                    this.serverCurrency = newCurrency;
                    getConfig().set("currency", this.serverCurrency.name());
                    saveConfig();
                    player.sendMessage("§aВалюта сервера установлена на: " + this.serverCurrency.toString());
                    Bukkit.broadcastMessage("§aВалюта сервера изменена на: " + this.serverCurrency.toString());
                    return true;
            }
            player.sendMessage("§cУкажите diamond, emerald, gold или diamond_ore.");
            return true;
        }
        if (command.getName().equalsIgnoreCase("setbankname")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cЭта команда может быть выполнена только игроком!");
                return true;
            }
            Player player = (Player) sender;
            if (!player.hasPermission("bankplugin.admin")) {
                player.sendMessage("§cУ вас нет прав для выполнения этой команды!");
                return true;
            }
            if (args.length < 1) {
                player.sendMessage("§cИспользуйте: /setbankname <новое имя>");
                return true;
            }
            this.bankName = String.join(" ", (CharSequence[]) args);
            getConfig().set("bankName", this.bankName);
            saveConfig();
            player.sendMessage("§aИмя банка установлено на: " + this.bankName);
            return true;
        }
        if (command.getName().equalsIgnoreCase("balance")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cЭта команда может быть выполнена только игроком!");
                return true;
            }
            Player player = (Player) sender;
            int balance = ((Integer) this.playerBalances.getOrDefault(player.getUniqueId(), Integer.valueOf(0))).intValue();
            player.sendMessage("§aВаш баланс: " + balance + " " + this.serverCurrency.toString());
            return true;
        }
        if (command.getName().equalsIgnoreCase("transfer")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cЭта команда может быть выполнена только игроком!");
                return true;
            }
            Player player = (Player) sender;
            if (args.length != 2) {
                player.sendMessage("§cИспользуйте: /transfer <игрок> <сумма>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendMessage("§cИгрок " + args[0] + " не найден.");
                return true;
            }
            try {
                int amount = Integer.parseInt(args[1]);
                processTransfer(player, target, amount);
            } catch (NumberFormatException e) {
                player.sendMessage("§cНеверный формат суммы.");
            }
            return true;
        }
        return false;
    }


    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("setcurrency")) {
            if (args.length == 1) {
                return Arrays.asList("diamond", "emerald", "gold", "diamond_ore");
            }
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
        ItemStack depositItem = createCustomItem(Material.GREEN_WOOL, "§aПополнить " + this.serverCurrency.toString(), 1001);
        ItemStack withdrawItem = createCustomItem(Material.RED_WOOL, "§cСнять " + this.serverCurrency.toString(), 1002);
        ItemStack balanceItem = createCustomItem(this.serverCurrency, "§eВаш баланс: " + this.playerBalances.getOrDefault(player.getUniqueId(), Integer.valueOf(0)) + " " + this.serverCurrency.toString(), 1003);
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
        meta.setCustomModelData(Integer.valueOf(customModelData));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals(this.bankName)) {
            event.setCancelled(true);
            Player player = (Player) event.getWhoClicked();
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem != null)
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
            if (clickedItem != null &&
                    clickedItem.getType() == Material.ARROW) {
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
        this.awaitingDeposit.put(player, Boolean.valueOf(true));
    }

    private void askForWithdrawAmount(Player player) {
        player.sendMessage("§aВведите сумму для снятия:");
        this.awaitingWithdraw.put(player, Boolean.valueOf(true));
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (((Boolean) this.awaitingDeposit.getOrDefault(player, Boolean.valueOf(false))).booleanValue()) {
            event.setCancelled(true);
            try {
                int amount = Integer.parseInt(event.getMessage());
                processDeposit(player, amount);
            } catch (NumberFormatException e) {
                player.sendMessage("§cНеверный формат суммы.");
            }
            this.awaitingDeposit.remove(player);
        } else if (((Boolean) this.awaitingWithdraw.getOrDefault(player, Boolean.valueOf(false))).booleanValue()) {
            event.setCancelled(true);
            try {
                int amount = Integer.parseInt(event.getMessage());
                processWithdraw(player, amount);
            } catch (NumberFormatException e) {
                player.sendMessage("§cНеверный формат суммы.");
            }
            this.awaitingWithdraw.remove(player);
        }
    }

    private void processDeposit(Player player, int amount) {
        ItemStack currencyItem = new ItemStack(this.serverCurrency);
        int availableAmount = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == this.serverCurrency)
                availableAmount += item.getAmount();
        }
        int depositAmount = Math.min(amount, availableAmount);
        if (depositAmount > 0) {
            player.getInventory().removeItem(new ItemStack[]{new ItemStack(this.serverCurrency, depositAmount)});
            int newBalance = ((Integer) this.playerBalances.getOrDefault(player.getUniqueId(), Integer.valueOf(0))).intValue() + depositAmount;
            this.playerBalances.put(player.getUniqueId(), Integer.valueOf(newBalance));
            player.sendMessage("§aВы пополнили счет на " + depositAmount + " " + this.serverCurrency.toString() + ". Ваш новый баланс: " + newBalance + " " + this.serverCurrency.toString());
            logTransaction(player.getUniqueId(), "Пополнение", depositAmount);
        } else {
            player.sendMessage("§cУ вас нет достаточного количества " + this.serverCurrency.toString() + " для пополнения.");
        }
    }

    private void processWithdraw(Player player, int amount) {
        int currentBalance = ((Integer) this.playerBalances.getOrDefault(player.getUniqueId(), Integer.valueOf(0))).intValue();
        if (currentBalance >= amount) {
            this.playerBalances.put(player.getUniqueId(), Integer.valueOf(currentBalance - amount));
            player.getInventory().addItem(new ItemStack[] { new ItemStack(this.serverCurrency, amount) });
            player.sendMessage("§aВы сняли со счета " + amount + " " + this.serverCurrency.toString() + ". Ваш новый баланс: " + (currentBalance - amount) + " " + this.serverCurrency.toString());
            logTransaction(player.getUniqueId(), "Снятие", amount);
        } else {
            player.sendMessage("§cНедостаточно средств на счете.");
        }
    }

    private void processTransfer(Player sender, Player target, int amount) {
        UUID senderId = sender.getUniqueId();
        UUID targetId = target.getUniqueId();
        int senderBalance = ((Integer) this.playerBalances.getOrDefault(senderId, Integer.valueOf(0))).intValue();
        if (senderBalance >= amount) {
            int targetBalance = ((Integer) this.playerBalances.getOrDefault(targetId, Integer.valueOf(0))).intValue();
            this.playerBalances.put(senderId, Integer.valueOf(senderBalance - amount));
            this.playerBalances.put(targetId, Integer.valueOf(targetBalance + amount));
            sender.sendMessage("§aВы перевели игроку " + target.getName() + " " + amount + " " + this.serverCurrency.toString() + ". Ваш новый баланс: " + (senderBalance - amount) + " " + this.serverCurrency.toString());
            target.sendMessage("§aИгрок " + sender.getName() + " перевел вам " + amount + " " + this.serverCurrency.toString() + ". Ваш новый баланс: " + (targetBalance + amount) + " " + this.serverCurrency.toString());
            logTransaction(senderId, "Перевод игроку " + target.getName(), amount);
            logTransaction(targetId, "Получен перевод от игрока " + sender.getName(), amount);
        } else {
            sender.sendMessage("§cНедостаточно средств на счете.");
        }
    }

    private void logTransaction(UUID playerId, String type, int amount) {
        String transactionId = UUID.randomUUID().toString();
        String path = "transactions." + playerId.toString() + "." + transactionId;
        this.transactionsConfig.set(path + ".type", type);
        this.transactionsConfig.set(path + ".amount", Integer.valueOf(amount));
        this.transactionsConfig.set(path + ".timestamp", (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")).format(new Date()));
        saveTransactions();
    }

    private void openTransactionsMenu(Player player, int page) {
        UUID playerId = player.getUniqueId();
        String playerTransactionsPath = "transactions." + playerId.toString();
        List<String> transactionKeys = new ArrayList<>(this.transactionsConfig.getConfigurationSection(playerTransactionsPath).getKeys(false));
        Collections.reverse(transactionKeys);
        int totalTransactions = transactionKeys.size();
        int totalPages = (int) Math.ceil(totalTransactions / this.TRANSACTIONS_PER_PAGE);
        if (page >= totalPages && totalPages > 0)
            page = totalPages - 1;
        if (page < 0)
            page = 0;
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
            ItemStack previousPage = createNavigationItem(Material.ARROW, "§cПредыдущая страница");
            transactionsMenu.setItem(45, previousPage);
        }
        if (page < totalPages - 1) {
            ItemStack nextPage = createNavigationItem(Material.ARROW, "§aСледующая страница");
            transactionsMenu.setItem(53, nextPage);
        }
        player.openInventory(transactionsMenu);
    }

    private ItemStack createTransactionItem(String type, int amount, String timestamp) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§eТранзакция");
        List<String> lore = new ArrayList<>();
        lore.add("§7Тип: " + type);
        lore.add("§7Сумма: " + amount + " " + this.serverCurrency.toString());
        lore.add("§7Время: " + timestamp);
        meta.setLore(lore);
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
}

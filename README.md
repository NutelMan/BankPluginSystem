# BankPluginSystem

Система банка для Minecraft сервера.

## Описание

Плагин добавляет функциональность банка на ваш сервер. Игроки могут просматривать баланс, пополнять и снимать деньги, переводить деньги другим игрокам. Администраторы могут устанавливать валюту сервера и имя банка. Также добавлена возможность просмотра топа игроков по балансу (пока не реализована).

## Команды

*   `/bank` - Открывает меню банка. Используйте `/bank reload` для перезагрузки плагина (требуется право `bankplugin.admin`).
*   `/balance` - Показывает ваш баланс.
*   `/transfer <игрок> <сумма>` - Перевести деньги другому игроку.
*   `/setcurrency <diamond | emerald | gold | diamond_ore>` - (Только для администраторов) Устанавливает валюту сервера.
*   `/setbankname <новое имя>` - (Только для администраторов) Устанавливает имя банка.
*   `/bhelp` - Показывает список команд.
*   `/bank top` - Показывает топ игроков по балансу (не реализовано).

## Права

*   `bankplugin.admin` - Права администратора для плагина.

## Установка

1.  Скачайте `.jar` файл плагина.
2.  Поместите `.jar` файл в папку `plugins` вашего сервера.
3.  Перезапустите сервер.

## API

Плагин предоставляет API для взаимодействия с другими плагинами. Вы можете получить доступ к API через `Bukkit.getPluginManager().getPlugin("BankPluginSystem")`.

### Методы API:

*   `getBankPlugin()`: Возвращает экземпляр плагина `bunksystem`. Пример: `bunksystem bankPlugin = (bunksystem) Bukkit.getPluginManager().getPlugin("BankPluginSystem");`
*   `getBalance(UUID playerUUID)`: Возвращает баланс игрока. Пример: `int balance = bankPlugin.getBalance(playerUUID);`
*   `deposit(UUID playerUUID, int amount)`: Пополняет баланс игрока. Пример: `bankPlugin.deposit(playerUUID, 100);`
*   `withdraw(UUID playerUUID, int amount)`: Снимает деньги с баланса игрока. Пример: `bankPlugin.withdraw(playerUUID, 50);`
*   `setBalance(UUID playerUUID, int amount)`: Устанавливает баланс игрока. Пример: `bankPlugin.setBalance(playerUUID, 1000);`
*   `getServerCurrency()`: Возвращает `Material` валюты сервера. Пример: `Material currency = bankPlugin.getServerCurrency();`
*   `getBankName()`: Возвращает название банка. Пример: `String bankName = bankPlugin.getBankName();`

### Вспомогательные методы:

*   `loadBalances()`: (Для внутреннего использования плагина) Перезагружает балансы из файла.  Не рекомендуется использовать напрямую.
*   `saveBalances()`: (Для внутреннего использования плагина) Сохраняет балансы в файл. Не рекомендуется использовать напрямую.

Важно: Не используйте `loadBalances()` и `saveBalances()` напрямую, если нет крайней необходимости. Используйте `deposit()`, `withdraw()` и `setBalance()` для изменения баланса игроков. Убедитесь, что ваш плагин зависит от `BankPluginSystem` в `plugin.yml` (`depend: [BankPluginSystem]`).

Пример использования API:
````
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.example.bunk.bunksystem;
import java.util.UUID;

public class MyPlugin {
public void doSomethingWithBank(UUID playerUUID) {
Plugin plugin = Bukkit.getPluginManager().getPlugin("BankPluginSystem");
if (plugin instanceof bunksystem) {
bunksystem bankPlugin = (bunksystem) plugin;
int balance = bankPlugin.getBalance(playerUUID);
System.out.println("Баланс игрока: " + balance);
bankPlugin.deposit(playerUUID, 50);
} else {
System.out.println("BankPluginSystem не найден!");
}
}
}
````


## Конфигурация

Плагин использует файл `config.yml` для хранения настроек.  Параметры: `bankName`, `currency`.

## Зависимости

Плагин не имеет жестких зависимостей.

## UPD:

 все методы API, описанные выше, существуют в предоставленном исходном коде (`bunksystem.java`). Методы `getBalance`, `deposit`, `withdraw`, `setBalance`, `getServerCurrency` и `getBankName` являются публичными (public) и могут быть вызваны из других плагинов. Методы `loadBalances` и `saveBalances` также присутствуют, но не рекомендуются для прямого использования из других плагинов.
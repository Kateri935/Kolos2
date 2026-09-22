import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

/**
 * ============================================================
 *  ШАГИ 9-14 (Kroki 9-14): корзина покупателя
 * ============================================================
 * Cart наследует ClientHandler (шаг 9, файл прислан без изменений)
 * и реализует его два абстрактных метода: onLineReceived() (шаг 11)
 * и onDisconnect() (шаг 14). Для каждого подключившегося клиента
 * Server (шаг 8) создаёт СВОЙ объект Cart — то есть у каждого
 * покупателя своя, независимая от других, корзина.
 *
 * Поле out (PrintWriter) для отправки ответов клиенту унаследовано
 * от ClientHandler — используется напрямую, отдельного метода
 * send() здесь не заводилось, чтобы не дублировать то, что уже
 * есть в базовом классе.
 */
public class Cart extends ClientHandler {

    private final Warehouse warehouse;

    /**
     * Содержимое корзины: id товара -> [количество, цена за штуку
     * НА МОМЕНТ добавления в корзину]. Цена запоминается специально —
     * она нужна на шаге 13, чтобы обнаружить, что цена товара
     * поменялась (например, из-за скидки), пока товар лежал в корзине.
     */
    private final Map<Integer, int[]> items = new HashMap<>();

    public Cart(Socket socket, Warehouse warehouse) {
        super(socket);
        this.warehouse = warehouse;
    }

    /**
     * ШАГ 11 (Krok 11): разбор одной строки, присланной клиентом.
     * Поддерживаются две команды:
     *   add <id> <quantity>  -> вызывает handleAdd()  (шаг 10)
     *   pay                  -> вызывает handlePay()  (шаги 12-13)
     * Любая другая команда или неверное число аргументов -> сообщение об ошибке.
     */
    @Override
    protected synchronized void onLineReceived(String line) {
        String[] parts = line.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) {
            return; // пустая строка — игнорируем
        }

        switch (parts[0]) {
            case "add" -> {
                if (parts.length != 3) {
                    out.println("Błąd: oczekiwano 'add <id> <quantity>'");
                    return;
                }
                try {
                    int id = Integer.parseInt(parts[1]);
                    int quantity = Integer.parseInt(parts[2]);
                    handleAdd(id, quantity);
                } catch (NumberFormatException e) {
                    out.println("Błąd: id oraz quantity muszą być liczbami całkowitymi");
                }
            }
            case "pay" -> handlePay();
            default -> out.println("Nieznana komenda: " + parts[0]);
        }
    }

    /**
     * ШАГ 10 (Krok 10): добавление товара в корзину.
     *
     * Порядок действий:
     *  1) проверяем, что quantity положительное;
     *  2) пытаемся АТОМАРНО зарезервировать товар на складе —
     *     warehouse.reserve() сама проверяет наличие И уменьшает
     *     количество как единую synchronized-операцию (подробности
     *     в Warehouse.java), поэтому даже если два клиента одновременно
     *     дерутся за последнюю единицу товара — она достанется только
     *     одному, гонки данных не будет;
     *  3) если товара не хватило — сообщаем клиенту, корзина НЕ меняется;
     *  4) если получилось — запоминаем товар в items (или увеличиваем
     *     количество, если этот товар уже был в корзине) и отправляем
     *     клиенту стоимость ИМЕННО ЭТОЙ операции (currentPrice * quantity),
     *     как того требует задание.
     */
    public synchronized void handleAdd(int id, int quantity) {
        if (quantity <= 0) {
            out.println("Błąd: ilość musi być dodatnia");
            return;
        }

        boolean reserved = warehouse.reserve(id, quantity);
        if (!reserved) {
            out.println("Za mało produktów w magazynie");
            return;
        }

        int currentPrice = warehouse.getCurrentPrice(id);
        items.merge(id, new int[]{quantity, currentPrice}, (existing, added) -> {
            existing[0] += added[0];   // суммируем количество, если товар уже добавляли раньше
            existing[1] = added[1];    // запоминаем последнюю известную цену
            return existing;
        });

        int cost = currentPrice * quantity;
        out.println("Dodano do koszyka. Koszt tej pozycji: " + cost);
    }

    /**
     * ШАГИ 12-13 (Kroki 12-13): оплата содержимого корзины.
     *
     * Сначала (шаг 13) проходим по ВСЕМ товарам в корзине и сверяем
     * цену, запомненную при добавлении, с текущей ценой на складе
     * (getCurrentPrice может измениться, если между add и pay кто-то
     * выставил скидку через DiscountWindow). Если хоть один товар
     * подорожал/подешевел — покупку НЕ завершаем, а предупреждаем
     * клиента и обновляем запомненную цену на актуальную, чтобы
     * ПОВТОРНАЯ команда pay уже прошла успешно (это и есть "повторное
     * подтверждение" из задания).
     *
     * Если цены не менялись (шаг 12) — считаем итоговую сумму,
     * для каждого товара вызываем warehouse.confirmPurchase() (эта
     * покупка УЖЕ пишется в базу данных, см. Warehouse.java), очищаем
     * корзину и сообщаем клиенту итоговую сумму.
     */
    public synchronized void handlePay() {
        if (items.isEmpty()) {
            out.println("Koszyk jest pusty");
            return;
        }

        // Шаг 13: проверка, что цены не "уехали" с момента добавления
        for (Map.Entry<Integer, int[]> entry : items.entrySet()) {
            int id = entry.getKey();
            int priceAtAddTime = entry.getValue()[1];
            int currentPrice = warehouse.getCurrentPrice(id);

            if (currentPrice != priceAtAddTime) {
                out.println("Ceny produktów uległy zmianie od momentu dodania do koszyka. " +
                        "Wyślij komendę 'pay' ponownie, aby potwierdzić zakup po nowych cenach.");
                entry.getValue()[1] = currentPrice; // запоминаем новую цену для повторного pay
                return;
            }
        }

        // Шаг 12: цены не изменились — можно фиксировать покупку
        int total = 0;
        for (Map.Entry<Integer, int[]> entry : items.entrySet()) {
            int id = entry.getKey();
            int quantity = entry.getValue()[0];
            int price = entry.getValue()[1];
            total += price * quantity;
            warehouse.confirmPurchase(id, quantity); // вот тут и происходит запись в БАЗУ ДАННЫХ
        }

        items.clear();
        out.println("Zakup potwierdzony. Kwota do zapłaty: " + total);
    }

    /**
     * ШАГ 14 (Krok 14): вызывается автоматически из ClientHandler.close()
     * (см. ClientHandler.java), когда клиент явно закрыл соединение
     * ИЛИ оно оборвалось из-за сетевой ошибки — в обоих случаях
     * поведение одинаковое: всё, что оставалось в корзине неоплаченным,
     * возвращается обратно на склад (warehouse.release), и корзина
     * очищается. База данных этой операцией не затрагивается —
     * ведь в неё товар из корзины ещё не попадал (это происходит
     * только при успешном handlePay(), шаг 12).
     */
    @Override
    protected synchronized void onDisconnect() {
        for (Map.Entry<Integer, int[]> entry : items.entrySet()) {
            warehouse.release(entry.getKey(), entry.getValue()[0]);
        }
        items.clear();
    }
}

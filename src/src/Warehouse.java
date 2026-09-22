import java.util.HashMap;
import java.util.Map;

/**
 * ============================================================
 *  ШАГ 4 (Krok 4): класс-склад, хранящий состояние в памяти
 * ============================================================
 * Конструктор ОДИН РАЗ читает все товары и их количества из базы
 * (через DatabaseHandler) и держит их в памяти в двух Map — дальше
 * вся работа идёт с этими Map, а не с базой напрямую (кроме
 * confirmPurchase(), см. ниже).
 *
 * ВАЖНО про потокобезопасность: к этому объекту одновременно
 * обращаются РАЗНЫЕ потоки — поток окна DiscountWindow (Swing EDT),
 * поток WebServer (обслуживает HTTP-запросы) и по отдельному потоку
 * на КАЖДОГО подключённого клиента (Cart, см. Server, шаг 8).
 * Поэтому абсолютно все методы помечены synchronized — только один
 * поток одновременно может изменять/читать состояние склада.
 */
public class Warehouse {

    private final DatabaseHandler databaseHandler;

    /** id -> товар (name, price) — статичные данные, не меняются после старта */
    private final Map<Integer, Product> products = new HashMap<>();

    /** id -> сколько ШТУК сейчас доступно клиентам (уменьшается при резервации) */
    private final Map<Integer, Integer> quantities = new HashMap<>();

    /** id -> текущая скидка в процентах (0-100). Только в памяти! */
    private final Map<Integer, Integer> discounts = new HashMap<>();

    public Warehouse(DatabaseHandler databaseHandler) {
        this.databaseHandler = databaseHandler;
        for (Product product : databaseHandler.getProducts()) {
            products.put(product.id(), product);
            quantities.put(product.id(), databaseHandler.getQuantity(product.id()));
        }
    }

    /** Копия карты товаров — используется в WebServer (шаг 5) и DiscountWindow (шаг 7),
     *  чтобы построить HTML-таблицу или заполнить выпадающий список. */
    public synchronized Map<Integer, Product> getProducts() {
        return new HashMap<>(products);
    }

    /** Ilość, видимая клиентам ПРЯМО СЕЙЧАС (базовое количество минус все
     *  текущие резервации в открытых корзинах). Именно это число показывает WebServer. */
    public synchronized int getAvailableQuantity(int id) {
        return quantities.getOrDefault(id, 0);
    }

    /** Цена товара С УЧЁТОМ текущей скидки, округлённая до целого.
     *  Ничего никуда не пишет — просто вычисление "на лету". */
    public synchronized int getCurrentPrice(int id) {
        Product product = products.get(id);
        if (product == null) {
            throw new IllegalArgumentException("Brak produktu o id " + id);
        }
        int discount = discounts.getOrDefault(id, 0);
        return Math.round(product.price() * (100 - discount) / 100.0f);
    }

    /**
     * ШАГ 6 (Krok 6): установка скидки на товар.
     * Вызывается из DiscountWindow (шаг 7) при нажатии кнопки.
     * Скидка живёт ТОЛЬКО в памяти (в discounts) — в базу данных
     * ничего не пишется, поэтому при перезапуске программы (Map
     * создаётся заново) все скидки автоматически "обнуляются",
     * как и требует задание.
     */
    public synchronized void setDiscount(int id, int discount) {
        if (discount < 0 || discount > 100) {
            throw new IllegalArgumentException("Rabat musi być z zakresu 0-100");
        }
        if (!products.containsKey(id)) {
            throw new IllegalArgumentException("Brak produktu o id " + id);
        }
        discounts.put(id, discount);
    }

    /**
     * ШАГ 10 (Krok 10), часть 1 — "атомарная" проверка+резервирование.
     * Вызывается из Cart.handleAdd() при команде "add id quantity".
     *
     * Атомарность обеспечивается тем, что ВЕСЬ метод (проверка "хватает
     * ли товара" + собственно уменьшение quantities) — это ОДИН
     * synchronized-блок. Значит, пока один клиентский поток выполняет
     * reserve(), ни один другой поток не может одновременно тоже
     * пройти проверку "available >= quantity" для того же id —
     * иначе два клиента могли бы "купить" один и тот же последний
     * товар одновременно (классический race condition).
     *
     * База данных здесь НЕ модифицируется — меняется только Map
     * в памяти, а значит, изменение сразу видно на WebServer,
     * но исчезнет при перезапуске, если клиент так и не заплатит
     * (см. release() ниже).
     */
    public synchronized boolean reserve(int id, int quantity) {
        Integer available = quantities.get(id);
        if (available == null || available < quantity) {
            return false; // товара не хватает — Cart отправит клиенту отказ
        }
        quantities.put(id, available - quantity);
        return true;
    }

    /**
     * ШАГ 14 (Krok 14): возврат ранее зарезервированного товара обратно
     * на склад. Вызывается из Cart.onDisconnect(), когда клиент
     * отключился (закрыл соединение или оно оборвалось), не оплатив
     * содержимое корзины. Просто прибавляет quantity обратно к
     * значению в Map — база данных этой операции вообще не касается,
     * потому что резервация изначально в базу не записывалась.
     */
    public synchronized void release(int id, int quantity) {
        quantities.merge(id, quantity, Integer::sum);
    }

    /**
     * ШАГ 12 (Krok 12): ПОДТВЕРЖДЁННАЯ покупка — вызывается из
     * Cart.handlePay(), когда клиент реально расплатился и цены
     * не изменились с момента добавления товара в корзину.
     *
     * Здесь и только здесь происходит запись в БАЗУ ДАННЫХ
     * (через databaseHandler.setQuantity). Количество в памяти
     * (quantities) ПОВТОРНО не уменьшается — это уже было сделано
     * раньше, в момент reserve() при добавлении в корзину. Мы лишь
     * "переносим" уже случившееся списание из памяти в постоянное
     * хранилище (файл базы данных), читая актуальное значение из
     * базы и вычитая из него купленное количество.
     */
    public synchronized void confirmPurchase(int id, int quantity) {
        int currentDbQuantity = databaseHandler.getQuantity(id);
        databaseHandler.setQuantity(id, currentDbQuantity - quantity);
    }
}

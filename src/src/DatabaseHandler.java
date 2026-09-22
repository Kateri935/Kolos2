import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================
 *  ШАГ 2 (Krok 2): подключение к базе SQLite
 * ============================================================
 * Класс отвечает ТОЛЬКО за связь с базой данных (таблица product:
 * id, name, price, quantity) — никакой бизнес-логики магазина
 * здесь нет, этим занимается класс Warehouse (шаг 4).
 *
 * jdbc:sqlite:<путь> — для SQLite это буквально путь к файлу .db
 * на диске. Драйвер org.xerial:sqlite-jdbc должен быть на classpath
 * во время ВЫПОЛНЕНИЯ (для компиляции он не нужен, т.к. мы работаем
 * только с интерфейсами из стандартного пакета java.sql).
 */
public class DatabaseHandler {

    private final String url;

    public DatabaseHandler(String databaseFilePath) {
        this.url = "jdbc:sqlite:" + databaseFilePath;
    }

    /** Открывает новое соединение с базой. Каждый метод открывает своё
     *  и сразу закрывает через try-with-resources — для SQLite и
     *  нечастых запросов этого вполне достаточно. */
    private Connection connect() throws SQLException {
        return DriverManager.getConnection(url);
    }

    /**
     * ШАГ 3 (Krok 3), метод 1 из 3:
     * SELECT id, name, price FROM product
     * Возвращает список ВСЕХ товаров (без учёта quantity — это
     * отдельное поле, для него есть getQuantity()).
     */
    public List<Product> getProducts() {
        String sql = "SELECT id, name, price FROM product";
        List<Product> products = new ArrayList<>();

        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                products.add(new Product(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getInt("price")
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Błąd podczas pobierania listy produktów", e);
        }
        return products;
    }

    /**
     * ШАГ 3 (Krok 3), метод 2 из 3:
     * SELECT quantity FROM product WHERE id = ?
     * Используется PreparedStatement с "?" вместо прямой подстановки id
     * в строку SQL — это защита от SQL-инъекций и правильная работа
     * с типами (кавычки, экранирование и т.д. драйвер берёт на себя).
     */
    public int getQuantity(int id) {
        String sql = "SELECT quantity FROM product WHERE id = ?";

        try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id); // "?" номер 1 (нумерация с единицы)
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("quantity");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Błąd podczas pobierania ilości dla id=" + id, e);
        }
        throw new IllegalArgumentException("Brak produktu o id " + id);
    }

    /**
     * ШАГ 3 (Krok 3), метод 3 из 3:
     * UPDATE product SET quantity = ? WHERE id = ?
     * Вызывается ТОЛЬКО в момент подтверждённой покупки
     * (см. Warehouse.confirmPurchase(), шаг 12) — резервирование
     * товара в корзине (шаг 10) базу данных не трогает вообще.
     */
    public void setQuantity(int id, int quantity) {
        String sql = "UPDATE product SET quantity = ? WHERE id = ?";

        try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, quantity);
            stmt.setInt(2, id);
            stmt.executeUpdate(); // возвращает число изменённых строк, здесь не используем
        } catch (SQLException e) {
            throw new RuntimeException("Błąd podczas aktualizacji ilości dla id=" + id, e);
        }
    }
}

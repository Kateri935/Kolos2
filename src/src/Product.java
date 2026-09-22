/**
 * ============================================================
 *  ШАГ 1 (Krok 1)
 * ============================================================
 * Рекорд (record) — неизменяемый класс-данные, описывающий один
 * товар. Java сама генерирует конструктор, геттеры id()/name()/price(),
 * а также equals()/hashCode()/toString() — вручную переопределён
 * только toString(), чтобы в выпадающем списке DiscountWindow
 * (JComboBox<Product>) отображалось просто имя товара, а не
 * что-то вроде "Product[id=1, name=monitor, price=899]".
 */
public record Product(int id, String name, int price) {

    @Override
    public String toString() {
        return name;
    }
}

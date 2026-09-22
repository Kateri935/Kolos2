import javax.swing.*;
import java.awt.*;

/**
 * ============================================================
 *  ШАГ 7 (Krok 7): графическое окно установки скидок (Swing)
 * ============================================================
 * ЭТО РЕАЛЬНЫЙ КЛАСС ИЗ projekt_poczatkowy.zip. Изменения:
 *  1) в конструктор добавлен параметр Warehouse;
 *  2) JComboBox<String> заменён на JComboBox<Product> и теперь
 *     заполняется реальными товарами из warehouse.getProducts()
 *     вместо статичного массива {"item1","item2","item3"};
 *  3) applyDiscount() теперь реально читает выбранный товар/процент
 *     из компонентов интерфейса и вызывает warehouse.setDiscount(),
 *     вместо захардкоженных значений percent=20, product="things".
 * Расположение компонентов (FlowLayout) и структура конструктора
 * не менялись.
 */
public class DiscountWindow extends JFrame {

    /** Ссылка на общий (тот же самый, что у WebServer и Server) объект склада —
     *  благодаря этому изменение скидки в GUI сразу видно на веб-странице. */
    private final Warehouse warehouse;

    private final JComboBox<Product> productCombo;
    private final JSpinner discountSpinner;

    public DiscountWindow(Warehouse warehouse) {
        this.warehouse = warehouse;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new FlowLayout(FlowLayout.LEFT, 10, 10));

        // ШАГ 7: "запełнить listę rozwijaną produktami z magazynu" —
        // читаем реальные товары из Warehouse и добавляем их в комбобокс.
        // Показываться будет Product.toString() (см. Product.java) — просто имя.
        productCombo = new JComboBox<>();
        for (Product product : warehouse.getProducts().values()) {
            productCombo.addItem(product);
        }

        // Ограничение диапазона 0..100 задано прямо в модели спиннера —
        // пользователь физически не может ввести некорректный процент.
        discountSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 100, 1));
        discountSpinner.setPreferredSize(new Dimension(60, 25));

        JButton discountButton = new JButton("Discount");
        discountButton.addActionListener(e -> applyDiscount()); // вызов при клике

        add(new JLabel("product:"));
        add(productCombo);
        add(new JLabel("discount (%):"));
        add(discountSpinner);
        add(discountButton);

        pack();
        setLocationRelativeTo(null);
    }

    /**
     * ШАГ 7 (Krok 7): "po naciśnięciu przycisku wywołaj metodę setDiscount()
     * z parametrami wprowadzonymi przez użytkownika".
     * Читаем текущий выбор из JComboBox и JSpinner, вызываем
     * warehouse.setDiscount(id, percent) — и сразу показываем
     * пользователю подтверждение (или ошибку, если что-то не так).
     */
    private void applyDiscount() {
        Product selected = (Product) productCombo.getSelectedItem();
        if (selected == null) {
            return; // на всякий случай — список пуст (в этом проекте так не бывает)
        }
        int percent = (Integer) discountSpinner.getValue();

        try {
            warehouse.setDiscount(selected.id(), percent);
            String msg = "The discount on " + selected.name() + " is set to " + percent + "%.";
            JOptionPane.showMessageDialog(this, msg);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Błąd", JOptionPane.ERROR_MESSAGE);
        }
    }
}

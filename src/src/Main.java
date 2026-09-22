/**
 * ============================================================
 *  ТОЧКА ВХОДА: связывает все шаги задания в одну программу
 * ============================================================
 * Порядок запуска соответствует зависимостям между классами:
 *
 *   1) DatabaseHandler  — подключение к warehouse.db          (шаги 2-3)
 *   2) Warehouse         — читает товары/остатки из БД в память (шаг 4)
 *   3) WebServer          — отдельный поток, порт 8000          (шаг 5)
 *   4) Server (TCP)       — отдельный поток, порт 9090          (шаг 8)
 *   5) DiscountWindow      — окно Swing, показывается последним  (шаг 7)
 *
 * WebServer и Server запущены как daemon-потоки: если закрыть окно
 * DiscountWindow (что вызывает System.exit через EXIT_ON_CLOSE),
 * JVM завершится, не дожидаясь этих фоновых потоков.
 *
 * Все три компонента (WebServer, Server, DiscountWindow) получают
 * ОДИН И ТОТ ЖЕ объект warehouse — поэтому, например, изменение
 * скидки в окне сразу видно на веб-странице, а покупка через
 * TCP-клиента сразу меняет отображаемый там остаток.
 */
public class Main {
    public static void main(String[] args) throws Exception {
        DatabaseHandler databaseHandler = new DatabaseHandler("warehouse.db");
        Warehouse warehouse = new Warehouse(databaseHandler);

        // Шаг 5: веб-сервер для просмотра состояния склада в браузере
        WebServer webServer = new WebServer(8000, warehouse);
        Thread webThread = new Thread(() -> {
            try { webServer.start(); }
            catch (Exception e) { e.printStackTrace(); }
        });
        webThread.setDaemon(true);
        webThread.start();

        // Шаг 8: TCP-сервер для клиентов-покупателей (Cart)
        Server server = new Server(9090, warehouse);
        Thread serverThread = new Thread(() -> {
            try { server.start(); }
            catch (Exception e) { e.printStackTrace(); }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        // Шаг 7: окно управления скидками — запускается в потоке main,
        // как и было в исходном файле (без SwingUtilities.invokeLater)
        DiscountWindow window = new DiscountWindow(warehouse);
        window.setVisible(true);
    }
}

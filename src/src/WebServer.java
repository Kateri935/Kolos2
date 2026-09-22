import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * ============================================================
 *  ШАГ 5 (Krok 5): веб-сервер, показывающий состояние склада
 * ============================================================
 * ЭТО РЕАЛЬНЫЙ КЛАСС ИЗ projekt_poczatkowy.zip. Изменения:
 *  1) в конструктор добавлен параметр Warehouse — раньше сервер
 *     вообще ничего не знал про склад;
 *  2) метод buildHtmlPage() переписан на StringBuilder + цикл
 *     по товарам (раньше это был статичный текстовый блок
 *     с "зашитыми" monitor/klawiatura).
 * Методы start() и handleClient() НЕ ТРОГАЛИСЬ — задание прямо
 * просит поменять только buildHtmlPage().
 *
 * Это НЕ обёртка над стандартным HTTP-сервером Java — это
 * самописный минимальный HTTP-сервер поверх голого ServerSocket:
 * вручную читается запрос (точнее, тут он даже не парсится —
 * сервер всегда отдаёт одну и ту же страницу на любой запрос)
 * и вручную собирается HTTP-ответ (статус-строка + заголовки + тело).
 */
public class WebServer {

    private final int port;
    private final Warehouse warehouse;

    public WebServer(int port, Warehouse warehouse) {
        this.port = port;
        this.warehouse = warehouse;
    }

    /** Бесконечный цикл: принять соединение -> обработать -> ждать следующее.
     *  Обрабатывается синхронно, один клиент за раз (для учебного
     *  примера — приемлемо, т.к. страница отдаётся мгновенно). */
    public void start() throws IOException {
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Server running on http://localhost:" + port);

        while (true) {
            Socket client = serverSocket.accept();
            handleClient(client);
        }
    }

    /** Формирует и отправляет "руками" HTTP-ответ: статус-строка,
     *  заголовки Content-Type/Content-Length, пустая строка, тело (HTML). */
    private void handleClient(Socket client) {
        try (client;
             OutputStream out = client.getOutputStream()
        ) {
            try {
                String body = buildHtmlPage();
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

                String response =
                        "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: text/html; charset=utf-8\r\n" +
                                "Content-Length: " + bytes.length + "\r\n" +
                                "\r\n";

                out.write(response.getBytes(StandardCharsets.UTF_8));
                out.write(bytes);
                out.flush();

            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (IOException ignored) {
        }
    }

    /**
     * ШАГ 5 (Krok 5): изменённый метод — строит HTML-таблицу
     * ПО РЕАЛЬНЫМ ДАННЫМ склада (warehouse.getProducts(), цена
     * с учётом скидки warehouse.getCurrentPrice(), актуальное
     * количество warehouse.getAvailableQuantity()) вместо двух
     * захардкоженных строк monitor/klawiatura из исходного файла.
     *
     * StringBuilder использован вместо text-блока ("""..."""),
     * потому что содержимое строится ДИНАМИЧЕСКИ в цикле —
     * с обычным текстовым блоком так сделать нельзя.
     */
    private String buildHtmlPage() {
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>");
        html.append("<html>");
        html.append("<head>");
        html.append("<meta charset=\"UTF-8\">");
        html.append("</head>");
        html.append("<body>");

        html.append("<table border=\"1\">");
        html.append("<tr>");
        html.append("<th>ID</th><th>Nazwa</th><th>Cena</th><th>Ilość</th>");
        html.append("</tr>");

        // Проходим по ВСЕМ товарам склада и для каждого строим строку таблицы
        for (Map.Entry<Integer, Product> entry : warehouse.getProducts().entrySet()) {
            int id = entry.getKey();
            Product product = entry.getValue();
            int price = warehouse.getCurrentPrice(id);        // цена с учётом скидки (шаг 6)
            int quantity = warehouse.getAvailableQuantity(id); // с учётом текущих резерваций (шаг 10)

            html.append("<tr>");
            html.append("<td>").append(id).append("</td>");
            html.append("<td>").append(product.name()).append("</td>");
            html.append("<td>").append(price).append("</td>");
            html.append("<td>").append(quantity).append("</td>");
            html.append("</tr>");
        }

        html.append("</table>");
        html.append("</body>");
        html.append("</html>");

        return html.toString();
    }
}

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ============================================================
 *  ШАГ 8 (Krok 8): TCP-сервер для параллельных клиентов
 * ============================================================
 * "Stwórz klasę Server obsługującą równoległe połączenia TCP od wielu
 * klientów. Server i WebServer powinny działać równocześnie na różnych
 * portach."
 *
 * Работает независимо и на ДРУГОМ порту, чем WebServer (см. Main.java:
 * WebServer слушает 8000, Server слушает 9090) — оба сервера запущены
 * каждый в своём собственном потоке.
 *
 * Схема параллельности:
 *  1) accept() блокируется, пока не подключится очередной клиент;
 *  2) для каждого подключения создаётся СВОЙ объект Cart (шаг 9);
 *  3) Cart не выполняется напрямую в текущем потоке, а отправляется
 *     в пул потоков (ExecutorService) — значит, много клиентов
 *     могут общаться с сервером ОДНОВРЕМЕННО, каждый в своём потоке,
 *     а цикл while(true) сразу же возвращается к accept() и ждёт
 *     следующее подключение, не блокируясь на текущем клиенте.
 */
public class Server {

    private final int port;
    private final Warehouse warehouse;

    /** Пул потоков растёт по мере необходимости (newCachedThreadPool) —
     *  подходит для непредсказуемого числа одновременных клиентов. */
    private final ExecutorService pool = Executors.newCachedThreadPool();

    public Server(int port, Warehouse warehouse) {
        this.port = port;
        this.warehouse = warehouse;
    }

    public void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Cart server running on port " + port);
            while (true) {
                Socket clientSocket = serverSocket.accept(); // ждём нового клиента
                Cart cart = new Cart(clientSocket, warehouse); // свой "продавец" для каждого клиента
                pool.submit(cart); // Cart.run() выполнится в отдельном потоке пула
            }
        }
    }
}

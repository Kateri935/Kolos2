import java.io.*;
import java.net.Socket;

/**
 * ============================================================
 *  ШАГ 9 (Krok 9), базовый класс — ПРИСЛАННЫЙ ФАЙЛ, БЕЗ ИЗМЕНЕНИЙ
 * ============================================================
 * Это оригинальный файл ClientHandler.java, как он был загружен —
 * ни одна строчка кода здесь не менялась. Комментарии добавлены
 * только для объяснения, как этот класс используется в Cart.java.
 *
 * Как это работает:
 *  - конструктор просто сохраняет Socket;
 *  - run() (вызывается автоматически, когда объект передан в поток
 *    или в ExecutorService — см. Server.java, шаг 8) открывает
 *    поток чтения (in) и поток записи (out) и в бесконечном цикле
 *    читает строки от клиента, для каждой вызывая абстрактный метод
 *    onLineReceived(line) — его реализует Cart (шаг 11);
 *  - как только клиент закрывает соединение (readLine() вернёт null)
 *    или связь обрывается (IOException) — выполнение переходит
 *    в finally -> close();
 *  - close() СНАЧАЛА вызывает onDisconnect() (это и есть точка входа
 *    для шага 14 — возврата товаров на склад), а ПОТОМ закрывает
 *    потоки ввода/вывода и сокет.
 */
public abstract class ClientHandler implements Runnable {
    protected final Socket socket;
    protected BufferedReader in;
    protected PrintWriter out;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

            String line;
            while ((line = in.readLine()) != null) {
                onLineReceived(line.trim());
            }
        } catch (IOException e) {
            System.err.println("connection lost");
        } finally {
            close();
        }
    }

    // Реализуется в Cart.java — разбор команд "add" / "pay" (шаг 11)
    protected abstract void onLineReceived(String line);

    // Реализуется в Cart.java — возврат товаров на склад при разрыве связи (шаг 14)
    protected abstract void onDisconnect();

    private void close() {
        onDisconnect();
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (!socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }
}

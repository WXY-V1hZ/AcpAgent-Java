import java.io.*;
import java.net.*;
import java.net.http.*;
import java.net.http.WebSocket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;
import java.util.logging.*;

/**
 * acp-mid: stdio ↔ WebSocket 透明桥
 *
 * 使用: java Main.java [ws://localhost:8081/acp]
 *
 * 日志文件: acp-mid.log（执行目录同级，每次覆盖写入，退出时自动删除）
 */
public class Main {

    private static final Logger log = Logger.getLogger("acp-mid");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final boolean LOG_TO_FILE = false;  // true = 输出日志到 acp-mid.log，false = 仅控制台

    public static void main(String[] args) throws Exception {
        var wsUrl = args.length > 0 ? args[0] : "ws://localhost:8081/acp";

        // ===== 初始化文件日志 =====
        setupFileLogging();
        log.info("=== acp-mid started ===");
        log.info("target WebSocket: " + wsUrl);

        // ===== 连接 BizAgent WebSocket =====
        var httpClient = HttpClient.newHttpClient();
        var received = new LinkedBlockingQueue<String>();
        var msgBuf = new StringBuilder();

        var listener = new WebSocket.Listener() {

            public void onOpen(WebSocket ws) {
                log.info(">>> WebSocket connected to " + wsUrl);
                ws.request(1);  // 请求接收第一条消息
            }

            public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                msgBuf.append(data);
                if (last) {
                    String msg = msgBuf.toString();
                    msgBuf.setLength(0);
                    log.info("<<< WS recv (" + msg.length() + " bytes): " + msg);
                    received.offer(msg);
                }
                ws.request(1);  // 请求接收下一条
                return CompletableFuture.completedFuture(null);
            }

            public void onError(WebSocket ws, Throwable error) {
                log.log(Level.SEVERE, ">>> WebSocket error", error);
            }

            public CompletionStage<?> onClose(WebSocket ws, int status, String reason) {
                log.warning(">>> WebSocket closed: status=" + status + " reason=" + reason);
                return CompletableFuture.completedFuture(null);
            }
        };

        log.info("connecting to " + wsUrl + " ...");
        WebSocket ws;
        try {
            var future = httpClient.newWebSocketBuilder()
                    .buildAsync(URI.create(wsUrl), listener);
            ws = future.get(10, TimeUnit.SECONDS);
            log.info("WebSocket session established");
        } catch (TimeoutException e) {
            log.severe("FATAL: connection timeout after 10s: " + wsUrl);
            log.severe("Make sure BizAgent is running and port is correct");
            System.err.println("[acp-mid] FATAL: Cannot connect to " + wsUrl + " (timeout)");
            System.exit(1);
            return;
        } catch (Exception e) {
            log.severe("FATAL: connection failed: " + e);
            System.err.println("[acp-mid] FATAL: Cannot connect to " + wsUrl + " - " + e.getMessage());
            System.exit(1);
            return;
        }

        // ===== 线程 1: WebSocket 接收 → stdout（给 ZED） =====
        var sender = new Thread(() -> {
            try (var stdout = new BufferedWriter(new OutputStreamWriter(System.out))) {
                while (true) {
                    var msg = received.take();
                    log.info(">>> stdout send (" + msg.length() + " bytes)");
                    stdout.write(msg);
                    stdout.newLine();
                    stdout.flush();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                log.severe("stdout write failed: " + e);
            }
        }, "ws2stdout");
        sender.setDaemon(true);
        sender.start();

        // ===== 主线程: stdin 读（来自 ZED）→ WebSocket 发送 =====
        log.info("ready, waiting for stdin ...");
        try (var stdin = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = stdin.readLine()) != null) {
                log.info("<<< stdin recv (" + line.length() + " bytes)");
                ws.sendText(line, true).join();
                log.info(">>> WS sent (" + line.length() + " bytes)");
            }
        }

        log.info("stdin closed, shutting down");
        ws.sendClose(1000, "acp-mid done");
        log.info("=== acp-mid stopped ===");
    }

    private static void setupFileLogging() throws IOException {
        if (!LOG_TO_FILE) return;  // 未启用文件日志，仅保留控制台输出
        // 文件日志 → acp-mid.log（每次启动覆盖写入，退出时自动删除）
        var logFile = new File("acp-mid.log");
        logFile.deleteOnExit();  // 无论正常退出还是异常退出，JVM 退出时自动删除
        var handler = new FileHandler(logFile.getPath(), false);  // append = false，覆盖写入
        handler.setLevel(Level.ALL);
        handler.setFormatter(new Formatter() {
            @Override
            public String format(LogRecord r) {
                return TS.format(LocalDateTime.now()) + " [" + r.getLevel() + "] "
                        + r.getMessage() + System.lineSeparator();
            }
        });
        log.addHandler(handler);
        log.setLevel(Level.ALL);
    }
}

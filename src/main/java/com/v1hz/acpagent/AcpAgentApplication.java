package com.v1hz.acpagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

@SpringBootApplication
public class AcpAgentApplication {

    public static void main(String[] args) {
        try {
            SpringApplication.run(AcpAgentApplication.class, args);
        } catch (Throwable e) {
            writeCrashLog(e);
            System.exit(1);
        }
    }

    private static void writeCrashLog(Throwable e) {
        try {
            // Spring 可能未启动，无法使用 @Value；直接硬编码，与 acpagent.home-dir 保持一致
            Path logDir = Path.of(System.getProperty("user.home"), ".acpagent", "log");
            Files.createDirectories(logDir);
            try (PrintWriter pw = new PrintWriter(logDir.resolve("error").toFile(), "UTF-8")) {
                pw.println("=== AcpAgent Crash Report ===");
                pw.println("Time: " + LocalDateTime.now());
                pw.println();
                e.printStackTrace(pw);
            }
        } catch (Exception ignored) {
            // 日志写入失败不影响进程退出
        }
    }
}

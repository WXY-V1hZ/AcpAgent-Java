package com.v1hz.bizagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

@SpringBootApplication
public class BizAgentApplication {

    public static void main(String[] args) {
        try {
            SpringApplication.run(BizAgentApplication.class, args);
        } catch (Throwable e) {
            writeCrashLog(e);
            System.exit(1);
        }
    }

    private static void writeCrashLog(Throwable e) {
        try {
            Path logDir = Path.of(System.getProperty("user.home"), ".bizagent", "log");
            Files.createDirectories(logDir);
            try (PrintWriter pw = new PrintWriter(logDir.resolve("error").toFile(), "UTF-8")) {
                pw.println("=== BizAgent Crash Report ===");
                pw.println("Time: " + LocalDateTime.now());
                pw.println();
                e.printStackTrace(pw);
            }
        } catch (Exception ignored) {
            // 日志写入失败不影响进程退出
        }
    }
}

package com.v1hz.acpagent.tool;

import com.alibaba.cloud.ai.graph.agent.tools.ToolContextHelper;
import com.v1hz.acpagent.entity.Session;
import com.v1hz.acpagent.service.SessionService;
import com.v1hz.acpagent.tool.schema.input.BashInputSchema;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Bash 命令执行工具，供 AI Agent 安全地执行 shell 命令并获取结果。
 *
 * <h3>核心设计原则</h3>
 * <ul>
 *   <li><b>超时兜底</b> — 每个命令有独立的超时控制，超时后强制 SIGKILL 子进程</li>
 *   <li><b>防管道死锁</b> — 用独立守护线程消费 stdout，避免 OS pipe buffer 写满后子进程阻塞</li>
 *   <li><b>输出截断</b> — 返回给模型的内容限制在 {@value #MAX_OUTPUT_LENGTH} 字符以内，防止撑爆上下文窗口</li>
 *   <li><b>工作目录隔离</b> — 通过 {@link ToolContext} 获取 session cwd 作为执行目录，不继承 JVM 默认 cwd</li>
 *   <li><b>stderr 合并</b> — 将 stderr 重定向到 stdout，简化输出消费逻辑</li>
 * </ul>
 */
@Slf4j
@Component
public class BashTools implements BaseTool {

    /**
     * 返回给 LLM 的最大输出字符数。
     * <p>
     * 超出部分截断，且只保留尾部内容。因为命令行工具通常把最重要的结果（输出数据、错误信息）
     * 放在最后输出，保留尾部比保留头部对模型的决策更有价值。
     */
    private static final int MAX_OUTPUT_LENGTH = 10_000;
    private final SessionService sessionService;

    public BashTools(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    /**
     * 执行一条 bash 命令并将结果返回给模型。
     *
     * <h3>执行流程</h3>
     * <ol>
     *   <li>通过 {@link ProcessBuilder} 创建子进程，以 {@code bash -c <command>} 方式执行，
     *       支持管道、重定向、通配符等 shell 特性</li>
     *   <li>启动独立守护线程消费 stdout（见 {@link #getOutput(Process, StringBuilder)}），
     *       防止 OS pipe buffer 写满后子进程阻塞</li>
     *   <li>主线程调用 {@link Process#waitFor(long, TimeUnit)} 等待子进程结束</li>
     *   <li>超时则强制 {@link Process#destroyForcibly()} 终止子进程</li>
     *   <li>输出超出 {@link #MAX_OUTPUT_LENGTH} 时截断保留尾部</li>
     * </ol>
     *
     * <h3>返回值格式</h3>
     * <pre>{@code
     * <stdout 与 stderr 的合并输出>
     * Exit code: <退出码>
     * }</pre>
     *
     * @param bashInputSchema      包含命令、超时秒数的参数对象
     * @param toolContext 工具上下文，从中获取 session cwd 作为执行目录
     * @return 格式化后的命令执行结果，或错误信息
     */
    @Tool(description = """
            执行Bash命令并返回其输出
            示例输入：
            {
              "bashInputSchema": {
                "command": "要执行的命令",
                "timeoutSeconds": 30
              }
            }
            注意不要遗漏bashInputSchema这个顶层节点
            """
    )
    public String executeBash(@ToolParam BashInputSchema bashInputSchema, ToolContext toolContext) {
        // TODO check command
        try {
            var config = ToolContextHelper.getConfig(toolContext).orElseThrow();
            String sessionId = config.threadId().orElseThrow();
            Session session = sessionService.findById(sessionId).orElseThrow();
            String cwd = session.getCwd();
            String command = bashInputSchema.getCommand();
            int timeoutSeconds = bashInputSchema.getTimeoutSeconds();

            // 以 bash -c <command> 执行，确保 shell 特性（管道、通配符、变量展开等）正常工作
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
            pb.directory(new File(cwd));
            // 合并 stderr → stdout：避免两路流独立消费时遗漏其中一路、导致管道死锁
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 守护线程异步消费 stdout，防止 OS pipe buffer（64KB）写满后子进程阻塞
            StringBuilder fullOutput = new StringBuilder();
            Thread consumer = getOutput(process, fullOutput);

            // 等待子进程结束，超时则强制终止
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();            // 发送 SIGKILL
                process.waitFor(5, TimeUnit.SECONDS); // 等待进程彻底消亡
                log.warn("Bash command timed out after {}s: {}", timeoutSeconds, command);
                return "Error: Command timed out after " + timeoutSeconds + " seconds.";
            }

            // 确保消费者线程已完成读取
            consumer.join(5_000);
            int exitCode = process.exitValue();

            // 截断输出，保留尾部（最新输出最有价值）
            String output = fullOutput.toString();
            if (output.length() > MAX_OUTPUT_LENGTH) {
                output = "... [output truncated, showing last " + MAX_OUTPUT_LENGTH + " characters]\n"
                        + output.substring(output.length() - MAX_OUTPUT_LENGTH);
            }

            return output.stripTrailing() + "\nExit code: " + exitCode;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Bash command interrupted", e);
            return "Error: Command was interrupted.";
        } catch (Exception e) {
            log.error("Error executing bash command", e);
            return "Error executing command: " + e.getMessage();
        }
    }

    /**
     * 启动一个守护线程来异步消费子进程的 stdout。
     * <p>
     * 子进程的 stdout 连接到一个 OS pipe。如果不及时读取，pipe buffer（通常 64KB）写满后
     * 子进程的 {@code write()} 会阻塞，导致父子进程互相等待的死锁：
     * <pre>{@code
     * parent: process.waitFor() 等待子进程结束
     * child:  write(stdout) 等待 parent 读取 → 死锁
     * }</pre>
     * 因此必须在主线程 {@code waitFor()} 的同时、用独立线程持续消费 stdout。
     *
     * @param process    已启动的子进程
     * @param fullOutput 累积输出的 StringBuilder（只有该线程写入，无需同步）
     * @return 消费者线程引用，调用方可调用 {@link Thread#join(long)} 确保输出读完
     */
    private static @NonNull Thread getOutput(Process process, StringBuilder fullOutput) {
        Thread consumer = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    fullOutput.append(line).append("\n");
                }
            } catch (IOException e) {
                // 流关闭属于正常结束（destroyForcibly 关闭输入流），此处不处理
            }
        }, "bash-output-consumer");
        consumer.setDaemon(true);
        consumer.start();
        return consumer;
    }

    @Override
    public List<String> getAllowedToolNames() {
        return List.of(
//                "executeBash"
        );
    }
}

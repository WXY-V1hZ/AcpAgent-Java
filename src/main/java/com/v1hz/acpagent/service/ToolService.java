package com.v1hz.acpagent.service;

import com.agentclientprotocol.sdk.agent.AcpAsyncAgent;
import com.agentclientprotocol.sdk.spec.AcpSchema;
import com.agentclientprotocol.sdk.spec.AcpSchema.*;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.v1hz.acpagent.constants.enums.PermissionOptionEnum;
import com.v1hz.acpagent.constants.enums.SessionUpdateEnum;
import com.v1hz.acpagent.tool.BaseTool;
import com.v1hz.acpagent.tool.BashTools;
import com.v1hz.acpagent.tool.FileSystemTools;
import com.v1hz.acpagent.tool.MiscTools;
import com.v1hz.acpagent.tool.schema.input.*;
import com.v1hz.acpagent.tool.schema.utils.SchemaParser;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ToolService {

    private static Set<String> allowedToolNames;
    private final MiscTools miscTools;
    private final BashTools bashTools;
    private final FileSystemTools fileSystemTools;
    private final ObjectProvider<AcpAsyncAgent> agentProvider;
    private final SchemaParser schemaParser;

    public ToolService(MiscTools miscTools, BashTools bashTools, FileSystemTools fileSystemTools,
                       ObjectProvider<AcpAsyncAgent> agentProvider, SchemaParser schemaParser) {
        this.miscTools = miscTools;
        this.bashTools = bashTools;
        this.fileSystemTools = fileSystemTools;
        this.agentProvider = agentProvider;
        this.schemaParser = schemaParser;
    }

    @PostConstruct
    public void init() {
        allowedToolNames = Arrays.stream(getTools())
                .flatMap(tool -> tool.getAllowedToolNames().stream())
                .collect(Collectors.toSet());
    }

    public BaseTool[] getTools() {
        return new BaseTool[]{
                miscTools,
                bashTools,
                fileSystemTools,
        };
    }

    /**
     * 创建并应用工具特定配置的链。根据工具名解析参数并设置 title / kind。
     * 新增工具的自定义配置在此处统一添加。
     *
     * @return 已配置的链，若参数解析失败则返回 null
     */
    public ToolCallUpdateChain createUpdateChain(String sessionId, String toolCallId, String toolName, String arguments) {
        var chain = new ToolCallUpdateChain(agentProvider.getObject(), sessionId, toolCallId, toolName, arguments);
        switch (toolName) {
            case "executeBash" -> {
                BashInputSchema input = schemaParser.parse(arguments, BashInputSchema.class);
                if (input == null) return null;
                chain.title(input.getCommand())
                        .kind(ToolKind.EXECUTE)
                        .input(input);
            }
            case "readFile" -> {
                ReadInputSchema input = schemaParser.parse(arguments, ReadInputSchema.class);
                if (input == null) return null;
                chain.location(input.getFilePath())
                        .kind(ToolKind.READ)
                        .title(toolName + ": " + input.getFilePath())
                        .input(input);
            }
            case "writeFile" -> {
                WriteInputSchema input = schemaParser.parse(arguments, WriteInputSchema.class);
                if (input == null) return null;
                chain.location(input.getFilePath())
                        .kind(AcpSchema.ToolKind.EDIT)
                        .title(toolName + ": " + input.getFilePath())
                        .input(input);
                try {
                    Path path = Paths.get(input.getFilePath());
                    String oldText = Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8) : "";
                    chain.diff(input.getFilePath(), oldText, input.getContent());
                } catch (IOException e) {
                    log.warn("Failed to read old content for diff: {}", input.getFilePath(), e);
                }
            }
            case "editFile" -> {
                EditInputSchema input = schemaParser.parse(arguments, EditInputSchema.class);
                if (input == null) return null;
                chain.location(input.getFilePath())
                        .kind(ToolKind.EDIT)
                        .diff(input.getFilePath(), input.getOldContent(), input.getNewContent())
                        .title(toolName + ": " + input.getFilePath())
                        .input(input);
            }
            case "listDirectory" -> {
                ListDirectoryInputSchema input = schemaParser.parse(arguments, ListDirectoryInputSchema.class);
                if (input == null) return null;
                chain.location(input.getPath())
                        .kind(ToolKind.SEARCH)
                        .title(toolName + ": " + input.getPath())
                        .input(input);
            }
        }
        return chain;
    }

    /**
     * ACP 工具调用状态通知链。
     * <p>
     * 链式发送 PENDING → IN_PROGRESS → COMPLETED 三条通知。
     * 默认 title 为工具名、kind 为 {@link AcpSchema.ToolKind#OTHER}，
     * 可通过 {@link #title(String)} 和 {@link #kind(AcpSchema.ToolKind)} 覆盖。
     */
    public static class ToolCallUpdateChain {

        private final AcpAsyncAgent agent;
        private final String sessionId;
        private final String toolCallId;
        private final String toolName;
        private String title;
        private Object input;
        private AcpSchema.ToolKind kind;
        private List<ToolCallLocation> locations;
        private List<ToolCallContent> diffs;

        ToolCallUpdateChain(AcpAsyncAgent agent, String sessionId, String toolCallId, String toolName, Object input) {
            this.agent = agent;
            this.sessionId = sessionId;
            this.toolCallId = toolCallId;
            this.toolName = toolName;
            this.title = toolName;
            this.input = input;
            this.kind = AcpSchema.ToolKind.OTHER;
        }

        /**
         * 设置通知标题，默认取工具名。
         */
        public ToolCallUpdateChain input(Object input) {
            this.input = input;
            return this;
        }

        /**
         * 设置通知标题，默认取工具名。
         */
        public ToolCallUpdateChain title(String title) {
            this.title = title;
            return this;
        }

        /**
         * 设置工具类型，默认 {@link AcpSchema.ToolKind#OTHER}。
         */
        public ToolCallUpdateChain kind(AcpSchema.ToolKind kind) {
            this.kind = kind;
            return this;
        }

        /**
         * 标记工具操作的目标文件位置，客户端可高亮该文件。
         */
        public ToolCallUpdateChain location(String filePath) {
            this.locations = List.of(new ToolCallLocation(filePath, null));
            return this;
        }

        /**
         * 便捷方法：添加一个文件 diff（旧 → 新）。
         */
        public ToolCallUpdateChain diff(String filePath, String oldText, String newText) {
            this.diffs = List.of(new ToolCallDiff("diff", filePath, oldText, newText));
            return this;
        }

        /**
         * 检查工具是否在允许名单中，不在则请求用户授权。
         * <p>
         * 允许名单中的工具直接放行（返回 null）。
         * 不允许的工具发送权限请求：批准后放行（返回 null），拒绝则发送 FAILED 并返回错误响应。
         *
         * @return null 表示允许执行，非 null 为拒绝的 {@link ToolCallResponse}
         */
        public ToolCallResponse check() {
            if (allowedToolNames.contains(toolName)) {
                return null;
            }

            List<PermissionOption> options = List.of(
                    PermissionOptionEnum.ALLOW_ONCE.toPermissionOption(),
                    PermissionOptionEnum.REJECT_ONCE.toPermissionOption()
                    // TODO 添加另外两个Option
            );

            try {
                RequestPermissionResponse response = agent.requestPermission(
                        new RequestPermissionRequest(sessionId,
                                new AcpSchema.ToolCallUpdate(toolCallId, title, kind,
                                        AcpSchema.ToolCallStatus.PENDING, null, null, input, null),
                                options)
                ).block();

                if (response != null && response.outcome() instanceof PermissionSelected selected
                        && PermissionOptionEnum.ALLOW_ONCE.getId().equals(selected.optionId())) {
                    return null;
                }
            } catch (Exception e) {
                log.warn("权限请求失败: {}", toolName, e);
            }

            failed();
            return ToolCallResponse.error(toolCallId, toolName, "操作被拒绝");
        }

        /**
         * 发送 PENDING 状态通知。
         */
        public ToolCallUpdateChain pending() {
            send(AcpSchema.ToolCallStatus.PENDING, null);
            return this;
        }

        /**
         * 发送 IN_PROGRESS 状态通知。
         */
        public ToolCallUpdateChain inProgress() {
            send(AcpSchema.ToolCallStatus.IN_PROGRESS, null);
            return this;
        }

        /**
         * 发送 COMPLETED 状态通知并携带执行结果。
         */
        public void completed(String result) {
            send(AcpSchema.ToolCallStatus.COMPLETED, result);
        }

        private void send(AcpSchema.ToolCallStatus status, String result) {
            agent.sendSessionUpdate(sessionId,
                    new AcpSchema.ToolCallUpdateNotification(
                            SessionUpdateEnum.TOOL_CALL.getValue(),
                            toolCallId,
                            title,
                            kind,
                            status,
                            diffs,
                            locations,
                            input,
                            result,
                            null
                    )
            ).block();
        }

        /**
         * 发送 FAILED 状态通知。
         */
        public void failed() {
            try {
                send(AcpSchema.ToolCallStatus.FAILED, null);
            } catch (Exception e) {
                log.warn("发送 FAILED 状态通知失败", e);
            }
        }
    }
}

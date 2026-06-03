package com.v1hz.acpagent.service;

import com.agentclientprotocol.sdk.agent.AcpAsyncAgent;
import com.agentclientprotocol.sdk.spec.AcpSchema;
import com.agentclientprotocol.sdk.spec.AcpSchema.*;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.v1hz.acpagent.constants.enums.PermissionOptionEnum;
import com.v1hz.acpagent.constants.enums.SessionUpdateEnum;
import com.v1hz.acpagent.tool.*;
import com.v1hz.acpagent.tool.schema.input.BashInputSchema;
import com.v1hz.acpagent.tool.schema.utils.SchemaParser;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ToolService {

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

    private static Set<String> allowedToolNames;

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

    public Set<String> getAllowedToolNames() {
        // 返回不可变视图，防止外部修改缓存
        return Collections.unmodifiableSet(allowedToolNames);
    }

    /**
     * 创建并应用工具特定配置的链。根据工具名解析参数并设置 title / kind。
     * 新增工具的自定义配置在此处统一添加。
     *
     * @return 已配置的链，若参数解析失败则返回 null
     */
    public ToolCallUpdateChain createUpdateChain(String sessionId, String toolCallId, String toolName, String arguments) {
        var chain = new ToolCallUpdateChain(agentProvider.getObject(), sessionId, toolCallId, toolName, arguments);
        if ("executeBash".equals(toolName)) {
            BashInputSchema input = schemaParser.parse(arguments, BashInputSchema.class);
            if (input == null) return null;
            chain.title(input.getCommand()).kind(AcpSchema.ToolKind.EXECUTE);
        }
        // 未来新增工具在此添加分支
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
        private final Object input;
        private AcpSchema.ToolKind kind;

        ToolCallUpdateChain(AcpAsyncAgent agent, String sessionId, String toolCallId, String toolName, Object input) {
            this.agent = agent;
            this.sessionId = sessionId;
            this.toolCallId = toolCallId;
            this.toolName = toolName;
            this.title = toolName;
            this.input = input;
            this.kind = AcpSchema.ToolKind.OTHER;
        }

        /** 设置通知标题，默认取工具名。 */
        public ToolCallUpdateChain title(String title) {
            this.title = title;
            return this;
        }

        /** 设置工具类型，默认 {@link AcpSchema.ToolKind#OTHER}。 */
        public ToolCallUpdateChain kind(AcpSchema.ToolKind kind) {
            this.kind = kind;
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

        /** 发送 PENDING 状态通知。 */
        public ToolCallUpdateChain pending() {
            send(AcpSchema.ToolCallStatus.PENDING, null);
            return this;
        }

        /** 发送 IN_PROGRESS 状态通知。 */
        public ToolCallUpdateChain inProgress() {
            send(AcpSchema.ToolCallStatus.IN_PROGRESS, null);
            return this;
        }

        /** 发送 COMPLETED 状态通知并携带执行结果。 */
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
                            null,
                            null,
                            input,
                            result,
                            null
                    )
            ).block();
        }

        private void failed() {
            try {
                send(AcpSchema.ToolCallStatus.FAILED, null);
            } catch (Exception e) {
                log.warn("发送 FAILED 状态通知失败", e);
            }
        }
    }
}

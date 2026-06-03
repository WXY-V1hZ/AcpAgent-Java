package com.v1hz.acpagent.interceptor;

import com.agentclientprotocol.sdk.spec.AcpSchema;
import com.alibaba.cloud.ai.graph.agent.interceptor.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v1hz.acpagent.service.ToolService;
import com.v1hz.acpagent.tool.schema.input.BashInputSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 工具状态拦截器 — 按工具名分支处理，统一管理 ACP 状态通知与权限检查。
 * <p>
 * <ul>
 *   <li>{@code executeBash} — 解析参数，以命令原文为标题，标记 {@link AcpSchema.ToolKind#EXECUTE}</li>
 *   <li>其他工具 — 使用原始参数，默认标题和 {@link AcpSchema.ToolKind#OTHER}</li>
 * </ul>
 * 公共流程：PENDING → check 权限 → IN_PROGRESS → 执行 → COMPLETED
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolStatusInterceptor extends ToolInterceptor {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ToolService toolService;

    @Override
    public String getName() {
        return "toolStatusInterceptor";
    }

    @Override
    public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
        // executeBash 走特殊分支，其余工具走默认流程
        String toolName = request.getToolName();
        String toolCallId = request.getToolCallId();
        String arguments = request.getArguments();
        String sessionId = request.getExecutionContext()
                .flatMap(ToolCallExecutionContext::threadId)
                .orElseThrow();

        var chain = toolService.update(sessionId, toolCallId, toolName, arguments);

        if ("executeBash".equals(toolName)) {
            BashInputSchema input = parseInput(arguments);
            if (input == null) {
                return ToolCallResponse.error(toolCallId, toolName, "无法解析工具参数");
            }
            chain.title(input.getCommand()).kind(AcpSchema.ToolKind.EXECUTE);
        }

        ToolCallResponse denied = chain.pending().check();
        if (denied != null) return denied;
        chain.inProgress();
        ToolCallResponse result = handler.call(request);
        chain.completed(result.getResult());
        return result;
    }

    /** 从 {@code {"bashInputSchema": {...}}} 中还原参数对象。 */
    private BashInputSchema parseInput(String arguments) {
        if (arguments == null || arguments.isEmpty()) return null;
        try {
            var root = objectMapper.readTree(arguments);
            var inputNode = root.get("bashInputSchema");
            if (inputNode == null) return null;
            return objectMapper.treeToValue(inputNode, BashInputSchema.class);
        } catch (Exception e) {
            log.warn("Failed to parse tool arguments: {}", arguments, e);
            return null;
        }
    }
}

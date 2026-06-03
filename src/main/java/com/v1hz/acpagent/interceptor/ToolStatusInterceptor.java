package com.v1hz.acpagent.interceptor;

import com.alibaba.cloud.ai.graph.agent.interceptor.*;
import com.v1hz.acpagent.service.ToolService;
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

        var chain = toolService.createUpdateChain(sessionId, toolCallId, toolName, arguments);
        if (chain == null) {
            return ToolCallResponse.error(toolCallId, toolName, "无法解析工具参数");
        }

        ToolCallResponse denied = chain.pending().check();
        if (denied != null) return denied;
        chain.inProgress();
        ToolCallResponse result = handler.call(request);
        chain.completed(result.getResult());
        return result;
    }
}

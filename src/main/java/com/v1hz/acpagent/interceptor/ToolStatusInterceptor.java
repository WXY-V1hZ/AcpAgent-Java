package com.v1hz.acpagent.interceptor;

import com.alibaba.cloud.ai.graph.agent.interceptor.*;
import com.v1hz.acpagent.service.CancellationService;
import com.v1hz.acpagent.service.ToolService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 工具状态拦截器 — 按工具名分支处理，统一管理 ACP 状态通知与权限检查。
 * <p>
 * <ul>
 * </ul>
 * 公共流程：PENDING → check 权限 → IN_PROGRESS → 执行 → COMPLETED
 */
@Slf4j
@Component
public class ToolStatusInterceptor extends ToolInterceptor {

    private final ToolService toolService;
    private final CancellationService cancellationService;

    public ToolStatusInterceptor(ToolService toolService, CancellationService cancellationService) {
        this.toolService = toolService;
        this.cancellationService = cancellationService;
    }

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
        if (cancellationService.isCancelled(sessionId)) {
            log.warn("Tool execution cancelled: {}", toolName);
            chain.failed();
            return ToolCallResponse.error(toolCallId, toolName, "Session was cancelled");
        }
        cancellationService.registerThread(sessionId);
        try {
            ToolCallResponse result = handler.call(request);
            chain.completed(result.getResult());
            return result;
        } catch (Exception e) {
            log.error("Tool execution failed: {}", toolName, e);
            chain.failed();
            return ToolCallResponse.error(toolCallId, toolName, e.getMessage());
        } finally {
            cancellationService.unregisterThread(sessionId);
        }
    }
}

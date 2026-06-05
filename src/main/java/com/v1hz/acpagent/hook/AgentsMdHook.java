package com.v1hz.acpagent.hook;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.messages.SystemMessage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * 在 Agent 启动前读取用户级和项目级 AGENTS.md，并在模型调用前追加到 system prompt。
 * <p>
 * 用户级文件用于长期偏好和全局约束，项目级文件用于当前仓库的编码规范和架构说明。
 * 若两个路径最终指向同一个文件，则只注入一次，避免重复放大系统提示词。
 */
@Slf4j
@HookPositions(HookPosition.BEFORE_AGENT)
public class AgentsMdHook extends AgentHook {

    private final Path userPath;
    private final Path projectPath;

    /**
     * beforeAgent 负责刷新内容，模型拦截器负责读取并注入。
     * 当前每次 prompt 都会创建独立 ReactAgent，因此这里不需要跨会话共享缓存。
     */
    private String cachedContent;

    public AgentsMdHook(Builder builder) {
        this.userPath = builder.userPath;
        this.projectPath = builder.projectPath;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 显式区分用户级和项目级路径，避免调用方用顺序参数表达语义。
     */
    public static class Builder {

        private Path userPath;
        private Path projectPath;

        public Builder userPath(Path userPath) {
            this.userPath = userPath;
            return this;
        }

        public Builder projectPath(Path projectPath) {
            this.projectPath = projectPath;
            return this;
        }

        public AgentsMdHook build() {
            if (userPath == null) throw new IllegalArgumentException("userPath must be provided");
            if (projectPath == null) throw new IllegalArgumentException("projectPath must be provided");
            return new AgentsMdHook(this);
        }
    }

    @Override
    public CompletableFuture<Map<String, Object>> beforeAgent(OverAllState state, RunnableConfig config) {
        final var userContent = readContent("User AGENTS.md", userPath);
        final var duplicateProjectPath = samePath(userPath, projectPath);
        final var projectContent = duplicateProjectPath
                ? ""
                : readContent("Project AGENTS.md", projectPath);
        cachedContent = Stream.of(userContent, projectContent)
                .filter(StringUtils::isNotBlank)
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse(null);
        if (duplicateProjectPath) {
            log.info("Skipped duplicate project AGENTS.md. userPath={}, projectPath={}", userPath, projectPath);
        }
        return CompletableFuture.completedFuture(Map.of());
    }

    private String readContent(String title, Path path) {
        // AGENTS.md 是可选配置；不存在时不应影响 Agent 正常启动。
        if (!Files.exists(path)) {
            log.info("AGENTS.md not found. title={}, path={}", title, path);
            return "";
        }
        try {
            final var content = Files.readString(path);
            if (StringUtils.isBlank(content)) {
                log.info("AGENTS.md is blank. title={}, path={}", title, path);
                return "";
            }
            return "# " + title + "\n\nPath: `" + path + "`\n\n" + content;
        } catch (IOException e) {
            log.warn("Failed to read AGENTS.md. title={}, path={}, error={}", title, path, e.getMessage());
            return "";
        }
    }

    private boolean samePath(Path a, Path b) {
        final var normalizedA = a.toAbsolutePath().normalize();
        final var normalizedB = b.toAbsolutePath().normalize();
        if (normalizedA.equals(normalizedB)) return true;
        if (!Files.exists(a) || !Files.exists(b)) return false;
        try {
            // Resolve symlinks and filesystem aliases to avoid injecting the same AGENTS.md twice.
            return a.toRealPath().equals(b.toRealPath());
        } catch (IOException e) {
            log.debug("Failed to resolve AGENTS.md real path. userPath={}, projectPath={}, error={}", a, b, e.getMessage());
            return false;
        }
    }

    @Override
    public List<ModelInterceptor> getModelInterceptors() {
        return List.of(new AgentsMdInterceptor());
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    private class AgentsMdInterceptor extends ModelInterceptor {

        @Override
        public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
            if (StringUtils.isBlank(cachedContent)) return handler.call(request);
            // 保留已有 system prompt，并将 AGENTS.md 作为额外上下文追加到末尾。
            var existingText = request.getSystemMessage() != null
                    ? request.getSystemMessage().getText()
                    : "";
            var modified = ModelRequest.builder(request)
                    .systemMessage(new SystemMessage(existingText + "\n\n" + cachedContent))
                    .build();
            return handler.call(modified);
        }

        @Override
        public String getName() {
            return "AgentMdInterceptor";
        }
    }
}

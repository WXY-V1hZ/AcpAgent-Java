package com.v1hz.acpagent.service;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.alibaba.cloud.ai.graph.checkpoint.savers.file.FileSystemSaver;
import com.alibaba.cloud.ai.graph.serializer.plain_text.jackson.SpringAIJacksonStateSerializer;
import com.v1hz.acpagent.component.DeepSeekChatModel;
import com.v1hz.acpagent.interceptor.ToolStatusInterceptor;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Collection;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final ToolService toolService;
    private final ToolStatusInterceptor toolStatusInterceptor;
    @Value("${spring.ai.deepseek.api-key}")
    String apiKey;
    @Value("${acpagent.home-dir}")
    String homeDir;
    private BaseCheckpointSaver saver;

    @PostConstruct
    void initSaver() {
        var sessionsDir = Path.of(System.getProperty("user.home"), homeDir, "sessions", "checkpoints");
        var stateSerializer = new SpringAIJacksonStateSerializer(OverAllState::new);
        saver = FileSystemSaver.builder()
                .targetFolder(sessionsDir)
                .stateSerializer(stateSerializer)
                .build();
    }

    @NonNull
    public String buildSystemPrompt(@NonNull String cwd) {
        return String.format("""
                        你是一个运行在终端的AI智能助手。你的运行环境如下：
                        当前工作目录的绝对路径 {{cwd}}: %s
                        """,
                cwd
        );
    }

    @NonNull
    public DeepSeekApi createDeepSeekApi() {
        return DeepSeekApi.builder().apiKey(apiKey).build();
    }

    @NonNull
    public DeepSeekChatModel createChatModel(@NonNull DeepSeekApi deepSeekApi, @NonNull String modelName) {
        return DeepSeekChatModel.builder()
                .deepSeekApi(deepSeekApi)
                .defaultOptions(
                        DeepSeekChatOptions.builder().model(modelName).build()
                )
                .build();
    }

    /**
     * 读取指定 session 的检查点（消息历史），用于 loadSession 回放。
     */
    @NonNull
    public Collection<Checkpoint> getSessionCheckpoints(@NonNull String sessionId) {
        var config = RunnableConfig.builder().threadId(sessionId).build();
        return saver.list(config);
    }

    @NonNull
    public ReactAgent createReactAgent(@NonNull ChatModel chatModel, @NonNull String cwd) {
        return ReactAgent.builder()
                .name("Agent智能体")
                .model(chatModel)
                .systemPrompt(buildSystemPrompt(cwd))
                .methodTools((Object[]) toolService.getTools())
                .saver(saver)
                .interceptors(toolStatusInterceptor)
                .build();
    }
}

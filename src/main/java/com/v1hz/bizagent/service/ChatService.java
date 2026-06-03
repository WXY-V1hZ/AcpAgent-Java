package com.v1hz.bizagent.service;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.v1hz.bizagent.component.DeepSeekChatModel;
import com.v1hz.bizagent.constants.enums.SessionModelEnum;
import com.v1hz.bizagent.dto.response.ApiResponse;
import com.v1hz.bizagent.dto.response.SseResponse;
import com.v1hz.bizagent.entity.Session;
import com.v1hz.bizagent.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.ai.deepseek.DeepSeekAssistantMessage;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final AgentService agentService;
    private final SessionService sessionService;


    /**
     * 返回原始 NodeOutput 流供 AcpAgent 消费。
     * AcpAgent 拿到 Flux 后逐个 flatMap，调用 ctx.sendMessage/sendThought。
     */
    public @NonNull Flux<NodeOutput> chatStreamAcp(
            @NonNull String text,
            @NonNull List<Media> media,
            @NonNull String sessionId
    ) {
        UserMessage userMessage = UserMessage.builder()
                .text(text)
                .media(media)
                .build();
        return createAgentStream(userMessage, sessionId);
    }

    /**
     * 核心方法：创建 Agent 并返回原始 NodeOutput 流。
     */
    private @NonNull Flux<NodeOutput> createAgentStream(
            @NonNull UserMessage userMessage,
            @NonNull String sessionId
    ) {
        return Mono.fromCallable(() -> {
                    Session session = sessionService.findById(sessionId).orElseThrow();
                    DeepSeekApi deepSeekApi = agentService.createDeepSeekApi();
                    var sessionModel = SessionModelEnum.fromId(session.getModelId()).orElseThrow();
                    DeepSeekChatModel chatModel = agentService.createChatModel(deepSeekApi, sessionModel.getModelName());
                    return agentService.createReactAgent(chatModel, session.getCwd());
                })
                .flatMapMany(agent -> {
                    try {
                        return agent.stream(
                                userMessage,
                                RunnableConfig.builder().threadId(sessionId).build()
                        );
                    } catch (GraphRunnerException e) {
                        return Flux.error(new BusinessException(e));
                    }
                });
    }
}

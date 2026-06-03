package com.v1hz.acpagent.service;

import com.agentclientprotocol.sdk.agent.AcpAsyncAgent;
import com.agentclientprotocol.sdk.spec.AcpSchema.AgentMessageChunk;
import com.agentclientprotocol.sdk.spec.AcpSchema.TextContent;
import com.agentclientprotocol.sdk.spec.AcpSchema.UserMessageChunk;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class SessionUpdateService {

    private final ObjectProvider<AcpAsyncAgent> agentProvider;
    private final ToolService toolService;

    public SessionUpdateService(ObjectProvider<AcpAsyncAgent> agentProvider, ToolService toolService) {
        this.agentProvider = agentProvider;
        this.toolService = toolService;
    }

    /**
     * 将历史消息列表逐条回放到 ACP 会话。
     */
    public void replayMessages(String sessionId, List<?> messages) {
        Map<String, ToolService.ToolCallUpdateChain> chains = new HashMap<>();
        for (Object raw : messages) {
            if (raw instanceof UserMessage userMsg) {
                sendUserMessage(sessionId, userMsg);
            } else if (raw instanceof AssistantMessage assistantMsg) {
                sendAssistantMessage(sessionId, assistantMsg, chains);
            } else if (raw instanceof ToolResponseMessage toolMsg) {
                sendToolResponse(sessionId, toolMsg, chains);
            }
        }
    }

    private void sendUserMessage(String sessionId, UserMessage msg) {
        agentProvider.getObject().sendSessionUpdate(sessionId,
                new UserMessageChunk("user_message_chunk",
                        new TextContent(msg.getText()))).block();
    }

    private void sendAssistantMessage(String sessionId, AssistantMessage msg,
                                      Map<String, ToolService.ToolCallUpdateChain> chains) {
        AcpAsyncAgent agent = agentProvider.getObject();
        if (StringUtils.isNotEmpty(msg.getText())) {
            agent.sendSessionUpdate(sessionId,
                    new AgentMessageChunk("agent_message_chunk",
                            new TextContent(msg.getText()))).block();
        }
        if (msg.hasToolCalls()) {
            for (var tc : msg.getToolCalls()) {
                var chain = toolService.createUpdateChain(sessionId, tc.id(), tc.name(), tc.arguments());
                chain.pending().inProgress();
                chains.put(tc.id(), chain);
            }
        }
    }

    private void sendToolResponse(String sessionId, ToolResponseMessage msg,
                                  Map<String, ToolService.ToolCallUpdateChain> chains) {
        for (var resp : msg.getResponses()) {
            var chain = chains.remove(resp.id());
            if (chain != null) {
                chain.completed(resp.responseData());
            }
        }
    }
}

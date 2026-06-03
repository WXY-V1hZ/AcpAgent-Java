package com.v1hz.bizagent;

import com.agentclientprotocol.sdk.spec.AcpSchema;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.alibaba.cloud.ai.graph.checkpoint.savers.file.FileSystemSaver;
import com.alibaba.cloud.ai.graph.serializer.plain_text.jackson.SpringAIJacksonStateSerializer;
import com.v1hz.bizagent.service.AgentService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

@Slf4j
@SpringBootTest
public class LoadSessionTest {

    @Autowired
    private AgentService agentService;

    @Test
    public void test() {
        Collection<Checkpoint> checkpoints = agentService.getSessionCheckpoints("d9c197c9-078c-4a1a-bdce-9abb837bea66");
        // 最新 checkpoint 包含全部累积消息
        Checkpoint latest = checkpoints.iterator().next();
        Object msgs = latest.getState().get("messages");
        if (!(msgs instanceof List<?> msgList)) return ;
        for (Object raw : msgList) {
            if (raw instanceof UserMessage userMsg) {
                log.info(userMsg.toString());
            } else if (raw instanceof AssistantMessage assistantMsg
                    && StringUtils.isNotEmpty(assistantMsg.getText())) {
                log.info(assistantMsg.toString());
            }
        }
    }
}

package com.v1hz.acpagent.tool;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.v1hz.acpagent.interceptor.ToolStatusInterceptor;
import com.v1hz.acpagent.service.AgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class SubAgentConfig {

    private final AgentService agentService;
    private final WebSearchTools webSearchTools;
    private final ToolStatusInterceptor toolStatusInterceptor;

    @Bean
    public ReactAgent webSearchAgent() {
        var chatModel = agentService.createChatModel("deepseek-v4-flash");
        return ReactAgent.builder()
                .name("web_search_agent")
                .model(chatModel)
                .description("专门负责网络搜索，会总结并汇报搜索结果")
                .instruction("""
                        你是联网搜索助手，通过搜索和抓取网页来准确回答用户问题。

                        ## 工作流程
                        1. 使用 webSearchPages 搜索关键词，默认搜 5 条即可
                        2. 调用 webFetch 抓取具体页面获取详情
                        3. 综合搜索结果和抓取内容，给出汇报

                        ## 输出要求
                        - 先给出直接回答，再列出引用来源（标题 + URL）
                        - 若搜索结果不够，诚实告知，不要编造
                        - 中英文混合搜索时，优先用中文关键词
                        """)
                .methodTools(new Object[]{webSearchTools})
//                .interceptors(toolStatusInterceptor)
                .build();
    }
}

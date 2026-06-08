package com.v1hz.acpagent.tool;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class WebSearchTools {

    private final WebSearchService webSearchService;

    /**
     * 在 DuckDuckGo 上搜索，返回标题、URL 和摘要。
     */
    @Tool(description = """
                在网络上搜索信息。
                参数：query（搜索关键词），count（可选，返回结果数量，默认5，最大10）
                返回：搜索结果列表，每条包含序号、title、url、snippet
                """)
    public String webSearchPages(
            @ToolParam(description = "搜索关键词") String query,
            @ToolParam(description = "返回数量，默认5", required = false) Integer count) {
        final var n = count != null ? Math.min(count, 10) : 5;
        final var results = webSearchService.search(query, n);
        if (results.isEmpty()) {
            return "未找到搜索结果，请尝试调整搜索关键词。";
        }
        final var sb = new StringBuilder();
        sb.append("搜索结果（共").append(results.size()).append("条）：\n\n");
        for (int i = 0; i < results.size(); i++) {
            final var r = results.get(i);
            sb.append(i + 1).append(". ").append(r.title()).append("\n");
            sb.append("   URL: ").append(r.url()).append("\n");
            sb.append("   摘要: ").append(r.snippet()).append("\n\n");
        }
        return sb.toString();
    }

    /**
     * 抓取指定 URL 的页面内容，返回纯文本。
     */
    @Tool(description = """
                抓取指定URL的网页内容，提取纯文本。
                参数：url（要抓取的网页地址）
                返回：提取的纯文本内容（最多8000字符）
                """)
    public String webFetch(
            @ToolParam(description = "要抓取的网页URL，必须是完整的 http:// 或 https:// 地址") String url) {
        return webSearchService.fetchPage(url);
    }
}
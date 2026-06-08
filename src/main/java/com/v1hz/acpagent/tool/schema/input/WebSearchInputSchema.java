package com.v1hz.acpagent.tool.schema.input;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Web 搜索工具的入参。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class WebSearchInputSchema {

    @ToolParam(description = """
            搜索内容描述，用自然语言描述你想要搜索什么信息。
            例如："Spring AI 最新版本的新特性"、"Java 21 虚拟线程最佳实践"
            """)
    private String query;
}

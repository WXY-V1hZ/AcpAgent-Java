package com.v1hz.acpagent.tool.schema.input;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.tool.annotation.ToolParam;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WriteInputSchema {

    @ToolParam(description = "文件的绝对路径（必须为绝对路径）")
    private String filePath;

    @ToolParam(description = "要写入的内容")
    private String content;
}

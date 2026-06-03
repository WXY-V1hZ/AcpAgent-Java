package com.v1hz.acpagent.tool.schema.input;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.lang.Nullable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReadInputSchema {

    @ToolParam(description = "要读取的文件的绝对路径")
    private String filePath;

    @Nullable
    @ToolParam(description = "起始行号（1-indexed），默认1", required = false)
    private Integer offset;

    @Nullable
    @ToolParam(description = "最大读取行数，默认2000", required = false)
    private Integer limit;
}

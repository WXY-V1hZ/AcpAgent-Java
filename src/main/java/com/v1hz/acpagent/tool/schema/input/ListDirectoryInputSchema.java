package com.v1hz.acpagent.tool.schema.input;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.tool.annotation.ToolParam;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ListDirectoryInputSchema {

    @ToolParam(description = "目录的绝对路径")
    private String path;
}

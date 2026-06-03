package com.v1hz.acpagent.tool.schema.input;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.lang.Nullable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EditInputSchema {

    @ToolParam(description = "要修改的文件的绝对路径")
    private String filePath;

    @ToolParam(description = "要替换的文本（需在文件中唯一出现，除非 replaceAll=true）")
    private String oldContent;

    @ToolParam(description = "替换后的新文本（必须与 oldString 不同）")
    private String newContent;

    @Nullable
    @ToolParam(description = "是否替换所有匹配项（默认 false）", required = false)
    private Boolean replaceAll;
}

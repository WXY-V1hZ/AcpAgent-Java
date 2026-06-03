package com.v1hz.bizagent.tool.schema.input;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.tool.annotation.ToolParam;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BashInputSchema {

    @ToolParam(description = """
            需要执行的命令，路径会自动设置为 {{cwd}}
            注意：必须确保命令对用户是安全的
            """)
    private String command;

    @ToolParam(description = """
            超时时间（秒），默认为300，最大为600
            如果传入的参数超过600则会截断到600
            """
    )
    private int timeoutSeconds;
}

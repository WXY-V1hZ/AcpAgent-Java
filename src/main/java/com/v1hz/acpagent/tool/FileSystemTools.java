package com.v1hz.acpagent.tool;

import com.v1hz.acpagent.tool.schema.input.EditInputSchema;
import com.v1hz.acpagent.tool.schema.input.ListDirectoryInputSchema;
import com.v1hz.acpagent.tool.schema.input.ReadInputSchema;
import com.v1hz.acpagent.tool.schema.input.WriteInputSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Component
public class FileSystemTools implements BaseTool {

    // 默认的读取行数限制
    private static final int DEFAULT_READ_LIMIT = 1000;
    // 限制输出的最大字符数
    private static final int MAX_OUTPUT_LENGTH = 50_000;
    // 文件锁，一个路径对应一把锁
    private static final Map<String, ReentrantLock> fileLocks = new ConcurrentHashMap<>();

    @Override
    public List<String> getAllowedToolNames() {
        return List.of(
                "listDirectory"
        );
    }

    @Tool(description = """
            读取文件的内容。
            - 返回带行号的文本内容，支持 offset（起始行，1-indexed，默认1）和 limit（最大行数，默认1000）
            - 二进制文件会提示不可读
            """)
    public String readFile(@ToolParam ReadInputSchema readInputSchema) {
        int offset = readInputSchema.getOffset() == null ? 1 : readInputSchema.getOffset();
        int limit = readInputSchema.getLimit() == null ? DEFAULT_READ_LIMIT : readInputSchema.getLimit();
        try {
            return readFile(readInputSchema.getFilePath(), offset, limit);
        } catch (IOException e) {
            log.error("Error reading file: {}", readInputSchema.getFilePath(), e);
            return "Error reading file: " + e.getMessage();
        } catch (Exception e) {
            log.error("Unexpected error reading file: {}", readInputSchema.getFilePath(), e);
            return "Unexpected error: " + e.getMessage();
        }
    }

    @Tool(description = """
            将内容写入文件（覆盖写入）。
            - 文件路径必须是绝对路径
            - 父目录不存在时会自动创建
            - 返回写入结果摘要
            """)
    public String writeFile(@ToolParam WriteInputSchema writeInputSchema) {
        try {
            return writeFile(writeInputSchema.getFilePath(), writeInputSchema.getContent());
        } catch (IOException e) {
            log.error("Error writing file: {}", writeInputSchema.getFilePath(), e);
            return "Error writing file: " + e.getMessage();
        } catch (Exception e) {
            log.error("Unexpected error writing file: {}", writeInputSchema.getFilePath(), e);
            return "Unexpected error: " + e.getMessage();
        }
    }

    @Tool(description = """
            列出目录的内容。
            - 目录以 / 结尾，方便区分
            - 按名称字母序排序
            """)
    public String listDirectory(@ToolParam ListDirectoryInputSchema listDirectoryInputSchema) {
        try {
            return listDirectory(listDirectoryInputSchema.getPath());
        } catch (IOException e) {
            log.error("Error listing directory: {}", listDirectoryInputSchema.getPath(), e);
            return "Error listing directory: " + e.getMessage();
        } catch (Exception e) {
            log.error("Unexpected error listing directory: {}", listDirectoryInputSchema.getPath(), e);
            return "Unexpected error: " + e.getMessage();
        }
    }

    @Tool(description = """
            在文件中执行精确的字符串替换（查找并替换）。
            - 当replaceAll为false时，所以你必须提供足够的上下文来确保oldString在文件中是唯一的
            - newString 必须与 oldString 不同
            """)
    public String editFile(@ToolParam EditInputSchema editInputSchema) {
        boolean replaceAll = editInputSchema.getReplaceAll() != null && editInputSchema.getReplaceAll();
        try {
            return editFile(editInputSchema.getFilePath(), editInputSchema.getOldContent(), editInputSchema.getNewContent(), replaceAll);
        } catch (IOException e) {
            log.error("Error editing file: {}", editInputSchema.getFilePath(), e);
            return "Error editing file: " + e.getMessage();
        } catch (Exception e) {
            log.error("Unexpected error editing file: {}", editInputSchema.getFilePath(), e);
            return "Unexpected error: " + e.getMessage();
        }
    }

    private String readFile(String filePath, int offset, int limit) throws IOException {
        Path path = Paths.get(filePath).toRealPath();

        // 检查是否是二进制
        if (isBinary(path)) {
            return "Binary file (cannot read as text): " + path + "\nSize: " + Files.size(path) + " bytes";
        }
        // 检查是否是目录而不是文件
        if (Files.isDirectory(path)) {
            return "Cannot read directory as file: " + path;
        }

        // 读取所有行，计算出起止行号
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        int maxLines = limit < 1 ? DEFAULT_READ_LIMIT : limit;
        int totalLines = lines.size();
        int from = Math.max(offset, 1);
        int to = Math.min(from + maxLines - 1, totalLines);
        if (from > totalLines) {
            return String.format("Start line %d exceeds file length (%d lines).", from, totalLines);
        }

        // 构建输出
        StringBuilder total = new StringBuilder();
        // header
        total.append(String.format("File: %s (%d lines total), showing lines %d-%d:\n\n",
                path, totalLines, from, to));
        // body
        StringBuilder body = new StringBuilder();
        for (int i = from; i <= to; i++) {
            String line = lines.get(i - 1);
            body.append(String.format("%d|%s\n", i, line));
        }
        // footer
        String footer = (to < totalLines
                ? String.format("\n... (%d more lines)", totalLines - to)
                : "\n(End of file)");
        return total.append(clip(body.toString())).append(footer).toString();
    }

    private String listDirectory(String path) throws IOException {
        Path dir = Paths.get(path).toRealPath();

        if (!Files.isDirectory(dir)) {
            return path + " is not a directory";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Directory: %s\n\n", dir));

        try (var stream = Files.list(dir)) {
            List<String> items = stream
                    .map(p -> {
                        String name = p.getFileName().toString();
                        return Files.isDirectory(p) ? name + "/" : name;
                    })
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();

            if (items.isEmpty()) {
                sb.append("(empty directory)");
            } else {
                for (String item : items) {
                    sb.append(item).append("\n");
                }
                sb.append(String.format("\n%d item(s)", items.size()));
            }
        }
        return sb.toString();
    }

    private String writeFile(String filePath, String content) throws IOException {
        Path path = Paths.get(filePath);
        if (!path.isAbsolute()) {
            return "Error: filePath must be an absolute path, got: " + filePath;
        }
        boolean existed = Files.exists(path);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
        String status = existed ? "Updated" : "Created";
        return String.format("%s file: %s (%d bytes, %d lines)",
                status, path, Files.size(path), content.lines().count());
    }

    private String editFile(String filePath, String oldString, String newString, boolean replaceAll) throws IOException {
        Path path = Paths.get(filePath);
        if (!path.isAbsolute()) {
            return "Error: filePath must be an absolute path, got: " + filePath;
        }
        if (!Files.exists(path)) {
            return "File not found: " + filePath;
        }
        if (oldString.equals(newString)) {
            return "Error: oldString and newString are identical — no changes to apply.";
        }
        ReentrantLock lock = fileLocks.computeIfAbsent(path.toRealPath().toString(), k -> new ReentrantLock());
        lock.lock();
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            String originalContent = content;
            int count = countOccurrences(content, oldString);
            if (count == 0) {
                return String.format(
                        "String not found: '%s' does not appear in %s",
                        oldString, path.getFileName()
                );
            }
            if (count > 1) {
                return String.format("""
                        Ambiguous match: '%s' appears %d times in %s.
                        Provide more context to make it unique, or set replaceAll=true to replace all occurrences.
                        """, oldString, count, path.getFileName()
                );
            }
            content = content.replace(oldString, newString);
            if (content.equals(originalContent)) {
                return "No changes applied.";
            }
            Files.writeString(path, content, StandardCharsets.UTF_8);
            return String.format("Edited file: %s", path);
        } finally {
            lock.unlock();
            fileLocks.remove(path.toRealPath().toString(), lock);
        }
    }

    private boolean isBinary(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        if (bytes.length == 0) return false;
        int sample = Math.min(bytes.length, 4096);
        for (int i = 0; i < sample; i++) {
            if (bytes[i] == 0) return true;
        }
        return false;
    }

    private String clip(String text) {
        if (text.length() <= MAX_OUTPUT_LENGTH) return text;
        return "... [content truncated, showing last " + MAX_OUTPUT_LENGTH + " characters]\n"
                + text.substring(text.length() - MAX_OUTPUT_LENGTH);
    }

    private int countOccurrences(String text, String pattern) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(pattern, idx)) != -1) {
            count++;
            idx += pattern.length();
        }
        return count;
    }
}

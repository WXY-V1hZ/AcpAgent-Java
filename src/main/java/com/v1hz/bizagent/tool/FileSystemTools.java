package com.v1hz.bizagent.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Component
public class FileSystemTools {

    @Tool(description = "Write content to a file at a specified line number. " +
            "Line number is 1-based. If lineNumber is 0 or omitted, content is appended to the end. " +
            "If lineNumber exceeds the file length, the file is padded with empty lines. " +
            "If the file does not exist, it will be created.")
    public String writeFile(String path, String content, int lineNumber) {
        try {
            Path filePath = Paths.get(path);
            boolean isNew = !Files.exists(filePath);

            if (isNew) {
                Files.createDirectories(filePath.getParent());
            }

            List<String> lines = isNew ? new ArrayList<>() : new ArrayList<>(Files.readAllLines(filePath, StandardCharsets.UTF_8));

            if (lineNumber <= 0) {
                // append to end
                lines.add(content);
            } else {
                // ensure the list has enough lines
                while (lines.size() < lineNumber - 1) {
                    lines.add("");
                }
                if (lines.size() >= lineNumber) {
                    // replace existing line
                    lines.set(lineNumber - 1, content);
                } else {
                    // add at new line
                    lines.add(content);
                }
            }

            Files.write(filePath, lines, StandardCharsets.UTF_8);
            return (isNew ? "Created" : "Updated") + " file: " + path + " at line " + (lineNumber <= 0 ? "end" : lineNumber);
        } catch (Exception e) {
            return "Error writing file: " + e.getMessage();
        }
    }

    @Tool(description = "Search for a keyword in a file and return matching lines with their line numbers. " +
            "The search is case-insensitive. Returns 'No matches found' if the keyword is not present.")
    public String searchFile(String path, String keyword) {
        try {
            Path filePath = Paths.get(path);
            if (!Files.exists(filePath)) {
                return "File not found: " + path;
            }

            List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
            StringBuilder result = new StringBuilder();
            String lowerKeyword = keyword.toLowerCase();

            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).toLowerCase().contains(lowerKeyword)) {
                    result.append("Line ").append(i + 1).append(": ").append(lines.get(i)).append("\n");
                }
            }

            return result.isEmpty() ? "No matches found for: " + keyword : result.toString().trim();
        } catch (Exception e) {
            return "Error searching file: " + e.getMessage();
        }
    }

    @Tool(description = "Read a file and return its content with line numbers. " +
            "Supports optional startLine and endLine (1-based, inclusive) to read a specific range. " +
            "If endLine is 0 or omitted, reads to the end of the file. " +
            "For large files, it's recommended to specify a range to avoid excessive output.")
    public String readFile(String path, int startLine, int endLine) {
        try {
            Path filePath = Paths.get(path);
            if (!Files.exists(filePath)) {
                return "File not found: " + path;
            }

            List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);

            int from = Math.max(1, startLine);
            int to = (endLine <= 0 || endLine > lines.size()) ? lines.size() : endLine;

            if (from > lines.size()) {
                return "Start line " + startLine + " exceeds file length (" + lines.size() + " lines)";
            }

            StringBuilder result = new StringBuilder();
            result.append("File: ").append(path).append(" (").append(lines.size()).append(" lines total)\n\n");
            for (int i = from; i <= to; i++) {
                result.append(String.format("%6d", i)).append(" | ").append(lines.get(i - 1)).append("\n");
            }
            if (to - from + 1 < lines.size()) {
                result.append("\n... (").append(lines.size() - (to - from + 1)).append(" more lines)");
            }

            return result.toString().trim();
        } catch (Exception e) {
            return "Error reading file: " + e.getMessage();
        }
    }
}

package com.v1hz.acpagent.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 网络搜索与页面抓取的底层服务。
 *
 * <h3>设计要点</h3>
 * <ul>
 *   <li><b>零配置</b> — 使用 DuckDuckGo HTML 搜索，无需 API Key</li>
 *   <li><b>HTML 解析</b> — 正则匹配 DuckDuckGo 结果页的链接与摘要</li>
 *   <li><b>页面抓取</b> — Java HttpClient + HTML 标签剥离，结果截断在 {@value #MAX_FETCH_LENGTH} 字符内</li>
 *   <li><b>超时兜底</b> — 所有 HTTP 请求均有超时限制</li>
 * </ul>
 */
@Slf4j
@Component
public class WebSearchService {

    private static final String DDG_HTML_URL = "https://html.duckduckgo.com/html/";
    private static final int DEFAULT_SEARCH_COUNT = 6;
    private static final int MAX_FETCH_LENGTH = 8_000;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * 搜索结果条目。
     */
    public record SearchResult(String title, String url, String snippet) {
    }

    /**
     * 调用 DuckDuckGo HTML 搜索，返回搜索结果列表。
     *
     * @param query 搜索关键词
     * @param count 期望返回的结果数量（1-10，超出自动截断）
     * @return 搜索结果列表，失败时返回空列表
     */
    public List<SearchResult> search(String query, int count) {
        final var results = new ArrayList<SearchResult>();
        try {
            final var encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            final var url = DDG_HTML_URL + "?q=" + encoded;
            final var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (compatible; AcpAgent/1.0)")
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();

            final var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                log.warn("DuckDuckGo search returned HTTP {} for query: {}", response.statusCode(), query);
                return results;
            }

            results.addAll(parseDdgHtml(response.body(), Math.min(count, 10)));
        } catch (Exception e) {
            log.warn("DuckDuckGo search failed for query: {}", query, e);
        }
        return results;
    }

    /**
     * 默认数量（{@value #DEFAULT_SEARCH_COUNT} 条）搜索。
     */
    public List<SearchResult> search(String query) {
        return search(query, DEFAULT_SEARCH_COUNT);
    }

    /**
     * 抓取指定 URL 的页面内容，移除 HTML 标签后返回纯文本。
     *
     * @param url 目标 URL
     * @return 提取的纯文本内容（最多 {@value #MAX_FETCH_LENGTH} 字符），或错误信息
     */
    public String fetchPage(String url) {
        try {
            final var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (compatible; AcpAgent/1.0)")
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();

            final var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                return "Error: HTTP " + response.statusCode() + " fetching " + url;
            }

            final var contentType = response.headers().firstValue("Content-Type").orElse("");
            if (contentType.contains("application/pdf") || contentType.contains("application/zip")) {
                return "Error: Cannot fetch binary content (" + contentType + ") from " + url;
            }

            final var text = stripHtml(response.body());
            if (text.isBlank()) {
                return "Error: No readable text content found at " + url;
            }
            if (text.length() > MAX_FETCH_LENGTH) {
                return text.substring(0, MAX_FETCH_LENGTH) + "\n... [内容已截断，共 " + text.length() + " 字符]";
            }
            return text;
        } catch (Exception e) {
            log.warn("Page fetch failed for URL: {}", url, e);
            return "Error fetching page: " + e.getMessage();
        }
    }

    /**
     * 解析 DuckDuckGo HTML 搜索结果页。
     * <p>
     * 目标 HTML 结构：
     * <pre>{@code
     * <a rel="nofollow" class="result__a" href="URL">Title</a>
     * ... <a class="result__snippet">Snippet text</a>
     * }</pre>
     */
    private List<SearchResult> parseDdgHtml(String html, int count) {
        final var results = new ArrayList<SearchResult>();

        // 匹配链接和标题：class="result__a" 的 <a> 标签
        final var linkPattern = Pattern.compile(
                "<a[^>]*class=\"[^\"]*result__a[^\"]*\"[^>]*href=\"(https?://[^\"]+)\"[^>]*>([^<]*(?:<[^/][^>]*>[^<]*</[^>]*>)*[^<]*)</a>",
                Pattern.CASE_INSENSITIVE);

        // 匹配摘要：class="result__snippet" 的元素
        final var snippetPattern = Pattern.compile(
                "<[a-z]+[^>]*class=\"[^\"]*result__snippet[^\"]*\"[^>]*>([^<]*(?:<[^>]+>[^<]*</[^>]*>)*[^<]*)</[a-z]+>",
                Pattern.CASE_INSENSITIVE);

        final var linkMatcher = linkPattern.matcher(html);
        final var snippetMatcher = snippetPattern.matcher(html);

        // 每个搜索结果包含一条链接标题和一个摘要，交替提取
        while (linkMatcher.find() && results.size() < count) {
            final var rawUrl = linkMatcher.group(1);
            final var title = stripHtml(linkMatcher.group(2)).trim();
            var snippet = "";

            if (snippetMatcher.find(linkMatcher.end())) {
                snippet = stripHtml(snippetMatcher.group(1)).trim();
            }

            // 过滤掉非网页链接（如图片、资源文件）
            if (!title.isEmpty() && !rawUrl.contains("duckduckgo.com")) {
                results.add(new SearchResult(title, rawUrl, snippet));
            }
        }

        return results;
    }

    /**
     * 移除 HTML 标签与常见实体，返回纯文本。
     */
    private String stripHtml(String html) {
        if (html == null || html.isEmpty()) return "";
        return html.replaceAll("<style[^>]*>[^<]*</style>", " ")
                .replaceAll("<script[^>]*>[^<]*</script>", " ")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"")
                .replaceAll("&#x27;", "'")
                .replaceAll("&nbsp;", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }
}

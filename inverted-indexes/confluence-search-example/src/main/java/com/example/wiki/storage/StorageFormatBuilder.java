package com.example.wiki.storage;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Builds Confluence-style XHTML storage format from structured content.
 */
@Component
public class StorageFormatBuilder {

    /**
     * Wraps plain text in paragraph tags.
     */
    public String fromPlainText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        String[] paragraphs = text.split("\n\n+");
        for (String para : paragraphs) {
            if (!para.isBlank()) {
                sb.append("<p>").append(escapeXml(para.trim())).append("</p>\n");
            }
        }
        return sb.toString();
    }

    /**
     * Creates a heading element.
     */
    public String heading(int level, String text) {
        if (level < 1) level = 1;
        if (level > 6) level = 6;
        return String.format("<h%d>%s</h%d>\n", level, escapeXml(text), level);
    }

    /**
     * Creates a paragraph element.
     */
    public String paragraph(String text) {
        return String.format("<p>%s</p>\n", escapeXml(text));
    }

    /**
     * Creates formatted text within a paragraph.
     */
    public String bold(String text) {
        return String.format("<strong>%s</strong>", escapeXml(text));
    }

    public String italic(String text) {
        return String.format("<em>%s</em>", escapeXml(text));
    }

    public String code(String text) {
        return String.format("<code>%s</code>", escapeXml(text));
    }

    /**
     * Creates an unordered list.
     */
    public String unorderedList(List<String> items) {
        StringBuilder sb = new StringBuilder("<ul>\n");
        for (String item : items) {
            sb.append("  <li>").append(escapeXml(item)).append("</li>\n");
        }
        sb.append("</ul>\n");
        return sb.toString();
    }

    /**
     * Creates an ordered list.
     */
    public String orderedList(List<String> items) {
        StringBuilder sb = new StringBuilder("<ol>\n");
        for (String item : items) {
            sb.append("  <li>").append(escapeXml(item)).append("</li>\n");
        }
        sb.append("</ol>\n");
        return sb.toString();
    }

    /**
     * Creates a table.
     */
    public String table(List<String> headers, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("<table class=\"confluenceTable\">\n<tbody>\n");

        // Header row
        if (headers != null && !headers.isEmpty()) {
            sb.append("<tr>\n");
            for (String header : headers) {
                sb.append("  <th class=\"confluenceTh\">").append(escapeXml(header)).append("</th>\n");
            }
            sb.append("</tr>\n");
        }

        // Data rows
        for (List<String> row : rows) {
            sb.append("<tr>\n");
            for (String cell : row) {
                sb.append("  <td class=\"confluenceTd\">").append(escapeXml(cell)).append("</td>\n");
            }
            sb.append("</tr>\n");
        }

        sb.append("</tbody>\n</table>\n");
        return sb.toString();
    }

    /**
     * Creates a code block macro.
     */
    public String codeBlock(String language, String code) {
        return codeBlock(language, null, code);
    }

    public String codeBlock(String language, String title, String code) {
        StringBuilder sb = new StringBuilder();
        sb.append("<ac:structured-macro ac:name=\"code\" ac:schema-version=\"1\">\n");
        if (language != null && !language.isBlank()) {
            sb.append("  <ac:parameter ac:name=\"language\">").append(escapeXml(language)).append("</ac:parameter>\n");
        }
        if (title != null && !title.isBlank()) {
            sb.append("  <ac:parameter ac:name=\"title\">").append(escapeXml(title)).append("</ac:parameter>\n");
        }
        sb.append("  <ac:plain-text-body><![CDATA[").append(code).append("]]></ac:plain-text-body>\n");
        sb.append("</ac:structured-macro>\n");
        return sb.toString();
    }

    /**
     * Creates an info panel macro.
     */
    public String infoPanel(String content) {
        return panel("info", null, content);
    }

    /**
     * Creates a warning panel macro.
     */
    public String warningPanel(String title, String content) {
        return panel("warning", title, content);
    }

    /**
     * Creates a note panel macro.
     */
    public String notePanel(String content) {
        return panel("note", null, content);
    }

    private String panel(String type, String title, String content) {
        StringBuilder sb = new StringBuilder();
        sb.append("<ac:structured-macro ac:name=\"").append(type).append("\" ac:schema-version=\"1\">\n");
        if (title != null && !title.isBlank()) {
            sb.append("  <ac:parameter ac:name=\"title\">").append(escapeXml(title)).append("</ac:parameter>\n");
        }
        sb.append("  <ac:rich-text-body>\n    <p>").append(escapeXml(content)).append("</p>\n  </ac:rich-text-body>\n");
        sb.append("</ac:structured-macro>\n");
        return sb.toString();
    }

    /**
     * Creates an internal page link.
     */
    public String pageLink(String spaceKey, String pageTitle, String linkText) {
        StringBuilder sb = new StringBuilder();
        sb.append("<ac:link>\n");
        sb.append("  <ri:page");
        if (spaceKey != null && !spaceKey.isBlank()) {
            sb.append(" ri:space-key=\"").append(escapeXml(spaceKey)).append("\"");
        }
        sb.append(" ri:content-title=\"").append(escapeXml(pageTitle)).append("\"/>\n");
        if (linkText != null && !linkText.isBlank()) {
            sb.append("  <ac:link-body>").append(escapeXml(linkText)).append("</ac:link-body>\n");
        }
        sb.append("</ac:link>");
        return sb.toString();
    }

    /**
     * Creates an external link.
     */
    public String externalLink(String url, String text) {
        return String.format("<a href=\"%s\">%s</a>", escapeXml(url), escapeXml(text));
    }

    /**
     * Creates a table of contents macro.
     */
    public String tableOfContents() {
        return tableOfContents(1, 3);
    }

    public String tableOfContents(int minLevel, int maxLevel) {
        return String.format("""
            <ac:structured-macro ac:name="toc" ac:schema-version="1">
              <ac:parameter ac:name="minLevel">%d</ac:parameter>
              <ac:parameter ac:name="maxLevel">%d</ac:parameter>
            </ac:structured-macro>
            """, minLevel, maxLevel);
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }
}

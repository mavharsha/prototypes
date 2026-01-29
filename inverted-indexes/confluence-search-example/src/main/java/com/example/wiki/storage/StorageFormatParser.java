package com.example.wiki.storage;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses Confluence-style XHTML storage format and extracts searchable text.
 *
 * The storage format includes:
 * - Standard HTML elements (p, h1-h6, ul, ol, li, table, etc.)
 * - Confluence-specific elements (ac:structured-macro, ac:link, ri:page, etc.)
 * - CDATA sections for code blocks
 */
@Component
public class StorageFormatParser {

    /**
     * Extracts plain text from XHTML storage format for indexing.
     * Removes all markup while preserving meaningful text content.
     */
    public String extractText(String xhtml) {
        if (xhtml == null || xhtml.isBlank()) {
            return "";
        }

        Document doc = Jsoup.parse(xhtml);

        // Remove script and style elements
        doc.select("script, style").remove();

        // Process code blocks - extract code content
        for (Element macro : doc.select("ac\\:structured-macro[ac\\:name=code]")) {
            Element codeBody = macro.selectFirst("ac\\:plain-text-body");
            if (codeBody != null) {
                macro.replaceWith(new Element("p").text(codeBody.text()));
            }
        }

        // Process other macros - extract rich text body
        for (Element macro : doc.select("ac\\:structured-macro")) {
            Element richBody = macro.selectFirst("ac\\:rich-text-body");
            if (richBody != null) {
                macro.replaceWith(new Element("div").html(richBody.html()));
            } else {
                // Remove macros without extractable content
                macro.remove();
            }
        }

        // Process links - extract link body text
        for (Element link : doc.select("ac\\:link")) {
            Element linkBody = link.selectFirst("ac\\:link-body");
            if (linkBody != null) {
                link.replaceWith(new Element("span").text(linkBody.text()));
            } else {
                // Try to get page title from ri:page
                Element page = link.selectFirst("ri\\:page");
                if (page != null) {
                    String title = page.attr("ri:content-title");
                    if (!title.isBlank()) {
                        link.replaceWith(new Element("span").text(title));
                    }
                }
            }
        }

        // Extract text and normalize whitespace
        String text = doc.text();
        return normalizeWhitespace(text);
    }

    /**
     * Extracts headings from the content for structure analysis.
     */
    public List<Heading> extractHeadings(String xhtml) {
        List<Heading> headings = new ArrayList<>();
        if (xhtml == null || xhtml.isBlank()) {
            return headings;
        }

        Document doc = Jsoup.parse(xhtml);

        for (int level = 1; level <= 6; level++) {
            Elements elements = doc.select("h" + level);
            for (Element h : elements) {
                headings.add(new Heading(level, h.text()));
            }
        }

        return headings;
    }

    /**
     * Extracts internal page links for relationship tracking.
     */
    public List<PageLink> extractLinks(String xhtml) {
        List<PageLink> links = new ArrayList<>();
        if (xhtml == null || xhtml.isBlank()) {
            return links;
        }

        Document doc = Jsoup.parse(xhtml);

        for (Element link : doc.select("ac\\:link")) {
            Element page = link.selectFirst("ri\\:page");
            if (page != null) {
                String spaceKey = page.attr("ri:space-key");
                String contentTitle = page.attr("ri:content-title");
                if (!contentTitle.isBlank()) {
                    links.add(new PageLink(spaceKey, contentTitle));
                }
            }
        }

        return links;
    }

    /**
     * Extracts a plain text excerpt for search result display.
     */
    public String extractExcerpt(String xhtml, int maxLength) {
        String text = extractText(xhtml);
        if (text.length() <= maxLength) {
            return text;
        }
        // Find a good break point
        int breakPoint = text.lastIndexOf(' ', maxLength);
        if (breakPoint < maxLength / 2) {
            breakPoint = maxLength;
        }
        return text.substring(0, breakPoint) + "...";
    }

    private String normalizeWhitespace(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

    // Inner classes for extracted data

    public record Heading(int level, String text) {}

    public record PageLink(String spaceKey, String contentTitle) {}
}

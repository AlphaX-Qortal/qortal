package org.qortal.api;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

public class HTMLParser {

    private static final Logger LOGGER = LogManager.getLogger(HTMLParser.class);

    private String linkPrefix;
    private byte[] data;
    private String qdnContext;

    public HTMLParser(String resourceId, String inPath, String prefix, boolean usePrefix, byte[] data, String qdnContext) {
        String inPathWithoutFilename = inPath.substring(0, inPath.lastIndexOf('/'));
        this.linkPrefix = usePrefix ? String.format("%s/%s%s", prefix, resourceId, inPathWithoutFilename) : "";
        this.data = data;
        this.qdnContext = qdnContext;
    }

    public void addAdditionalHeaderTags() {
        String fileContents = new String(data);
        Document document = Jsoup.parse(fileContents);
        String baseUrl = this.linkPrefix + "/";
        Elements head = document.getElementsByTag("head");
        if (!head.isEmpty()) {
            // Add q-apps script tag
            String qAppsScriptElement = String.format("<script src=\"/apps/q-apps.js?time=%d\">", System.currentTimeMillis());
            head.get(0).prepend(qAppsScriptElement);

            // Add QDN context var
            String qdnContextVar = String.format("<script>var qdnContext=\"%s\";</script>", this.qdnContext);
            head.get(0).prepend(qdnContextVar);

            // Add base href tag
            String baseElement = String.format("<base href=\"%s\">", baseUrl);
            head.get(0).prepend(baseElement);

            // Add meta charset tag
            String metaCharsetElement = "<meta charset=\"UTF-8\">";
            head.get(0).prepend(metaCharsetElement);

        }
        String html = document.html();
        this.data = html.getBytes();
    }

    public static boolean isHtmlFile(String path) {
        if (path.endsWith(".html") || path.endsWith(".htm")) {
            return true;
        }
        return false;
    }

    public byte[] getData() {
        return this.data;
    }
}

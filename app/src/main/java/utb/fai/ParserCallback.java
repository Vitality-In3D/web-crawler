package utb.fai;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Worker task that downloads and analyzes a single web page in its own thread.
 */
public class ParserCallback implements Runnable {

    private static final int TIMEOUT_MS = 10000;
    private static final String USER_AGENT = "Mozilla/5.0";

    private final URIinfo uriInfo;
    private final int maxDepth;
    private final int debugLevel;
    private final Set<String> visitedURIs;
    private final BlockingQueue<URIinfo> foundURIs;
    private final ConcurrentHashMap<String, Integer> wordCounts;
    private final AtomicInteger pendingPages;

    public ParserCallback(
            URIinfo uriInfo,
            int maxDepth,
            int debugLevel,
            Set<String> visitedURIs,
            BlockingQueue<URIinfo> foundURIs,
            ConcurrentHashMap<String, Integer> wordCounts,
            AtomicInteger pendingPages) {
        this.uriInfo = uriInfo;
        this.maxDepth = maxDepth;
        this.debugLevel = debugLevel;
        this.visitedURIs = visitedURIs;
        this.foundURIs = foundURIs;
        this.wordCounts = wordCounts;
        this.pendingPages = pendingPages;
    }

    @Override
    public void run() {
        try {
            if (uriInfo == null || uriInfo.uri == null) {
                return;
            }

            String pageUrl = uriInfo.uri.toString();
            if (debugLevel > 0) {
                System.err.println("Analyzing (depth " + uriInfo.depth + "): " + pageUrl);
            }

            // Jsoup automatically detects charset from HTTP Content-Type header or HTML <meta> tags
            Document doc = Jsoup.connect(pageUrl)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .followRedirects(true)
                    .get();

            // Extract visible body text and count words
            Element body = doc.body();
            if (body != null) {
                String text = body.text();
                if (!text.isEmpty()) {
                    String[] words = text.split("\\s+");
                    for (String word : words) {
                        if (!word.isEmpty()) {
                            wordCounts.merge(word, 1, Integer::sum);
                        }
                    }
                }
            }

            // Discover child links only if maxDepth has not been reached yet
            if (uriInfo.depth < maxDepth) {
                extractLinks(doc);
            }
        } catch (IOException e) {
            if (debugLevel > 0) {
                System.err.println("Failed to fetch page " + uriInfo.uri + ": " + e.getMessage());
            }
        } catch (Exception e) {
            if (debugLevel > 0) {
                System.err.println("Unexpected error on page " + uriInfo.uri + ": " + e.getMessage());
            }
        } finally {
            pendingPages.decrementAndGet();
        }
    }

    private void extractLinks(Document doc) {
        Elements links = doc.select("a[href], frame[src], iframe[src]");
        for (Element link : links) {
            String absUrl = link.hasAttr("href") ? link.absUrl("href") : link.absUrl("src");
            int fragmentIndex = absUrl.indexOf('#');
            if (fragmentIndex != -1) {
                absUrl = absUrl.substring(0, fragmentIndex);
            }
            absUrl = absUrl.trim();
            if (absUrl.isEmpty()) {
                continue;
            }

            try {
                URI nextUri = new URI(absUrl);
                if (nextUri.isOpaque()) {
                    continue;
                }
                String scheme = nextUri.getScheme();
                if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                    continue;
                }

                String normalizedUrl = nextUri.toString();
                if (visitedURIs.add(normalizedUrl)) {
                    pendingPages.incrementAndGet();
                    foundURIs.offer(new URIinfo(nextUri, uriInfo.depth + 1));
                    if (debugLevel > 1) {
                        System.err.println("Queued URI: " + normalizedUrl);
                    }
                }
            } catch (URISyntaxException e) {
                if (debugLevel > 1) {
                    System.err.println("Invalid URI skipped: " + absUrl);
                }
            }
        }
    }
}

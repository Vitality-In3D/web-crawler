package utb.fai;

import java.net.URI;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class App {

    private static final int THREAD_POOL_SIZE = 16;
    private static final int TOP_WORDS_LIMIT = 20;

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Missing parameter - start URL");
            return;
        }

        int maxDepth = 2;
        if (args.length >= 2) {
            try {
                maxDepth = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid maxDepth parameter, using default (2): " + args[1]);
            }
        }

        int debugLevel = 0;
        if (args.length >= 3) {
            try {
                debugLevel = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid debugLevel parameter, using default (0): " + args[2]);
            }
        }

        Set<String> visitedURIs = ConcurrentHashMap.newKeySet();
        ConcurrentHashMap<String, Integer> wordCounts = new ConcurrentHashMap<>();
        BlockingQueue<URIinfo> foundURIs = new LinkedBlockingQueue<>();
        AtomicInteger pendingPages = new AtomicInteger(0);

        try {
            String startUrl = args[0].trim();
            int fragmentIndex = startUrl.indexOf('#');
            if (fragmentIndex != -1) {
                startUrl = startUrl.substring(0, fragmentIndex);
            }

            URI startUri = new URI(startUrl);
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
            try {
                visitedURIs.add(startUri.toString());
                pendingPages.incrementAndGet();
                foundURIs.offer(new URIinfo(startUri, 0));

                // Dispatch discovered URLs to worker threads until all active tasks finish
                while (pendingPages.get() > 0 || !foundURIs.isEmpty()) {
                    URIinfo nextUriInfo = foundURIs.poll(50, TimeUnit.MILLISECONDS);
                    if (nextUriInfo != null) {
                        executor.execute(new ParserCallback(
                                nextUriInfo,
                                maxDepth,
                                debugLevel,
                                visitedURIs,
                                foundURIs,
                                wordCounts,
                                pendingPages));
                    }
                }

                executor.shutdown();
                executor.awaitTermination(1, TimeUnit.MINUTES);
            } finally {
                if (!executor.isShutdown()) {
                    executor.shutdownNow();
                }
            }

            // Print top 20 most frequent words in descending order to System.out
            wordCounts.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                    .limit(TOP_WORDS_LIMIT)
                    .forEach(entry -> System.out.println(entry.getKey() + ";" + entry.getValue()));

        } catch (Exception e) {
            System.err.println("Unhandled exception during crawling: " + e.getMessage());
            e.printStackTrace(System.err);
        }
    }
}

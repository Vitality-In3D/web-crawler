package utb.fai;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Holds a target URI together with its crawl depth.
 */
public class URIinfo {

    public URI uri;
    public int depth;

    public URIinfo(URI uri, int depth) {
        this.uri = uri;
        this.depth = depth;
    }

    public URIinfo(String str, int depth) {
        try {
            this.uri = new URI(str);
        } catch (URISyntaxException e) {
            System.err.println("Invalid URI syntax: " + str);
        }
        this.depth = depth;
    }
}

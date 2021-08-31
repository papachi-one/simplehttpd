# SimpleHTTPd
Simple single threaded HTTP Server processing incoming requests in the loop.

Usage:

```java
import one.papachi.simplehttpd.SimpleHTTPd;
import one.papachi.simplehttpd.SimpleHTTPd.HTTPRequest;
import one.papachi.simplehttpd.SimpleHTTPd.HTTPResponse;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.function.Function;

public class Main {
    public static void main(String[] args) {
        InetSocketAddress socketAddress = new InetSocketAddress(8080);
        Function<HTTPRequest, HTTPResponse> handler = httpRequest -> 
                new HTTPResponse("HTTP/1.1 200 OK",
                        Map.of("Content-Type", "text/plain"),
                        "Hello world!");
        SimpleHTTPd httpd = new SimpleHTTPd(socketAddress, handler);
        httpd.start();
    }
}
```
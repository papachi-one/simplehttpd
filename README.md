# SimpleHTTPd

Simple multi-threaded HTTP Server.

Usage:

```java
import one.papachi.simplehttpd.SimpleHTTPd;

import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Function;

public class Server {
    public static void main(String[] args) {
        InetSocketAddress socketAddress = new InetSocketAddress(8080);
        Function<HTTPRequest, HTTPResponse> handler = httpRequest ->
                new HTTPResponse("HTTP/1.1 200 OK",
                        Map.of("Content-Type", "text/plain"),
                        new ByteArrayInputStream("Hello world!".getBytes()));
        SimpleHTTPd httpd = new SimpleHTTPd(handler, socketAddress);
        new Thread(httpd).start();
    }
}
```
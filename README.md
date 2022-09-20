# SimpleHTTPd
Simple multi-threaded HTTP Server written in Java.
## Sample code
```java
import one.papachi.simplehttpd.SimpleHTTPd;

import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class Server {
    public static void main(String[] args) {
        InetSocketAddress socketAddress = new InetSocketAddress(8080);
        Function<HTTPRequest, HTTPResponse> handler = httpRequest ->
                new HTTPResponse(HTTPResponse.STATUS_200_OK,
                        Map.of("Content-Type", "text/plain"),
                        new ByteArrayInputStream("Hello world!".getBytes()));
        SimpleHTTPd httpd = new SimpleHTTPd(handler, socketAddress);
        new Thread(httpd).start();
    }
}
```

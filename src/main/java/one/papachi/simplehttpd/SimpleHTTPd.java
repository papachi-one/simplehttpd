package one.papachi.simplehttpd;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * Simple multi-threaded HTTP server.
 */
public class SimpleHTTPd implements Runnable {

    /**
     * Executor service which is used to process HTTP requests.
     */
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    /**
     * Function that takes HTTPRequest and returns HTTPResponse.
     */
    private final Function<HTTPRequest, HTTPResponse> handler;

    /**
     * Socket address to use in bind for ServerSocket .
     */
    private final InetSocketAddress socketAddress;

    /**
     * @param handler java.util.Function that takes HTTPRequest and returns HTTPResponse
     * @param socketAddress socket address to use in bind for ServerSocket
     */
    public SimpleHTTPd(Function<HTTPRequest, HTTPResponse> handler, InetSocketAddress socketAddress) {
        this.handler = handler;
        this.socketAddress = socketAddress;
    }

    /**
     * Main loop of the HTTP server.
     */
    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.bind(socketAddress);
            while (!serverSocket.isClosed()) {
                Socket client = serverSocket.accept();
                executorService.execute(() -> process(client));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * @param socket represents a connection to the HTTP client.
     */
    private void process(Socket socket) {
        try (Socket client = socket) {
            BufferedInputStream inputStream = new BufferedInputStream(client.getInputStream());
            BufferedOutputStream outputStream = new BufferedOutputStream(client.getOutputStream());
            String method, path, version;
            Map<String, String> queryParameters = new LinkedHashMap<>();
            Map<String, Set<String>> queryParametersMultiValue = new LinkedHashMap<>();
            Map<String, String> headers = new LinkedHashMap<>();
            InputStream body;
            List<String> lines = new ArrayList<>();
            String[] split;
            byte[] bytes;
            while ((bytes = readLine(inputStream)) != null) {
                lines.add(new String(bytes, StandardCharsets.ISO_8859_1));
            }
            split = lines.get(0).split("\\s", 3);
            method = split[0];
            path = split[1];
            version = split[2];
            split = path.split("\\?", 2);
            path = split[0];
            if (split.length == 2) {
                split = split[1].split("&");
                for (String s : split) {
                    String[] split1 = s.split("=");
                    String key = URLDecoder.decode(split1[0], StandardCharsets.UTF_8);
                    String value = URLDecoder.decode(split1[1], StandardCharsets.UTF_8);
                    queryParameters.putIfAbsent(key, value);
                    queryParametersMultiValue.compute(key, (k, v) -> {
                        v = v != null ? v : new LinkedHashSet<>();
                        v.add(value);
                        return v;
                    });
                }
            }
            lines.stream().skip(1L).forEach(string -> {
                String[] split1 = string.split(":", 2);
                headers.put(split1[0].trim(), split1[1].trim());
            });
            long contentLength = Long.parseLong(headers.getOrDefault("Content-Length", "0"));
            body = new InputStream() {
                long counter;
                @Override
                public int read() throws IOException {
                    if (counter++ == contentLength)
                        return -1;
                    return inputStream.read();
                }
            };
            HTTPRequest request = new HTTPRequest(method, path, version, queryParameters, queryParametersMultiValue, headers, body);
            HTTPResponse response;
            try {
                 response = handler.apply(request);
            } catch (Exception e) {
                response = HTTPResponse.exceptionResponse(e);
            }
            outputStream.write((response.statusLine + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
            outputStream.write("Connection: close\r\n".getBytes(StandardCharsets.ISO_8859_1));
            for (Map.Entry<String, String> entry : response.headers.entrySet())
                outputStream.write((entry.getKey() + ": " + entry.getValue() + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
            outputStream.write("\r\n".getBytes(StandardCharsets.ISO_8859_1));
            int i;
            while ((i = response.body.read()) != -1)
                outputStream.write(i);
            outputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * @param inputStream source from which reads the data
     * @return byte[] containing the line from HTTP request
     * @throws IOException if any input/output operation fails
     */
    private byte[] readLine(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        boolean cr = false, lf = false;
        int i;
        while (!(cr && lf) && (i = inputStream.read()) != -1) {
            if (i == '\r')
                cr = true;
            else if (i == '\n')
                lf = true;
            else
                outputStream.write(i);
        }
        return outputStream.size() > 0 ? outputStream.toByteArray() : null;
    }


    /**
     * Record holding parsed HTTP request.
     */
    public static record HTTPRequest(String method, String path, String version, Map<String, String> queryParameters,
                                     Map<String, Set<String>> queryParametersMultiValue, Map<String, String> headers,
                                     InputStream body) {
    }

    /**
     * Record holding HTTP response data to write to client's socket.
     */
    public static record HTTPResponse(String statusLine, Map<String, String> headers, InputStream body) {

        public static HTTPResponse exceptionResponse(Exception e) {
            StringWriter stringWriter = new StringWriter();
            PrintWriter printWriter = new PrintWriter(stringWriter);
            e.printStackTrace(printWriter);
            String string = stringWriter.toString();
            byte[] body = string.getBytes(StandardCharsets.UTF_8);
            ByteArrayInputStream inputStream = new ByteArrayInputStream(body);
            return new HTTPResponse("HTTP/1.1 500 Internal Server Error", Map.of("Content-Type", "text/plain"), inputStream);
        }

        public static HTTPResponse jsonResponse(String json) {
            return new HTTPResponse("HTTP/1.1 200 OK", Map.of("Content-Type", "application/json"), new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        }

        public static HTTPResponse htmlResponse(String html) {
            return new HTTPResponse("HTTP/1.1 200 OK", Map.of("Content-Type", "text/html; charset=UTF-8"), new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
        }

        public static HTTPResponse textResponse(String text) {
            return new HTTPResponse("HTTP/1.1 200 OK", Map.of("Content-Type", "text/plain; charset=UTF-8"), new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        }

        public static HTTPResponse cssResponse(String css) {
            return new HTTPResponse("HTTP/1.1 200 OK", Map.of("Content-Type", "text/css; charset=UTF-8"), new ByteArrayInputStream(css.getBytes(StandardCharsets.UTF_8)));
        }

        public static HTTPResponse jsResponse(String js) {
            return new HTTPResponse("HTTP/1.1 200 OK", Map.of("Content-Type", "text/javascript; charset=UTF-8"), new ByteArrayInputStream(js.getBytes(StandardCharsets.UTF_8)));
        }

    }

}

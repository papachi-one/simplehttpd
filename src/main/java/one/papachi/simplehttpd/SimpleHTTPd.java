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
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
     * Processes single HTTP request
     * @param socket represents a connection to the HTTP client.
     */
    private void process(Socket socket) {
        try (Socket client = socket) {
            BufferedInputStream inputStream = new BufferedInputStream(client.getInputStream());
            BufferedOutputStream outputStream = new BufferedOutputStream(client.getOutputStream());
            HTTPRequest request = readRequest(inputStream);
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
     * Reads and parses HTTP request
     * @param inputStream
     * @return HTTP Request object
     * @throws IOException
     */
    private HTTPRequest readRequest(InputStream inputStream) throws IOException {
        // request line + query params
        Map<String, String> paramsSingleValue = new LinkedHashMap<>();
        Map<String, Set<String>> paramsMultiValue = new LinkedHashMap<>();
        String[] split = new String(readLine(inputStream)).split("\\s", 3);
        String[] split1 = split[1].split("\\?", 2);
        String[] split2 = split1.length == 2 ? split1[1].split("&") : new String[0];
        for (String string : split2) {
            String[] split3 = string.split("=", 2);
            paramsSingleValue.putIfAbsent(split3[0], split3.length == 2 ? split3[1] : null);
            paramsMultiValue.compute(split3[0], (k, v) -> {
                v = v != null ? v : new LinkedHashSet<>();
                v.add(split3.length == 2 ? split3[1] : null);
                return v;
            });
        }
        HTTPRequestLine requestLine = new HTTPRequestLine(split[0], split1[0], split[2]);
        HTTPQueryParameters params = new HTTPQueryParameters(paramsSingleValue, paramsMultiValue);

        // headers
        Map<String, String> headersSingleValue = new LinkedHashMap<>();
        Map<String, Set<String>> headersMultiValue = new LinkedHashMap<>();
        String line;
        while ((line = readLine(inputStream)) != null) {
            String[] split4 = line.split(":\\s+", 2);
            headersSingleValue.putIfAbsent(split4[0], split4.length == 2 ? split4[1] : null);
            headersMultiValue.compute(split[0], (k, v) -> {
                v = v != null ? v : new LinkedHashSet<>();
                v.add(split4.length == 2 ? split4[1] : null);
                return v;
            });
        }
        HTTPHeaders headers = new HTTPHeaders(headersSingleValue, headersMultiValue);

        // body
        long contentLength = Long.parseLong(headersSingleValue.getOrDefault("Content-Length", "0"));
        InputStream bodyInputStream = new InputStream() {
            long counter;
            @Override
            public int read() throws IOException {
                if (counter++ == contentLength)
                    return -1;
                return inputStream.read();
            }
        };
        HTTPBody body = new HTTPBody(bodyInputStream);

        return new HTTPRequest(requestLine, headers, params, body);
    }

    /**
     * @param inputStream source from which reads the data
     * @return byte[] containing the line from HTTP request
     * @throws IOException if any input/output operation fails
     */
    private byte[] readLineBytes(InputStream inputStream) throws IOException {
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
     * @param inputStream source from which reads the data
     * @return String containing the line from HTTP request
     * @throws IOException if any input/output operation fails
     */
    private String readLine(InputStream inputStream) throws IOException {
        byte[] bytes = readLineBytes(inputStream);
        return bytes != null && bytes.length > 0 ? new String(bytes, StandardCharsets.ISO_8859_1) : null;
    }

    /**
     * Record for HTTP Request Line
     */
    public static record HTTPRequestLine(String method, String path, String version) {
    }

    /**
     * Record for HTTP headers (single/multi value)
     */
    public static record HTTPHeaders(Map<String, String> headers, Map<String, Set<String>> headersMultiValue) {
    }

    /**
     * Record for parsed query parameters as single and multi value maps
     */
    public static record HTTPQueryParameters(Map<String, String> params, Map<String, Set<String>> paramsMultiValue) {
    }

    /**
     * Record for holding inputStream representing HTTP Request Body
     */
    public static record HTTPBody(InputStream inputStream) {
    }

    /**
     * Record holding parsed HTTP request.
     */
    public static record HTTPRequest(HTTPRequestLine requestLine, HTTPHeaders headers, HTTPQueryParameters params, HTTPBody body) {
    }

    /**
     * Record holding HTTP response data to write to client's socket.
     */
    public static record HTTPResponse(String statusLine, Map<String, String> headers, InputStream body) {

        public static final String STATUS_200_OK = "HTTP/1.1 200 OK";

        public static final String STATUS_500_INTERNAL_SERVER_ERROR = "HTTP/1.1 500 Internal Server Error";

        public static final String CONTENT_TYPE_TEXT_PLAIN = "text/plain; charset=UTF-8";

        public static final String CONTENT_TYPE_TEXT_HTML = "text/html; charset=UTF-8";

        public static final String CONTENT_TYPE_TEXT_CSS = "text/css; charset=UTF-8";

        public static final String CONTENT_TYPE_TEXT_JAVASCRIPT = "text/javascript";

        public static final String CONTENT_TYPE_APPLICATION_JSON = "application/json";

        public static final String CONTENT_TYPE_APPLICATION_OCTET_STREAM = "application/octet-stream";

        public static HTTPResponse exceptionResponse(Exception e) {
            StringWriter stringWriter = new StringWriter();
            e.printStackTrace(new PrintWriter(stringWriter));
            ByteArrayInputStream inputStream = new ByteArrayInputStream(stringWriter.toString().getBytes(StandardCharsets.UTF_8));
            return new HTTPResponse(STATUS_500_INTERNAL_SERVER_ERROR, Map.of("Content-Type", CONTENT_TYPE_TEXT_PLAIN), inputStream);
        }

        public static HTTPResponse response(String contentType, String body) {
            return new HTTPResponse(STATUS_200_OK, Map.of("Content-Type", contentType), new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        }

    }

}

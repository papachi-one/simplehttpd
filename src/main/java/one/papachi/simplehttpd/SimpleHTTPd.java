package one.papachi.simplehttpd;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Single threaded HTTP Server to process incoming HTTPRequest using user defined function returning HTTPResponse.
 */
public class SimpleHTTPd implements Runnable {

    /**
     * Socket address that will use SimpleHTTPd to listen on.
     */
    protected final InetSocketAddress socketAddress;

    /**
     * User defined function that processes HTTPRequest and returns HTTPResponse
     */
    protected final Function<HTTPRequest, HTTPResponse> processor;

    /**
     * Flag to determine if SimpleHTTPd is listening for incoming HTTP Requests
     */
    protected final AtomicBoolean isRunning = new AtomicBoolean();

    /**
     * ServerSocket object
     */
    protected ServerSocket serverSocket;

    /**
     * @param socketAddress address to listen on for incoming HTTP Requests
     * @param processor function to process HTTPRequest returning HTTPResponse
     */
    public SimpleHTTPd(InetSocketAddress socketAddress, Function<HTTPRequest, HTTPResponse> processor) {
        this.socketAddress = socketAddress;
        this.processor = processor;
    }

    /**
     * Start accepting and processing incoming HTTP Requests using user defined function.
     */
    public void start() {
        if (!isRunning.compareAndSet(false, true))
            return;
        run();
    }

    /**
     * Stops processing incoming HTTP Requests.
     */
    public void stop() {
        if (!isRunning.compareAndSet(true, false))
            return;
        try {
            serverSocket.close();
        } catch (IOException e) {
        } finally {
            serverSocket = null;
        }
    }

    /**
     * @return true if SimpleHTTPd is running
     */
    public boolean isRunning() {
        return isRunning.get();
    }

    /**
     * Forever loop to process incoming HTTP Requests until SimpleHTTPd is stopped.
     */
    public void run() {
        try {
            serverSocket = new ServerSocket();
            serverSocket.bind(socketAddress);
        } catch (IOException e) {
            stop();
            return;
        }
        while (isRunning.get()) {
            try (Socket socket = serverSocket.accept()) {
                HTTPRequest request = process(socket);
                HTTPResponse response;
                try {
                    response = processor.apply(request);
                } catch (Exception e) {
                    StringWriter stringWriter = new StringWriter();
                    e.printStackTrace(new PrintWriter(stringWriter));
                    response = new HTTPResponse("HTTP/1.1 500 Internal Server Error", Map.of("Content-Type", "text/plain", "Connection", "close"), stringWriter.toString());
                }
                process(socket, response);
            } catch (IOException e) {
            }
        }
        stop();
    }

    /**
     * Reads HTTP headers and body from socket's inputStream.
     * @param socket from which we are reading data
     * @return SimpleHTTPd.HTTPRequest
     * @throws IOException if any IO operation fails
     */
    protected HTTPRequest process(Socket socket) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
        String string = reader.readLine();
        String[] split = string.split("\\s", 3);
        String method = split[0];
        String path = split[1];
        String version = split[2];
        split = path.split("\\?", 2);
        path = split[0];
        Map<String, String> params = new LinkedHashMap<>();
        Map<String, Set<String>> paramsMultiValue = new LinkedHashMap<>();
        if (split.length == 2) {
            split = split[1].split("&");
            for (String s : split) {
                String[] split1 = s.split("=");
                String key = URLDecoder.decode(split1[0], StandardCharsets.UTF_8);
                String value = URLDecoder.decode(split1[1], StandardCharsets.UTF_8);
                params.putIfAbsent(key, value);
                paramsMultiValue.compute(key, (k, v) -> {
                    v = v != null ? v : new LinkedHashSet<>();
                    v.add(value);
                    return  v;
                });
            }
        }
        Map<String, String> headers = new LinkedHashMap<>();
        while ((string = reader.readLine()) != null && !string.isEmpty()) {
            split = string.split(":", 2);
            headers.put(split[0].trim(), split[1].trim());
        }
        int contentLength = Integer.parseInt(headers.getOrDefault("Content-Length", "0"));
        byte[] array = new byte[contentLength];
        for (int i = 0, c; (i < contentLength && (c = reader.read()) != -1); i++)
            array[i] = (byte) c;
        String body = new String(array, StandardCharsets.UTF_8);
        return new HTTPRequest(method, path, version, headers, params, paramsMultiValue, body);
    }

    /**
     * Writes HTTP response headers and body to given socket based in response object.
     * @param socket Socket to write the HTTP Response
     * @param response HTTP Response object to write to Socket
     * @throws IOException if any IO operation fails
     */
    protected void process(Socket socket, HTTPResponse response) throws IOException {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        writer.write(response.status + "\r\n");
        for (Map.Entry<String, String> entry : response.headers.entrySet())
            writer.write(entry.getKey() + ": " + entry.getValue() + "\r\n");
        writer.write("Connection: close\r\n");
        writer.write("\r\n");
        if (response.body != null && !response.body.isEmpty())
            writer.write(response.body);
        writer.close();
    }

    /**
     * Record storing all necessary information from HTTP Request made to SimpleHTTPd
     */
    public static record HTTPRequest(String method, String path, String version, Map<String, String> headers, Map<String, String> params, Map<String, Set<String>> paramsMultiValue, String body) {
    }

    /**
     * Record storing all necessary information to respond to HTTP Request made to SimpleHTTPd
     */
    public static record HTTPResponse(String status, Map<String, String> headers, String body) {
    }

}

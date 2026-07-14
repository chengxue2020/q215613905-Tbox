package com.github.tvbox.osc.dlna;

import org.fourthline.cling.model.message.Connection;
import org.fourthline.cling.model.message.StreamRequestMessage;
import org.fourthline.cling.model.message.StreamResponseMessage;
import org.fourthline.cling.model.message.UpnpHeaders;
import org.fourthline.cling.model.message.UpnpMessage;
import org.fourthline.cling.model.message.UpnpRequest;
import org.fourthline.cling.protocol.ProtocolFactory;
import org.fourthline.cling.transport.Router;
import org.fourthline.cling.transport.spi.InitializationException;
import org.fourthline.cling.transport.spi.StreamServer;
import org.fourthline.cling.transport.spi.StreamServerConfiguration;
import org.fourthline.cling.transport.spi.UpnpStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SocketHttpStreamServer implements StreamServer<SocketHttpStreamServer.Configuration> {
    private static final Charset ISO_8859_1 = Charset.forName("ISO-8859-1");
    private final Configuration configuration;
    private ServerSocket serverSocket;
    private volatile boolean stopped;
    private Router router;

    public SocketHttpStreamServer(Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public Configuration getConfiguration() {
        return configuration;
    }

    @Override
    public void init(InetAddress bindAddress, Router router) throws InitializationException {
        this.router = router;
        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(bindAddress, configuration.getListenPort()), 50);
        } catch (IOException e) {
            throw new InitializationException("Could not bind HTTP server socket on " + bindAddress, e);
        }
    }

    @Override
    public int getPort() {
        return serverSocket != null ? serverSocket.getLocalPort() : -1;
    }

    @Override
    public void stop() {
        stopped = true;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {
        }
    }

    @Override
    public void run() {
        while (!stopped) {
            try {
                Socket socket = serverSocket.accept();
                socket.setSoTimeout(30000);
                router.received(new SocketUpnpStream(router.getProtocolFactory(), socket));
            } catch (SocketException e) {
                break;
            } catch (IOException ignored) {
            }
        }
    }

    private static class SocketUpnpStream extends UpnpStream {
        private final Socket socket;

        SocketUpnpStream(ProtocolFactory protocolFactory, Socket socket) {
            super(protocolFactory);
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                InputStream inputStream = socket.getInputStream();
                String requestLine = readLine(inputStream);
                String[] parts = requestLine.split(" ", 3);
                if (requestLine.length() == 0 || parts.length < 2) {
                    socket.close();
                    return;
                }
                Map<String, List<String>> headers = readHeaders(inputStream);
                StreamRequestMessage requestMessage = buildRequestMessage(parts[0], parts[1], headers);
                readBodyInto(inputStream, requestMessage, headers);
                StreamResponseMessage responseMessage = process(requestMessage);
                OutputStream outputStream = socket.getOutputStream();
                writeResponse(outputStream, responseMessage);
                outputStream.flush();
                responseSent(responseMessage);
            } catch (Exception e) {
                responseException(e);
            } finally {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        }

        private String readLine(InputStream inputStream) throws IOException {
            StringBuilder sb = new StringBuilder();
            int previous = -1;
            int value;
            while ((value = inputStream.read()) != -1) {
                if (previous == '\r' && value == '\n') {
                    sb.deleteCharAt(sb.length() - 1);
                    return sb.toString();
                }
                sb.append((char) value);
                previous = value;
            }
            return sb.toString();
        }

        private Map<String, List<String>> readHeaders(InputStream inputStream) throws IOException {
            Map<String, List<String>> headers = new HashMap<>();
            String line;
            while (!(line = readLine(inputStream)).isEmpty()) {
                int colon = line.indexOf(':');
                if (colon < 0) continue;
                String key = line.substring(0, colon).trim().toLowerCase();
                String value = line.substring(colon + 1).trim();
                List<String> values = headers.get(key);
                if (values == null) {
                    values = new ArrayList<>();
                    headers.put(key, values);
                }
                values.add(value);
            }
            return headers;
        }

        private StreamRequestMessage buildRequestMessage(String method, String rawUri, Map<String, List<String>> headers) {
            StreamRequestMessage message = new StreamRequestMessage(UpnpRequest.Method.getByHttpName(method), URI.create(rawUri));
            message.setConnection(new SocketConnection(socket));
            message.setHeaders(new UpnpHeaders(headers));
            return message;
        }

        private void readBodyInto(InputStream inputStream, StreamRequestMessage message, Map<String, List<String>> headers) throws IOException {
            List<String> lengthHeaders = headers.get("content-length");
            if (lengthHeaders == null || lengthHeaders.isEmpty()) return;
            int length = Integer.parseInt(lengthHeaders.get(0).trim());
            if (length <= 0) return;
            byte[] body = new byte[length];
            int offset = 0;
            int read;
            while (offset < length && (read = inputStream.read(body, offset, length - offset)) != -1) {
                offset += read;
            }
            if (message.isContentTypeMissingOrText()) message.setBodyCharacters(body);
            else message.setBody(UpnpMessage.BodyType.BYTES, body);
        }

        private void writeResponse(OutputStream outputStream, StreamResponseMessage message) throws IOException {
            if (message == null) {
                writeStatusLine(outputStream, 404, "Not Found");
                writeHeader(outputStream, "Content-Length", "0");
                writeEndHeaders(outputStream);
                return;
            }
            writeStatusLine(outputStream, message.getOperation().getStatusCode(), message.getOperation().getStatusMessage());
            for (Map.Entry<String, List<String>> entry : message.getHeaders().entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) continue;
                for (String value : entry.getValue()) {
                    writeHeader(outputStream, entry.getKey(), value);
                }
            }
            byte[] body = message.hasBody() ? message.getBodyBytes() : null;
            writeHeader(outputStream, "Content-Length", String.valueOf(body != null ? body.length : 0));
            writeEndHeaders(outputStream);
            if (body != null && body.length > 0) outputStream.write(body);
        }

        private void writeStatusLine(OutputStream outputStream, int code, String reason) throws IOException {
            outputStream.write(("HTTP/1.1 " + code + " " + (reason == null ? "" : reason) + "\r\n").getBytes(ISO_8859_1));
        }

        private void writeHeader(OutputStream outputStream, String name, String value) throws IOException {
            outputStream.write((name + ": " + value + "\r\n").getBytes(ISO_8859_1));
        }

        private void writeEndHeaders(OutputStream outputStream) throws IOException {
            outputStream.write("\r\n".getBytes(ISO_8859_1));
        }
    }

    private static class SocketConnection implements Connection {
        private final Socket socket;

        SocketConnection(Socket socket) {
            this.socket = socket;
        }

        @Override
        public boolean isOpen() {
            return !socket.isClosed();
        }

        @Override
        public InetAddress getRemoteAddress() {
            return socket.getInetAddress();
        }

        @Override
        public InetAddress getLocalAddress() {
            return socket.getLocalAddress();
        }
    }

    public static class Configuration implements StreamServerConfiguration {
        private final int listenPort;

        public Configuration(int listenPort) {
            this.listenPort = listenPort;
        }

        @Override
        public int getListenPort() {
            return listenPort;
        }
    }
}

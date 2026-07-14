package com.github.tvbox.osc.dlna;

import org.fourthline.cling.model.message.StreamRequestMessage;
import org.fourthline.cling.model.message.StreamResponseMessage;
import org.fourthline.cling.model.message.UpnpHeaders;
import org.fourthline.cling.model.message.UpnpResponse;
import org.fourthline.cling.transport.spi.AbstractStreamClient;
import org.fourthline.cling.transport.spi.AbstractStreamClientConfiguration;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OkHttpStreamClient extends AbstractStreamClient<OkHttpStreamClient.Configuration, Call> {
    private final Configuration configuration;
    private final OkHttpClient httpClient;

    public OkHttpStreamClient(Configuration configuration) {
        this.configuration = configuration;
        int timeout = configuration.getTimeoutSeconds() + 5;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(timeout, TimeUnit.SECONDS)
                .readTimeout(timeout, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public Configuration getConfiguration() {
        return configuration;
    }

    @Override
    protected Call createRequest(StreamRequestMessage requestMessage) {
        String method = requestMessage.getOperation().getHttpMethodName();
        Request.Builder builder = new Request.Builder()
                .url(requestMessage.getOperation().getURI().toString())
                .method(method, buildRequestBody(requestMessage, method));
        for (Map.Entry<String, List<String>> entry : requestMessage.getHeaders().entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            for (String value : entry.getValue()) {
                if (value != null) builder.addHeader(entry.getKey(), value);
            }
        }
        if (requestMessage.getHeaders().get("user-agent") == null) {
            builder.header("User-Agent", configuration.getUserAgentValue(requestMessage.getUdaMajorVersion(), requestMessage.getUdaMinorVersion()));
        }
        return httpClient.newCall(builder.build());
    }

    private RequestBody buildRequestBody(StreamRequestMessage requestMessage, String method) {
        if (requestMessage.hasBody()) {
            byte[] bytes = requestMessage.getBodyBytes();
            if (bytes != null && bytes.length > 0) {
                List<String> contentTypes = requestMessage.getHeaders().get("content-type");
                MediaType mediaType = contentTypes != null && !contentTypes.isEmpty() ? MediaType.parse(contentTypes.get(0)) : null;
                return RequestBody.create(mediaType, bytes);
            }
        }
        return requiresBody(method) ? RequestBody.create(null, new byte[0]) : null;
    }

    private boolean requiresBody(String method) {
        return "POST".equals(method) || "NOTIFY".equals(method) || "PUT".equals(method);
    }

    @Override
    protected Callable<StreamResponseMessage> createCallable(StreamRequestMessage requestMessage, final Call call) {
        return new Callable<StreamResponseMessage>() {
            @Override
            public StreamResponseMessage call() throws Exception {
                Response response = call.execute();
                try {
                    StreamResponseMessage responseMessage = new StreamResponseMessage(new UpnpResponse(response.code(), response.message()));
                    UpnpHeaders upnpHeaders = new UpnpHeaders();
                    for (String name : response.headers().names()) {
                        for (String value : response.headers(name)) {
                            upnpHeaders.add(name, value);
                        }
                    }
                    responseMessage.setHeaders(upnpHeaders);
                    byte[] bytes = response.body() == null ? new byte[0] : response.body().bytes();
                    if (bytes.length > 0) responseMessage.setBodyCharacters(bytes);
                    return responseMessage;
                } finally {
                    response.close();
                }
            }
        };
    }

    @Override
    protected void abort(Call call) {
        call.cancel();
    }

    @Override
    protected boolean logExecutionException(Throwable t) {
        return false;
    }

    @Override
    public void stop() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }

    public static class Configuration extends AbstractStreamClientConfiguration {
        public Configuration(ExecutorService executorService) {
            super(executorService);
        }
    }
}

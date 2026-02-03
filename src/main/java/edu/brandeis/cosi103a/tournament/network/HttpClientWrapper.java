package edu.brandeis.cosi103a.tournament.network;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

/**
 * Wrapper interface around HttpClient to enable mocking in tests.
 */
public interface HttpClientWrapper {

    HttpResponse<String> send(HttpRequest request, HttpResponse.BodyHandler<String> bodyHandler)
            throws IOException, InterruptedException;

    CompletableFuture<HttpResponse<String>> sendAsync(
            HttpRequest request,
            HttpResponse.BodyHandler<String> bodyHandler);

    /**
     * Default implementation that wraps a real HttpClient.
     */
    class Default implements HttpClientWrapper {
        private final HttpClient httpClient;

        public Default() {
            this.httpClient = HttpClient.newHttpClient();
        }

        @Override
        public HttpResponse<String> send(HttpRequest request, HttpResponse.BodyHandler<String> bodyHandler)
                throws IOException, InterruptedException {
            return httpClient.send(request, bodyHandler);
        }

        @Override
        public CompletableFuture<HttpResponse<String>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<String> bodyHandler) {
            return httpClient.sendAsync(request, bodyHandler);
        }
    }
}

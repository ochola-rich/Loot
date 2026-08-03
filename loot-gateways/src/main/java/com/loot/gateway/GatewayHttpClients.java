package com.loot.gateway;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * Builds RestClient instances pinned to HTTP/1.1. The JDK HttpClient that
 * RestClient uses by default negotiates HTTP/2 for plaintext connections in
 * some environments, which triggers RST_STREAM resets against servers that
 * don't expect it on POST bodies (seen against WireMock's Jetty backend in
 * tests) - pinning avoids that class of failure against real gateways too.
 */
public final class GatewayHttpClients {

    private GatewayHttpClients() {}

    public static RestClient restClient(String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .build();
    }
}

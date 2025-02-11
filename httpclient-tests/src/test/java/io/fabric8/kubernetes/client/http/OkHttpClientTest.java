/**
 * Copyright (C) 2015 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fabric8.kubernetes.client.http;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.http.HttpClient.AsyncBody;
import io.fabric8.kubernetes.client.http.WebSocket.Listener;
import io.fabric8.kubernetes.client.okhttp.OkHttpClientFactory;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests a {@link HttpClient} at or below the {@link KubernetesClient} level.
 */
class OkHttpClientTest {

  protected HttpClient.Factory getHttpClientFactory() {
    return new OkHttpClientFactory();
  }

  private KubernetesMockServer server;
  private KubernetesClient client;

  @BeforeEach
  void setUp() {
    server = new KubernetesMockServer();
    server.start();
    client = server.createClient(getHttpClientFactory());
  }

  @AfterEach
  void tearDown() {
    client.close();
    server.shutdown();
  }

  @Test
  void testMultipleClosure() {
    client.getHttpClient().close();
    client.getHttpClient().close(); // additional close should be no-op
  }

  @Test
  void testWebsocketHandshakeFailure() {
    CompletableFuture<WebSocket> startedFuture = client.getHttpClient().newWebSocketBuilder()
        .uri(URI.create(client.getConfiguration().getMasterUrl()))
        .buildAsync(new Listener() {
        });

    assertThrows(WebSocketHandshakeException.class, () -> {
      try {
        startedFuture.get();
      } catch (ExecutionException e) {
        throw e.getCause();
      }
    });
  }

  @Test
  void testOnOpen() throws Exception {
    server.expect().withPath("/foo")
        .andUpgradeToWebSocket()
        .open()
        .done().always();

    final CompletableFuture<Boolean> opened = new CompletableFuture<>();
    final CompletableFuture<Boolean> future = client.getHttpClient().newWebSocketBuilder()
        .uri(URI.create(client.getConfiguration().getMasterUrl() + "foo"))
        .buildAsync(new Listener() {

          @Override
          public void onOpen(WebSocket webSocket) {
            opened.complete(true);
          }

        })
        .thenCompose(ws -> opened);

    assertThat(future.get(10, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  void testRequest() throws Exception {
    server.expect().withPath("/foo")
        .andUpgradeToWebSocket()
        .open()
        .waitFor(0)
        .andEmit("hello")
        .waitFor(0)
        .andEmit("world")
        .done().always();

    CountDownLatch latch = new CountDownLatch(2);

    CompletableFuture<WebSocket> startedFuture = client.getHttpClient().newWebSocketBuilder()
        .uri(URI.create(client.getConfiguration().getMasterUrl() + "foo"))
        .buildAsync(new Listener() {

          @Override
          public void onMessage(WebSocket webSocket, String text) {
            latch.countDown();
          }

        });

    assertFalse(latch.await(2, TimeUnit.SECONDS));
    assertEquals(1, latch.getCount());

    startedFuture.get().request();

    assertTrue(latch.await(1, TimeUnit.SECONDS));
  }

  @Test
  void testAsyncBody() throws Exception {
    server.expect().withPath("/async").andReturn(200, "hello world").always();

    CompletableFuture<Boolean> consumed = new CompletableFuture<>();

    CompletableFuture<HttpResponse<AsyncBody>> responseFuture = client.getHttpClient().consumeBytes(
        client.getHttpClient().newHttpRequestBuilder().uri(URI.create(client.getConfiguration().getMasterUrl() + "async"))
            .build(),
        (value, asyncBody) -> {
          consumed.complete(true);
          asyncBody.consume();
        });

    responseFuture.whenComplete((r, t) -> {
      if (t != null) {
        consumed.completeExceptionally(t);
      }
      if (r != null) {
        r.body().consume();
        r.body().done().whenComplete((v, ex) -> {
          if (ex != null) {
            consumed.completeExceptionally(ex);
          }
          if (v != null) {
            consumed.complete(false);
          }
        });
      }
    });

    assertTrue(consumed.get(5, TimeUnit.SECONDS));
  }

  @DisplayName("Supported response body types")
  @ParameterizedTest(name = "{index}: {0}")
  @ValueSource(classes = { String.class, byte[].class, Reader.class, InputStream.class })
  void testSupportedTypes(Class<?> type) throws Exception {
    server.expect().withPath("/type").andReturn(200, "hello world").always();
    final HttpResponse<?> result = client.getHttpClient()
        .sendAsync(client.getHttpClient().newHttpRequestBuilder()
            .uri(URI.create(client.getConfiguration().getMasterUrl() + "type")).build(), type)
        .get(10, TimeUnit.SECONDS);
    assertThat(result)
        .satisfies(r -> assertThat(r.body()).isInstanceOf(type))
        .satisfies(r -> assertThat(r.bodyString()).isEqualTo("hello world"));
  }

}

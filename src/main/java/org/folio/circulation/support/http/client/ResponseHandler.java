package org.folio.circulation.support.http.client;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpClientResponse;

import java.util.concurrent.CompletableFuture;

public class ResponseHandler {
  public static Handler<HttpClientResponse> empty(
    CompletableFuture<Response> completed) {

    return response -> {
      try {
        int statusCode = response.statusCode();

        completed.complete(new Response(statusCode, null));
      }
      catch(Exception e) {
        completed.completeExceptionally(e);
      }
    };
  }

  public static Handler<HttpClientResponse> json(
    CompletableFuture<Response> completed) {

    return response -> {
      response.bodyHandler(buffer -> {
        try {
          int statusCode = response.statusCode();
          String body = BufferHelper.stringFromBuffer(buffer);

          System.out.println(String.format("Response: %s", body));

          completed.complete(new Response(statusCode, body));

        } catch(Exception e) {
          completed.completeExceptionally(e);
        }
      });
    };
  }

  public static Handler<HttpClientResponse> text(
    CompletableFuture<Response> completed) {

    return response -> {
        int statusCode = response.statusCode();

        response.bodyHandler(buffer -> {
          try {
            String body = BufferHelper.stringFromBuffer(buffer);

            completed.complete(new Response(statusCode, body));

          } catch (Exception e) {
            completed.completeExceptionally(e);
          }
        });
    };
  }
}

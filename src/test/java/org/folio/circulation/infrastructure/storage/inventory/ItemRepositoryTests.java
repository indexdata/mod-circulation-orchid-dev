package org.folio.circulation.infrastructure.storage.inventory;

import static api.support.matchers.FailureMatcher.isErrorFailureContaining;
import static api.support.matchers.ResultMatchers.succeeded;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.folio.circulation.domain.Holdings;
import org.folio.circulation.domain.Instance;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.LoanType;
import org.folio.circulation.domain.Location;
import org.folio.circulation.domain.MaterialType;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;
import lombok.SneakyThrows;

class ItemRepositoryTests {
  @Test
  void cannotUpdateAnItemThatHasNotBeenFetched() {
    final var repository = createRepository(null);

    final var notFetchedItem = dummyItem();

    final var updateResult = get(repository.updateItem(notFetchedItem));

    assertThat(updateResult, isErrorFailureContaining(
      "Cannot update item when original representation is not available in identity map"));
  }

  @Test
  void canUpdateAnItemThatHasBeenFetched() {
    final var itemsClient = mock(CollectionResourceClient.class);
    final var repository = createRepository(itemsClient);

    final var itemJson = new JsonObject()
      .put("holdingsRecordId", UUID.randomUUID())
      .put("effectiveLocationId", UUID.randomUUID());

    mockedClientGet(itemsClient, itemJson.encodePrettily());

    when(itemsClient.put(any(), any())).thenReturn(ofAsync(
      () -> new Response(204, null, "application/json")));

    final var fetchedItem = get(repository.fetchById(UUID.randomUUID().toString()))
      .value();

    final var updateResult = get(repository.updateItem(fetchedItem));

    assertThat(updateResult, succeeded());
  }

  private void mockedClientGet(CollectionResourceClient client, String body) {
    when(client.get(anyString())).thenReturn(Result.ofAsync(
      () -> new Response(200, body, "application/json")));
  }

  private ItemRepository createRepository(CollectionResourceClient itemsClient) {
    final var holdingsClient = mock(CollectionResourceClient.class);
    final var instancesClient = mock(CollectionResourceClient.class);
    final var locationRepository = mock(LocationRepository.class);
    final var materialTypeRepository = mock(MaterialTypeRepository.class);

    final var holdings = new JsonObject()
      .put("instanceId", UUID.randomUUID());

    mockedClientGet(holdingsClient, holdings.encodePrettily());

    final var instance = new JsonObject();

    mockedClientGet(instancesClient, instance.encodePrettily());

    when(locationRepository.getLocation(any()))
      .thenReturn(ofAsync(() -> Location.from(new JsonObject())));

    when(materialTypeRepository.getFor(any()))
      .thenReturn(ofAsync(MaterialType::unknown));

    return new ItemRepository(itemsClient, holdingsClient, instancesClient,
      null, locationRepository, materialTypeRepository, null);
  }

  private Item dummyItem() {
    return new Item(null, null, null, null, null, null, null, false,
      Holdings.unknown(), Instance.unknown(), MaterialType.unknown(),
      LoanType.unknown());
  }

  @SneakyThrows
  private <T> Result<T> get(CompletableFuture<Result<T>> future) {
    return future.get(1, TimeUnit.SECONDS);
  }
}

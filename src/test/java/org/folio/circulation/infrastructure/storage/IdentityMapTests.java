package org.folio.circulation.infrastructure.storage;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.List;
import java.util.UUID;

import org.folio.circulation.domain.MultipleRecords;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class IdentityMapTests {
  private final IdentityMap identityMap = new IdentityMap(json -> getProperty(json, "id"));

  @Nested
  @DisplayName("when created")
  class whenNew {
    @Nested
    @DisplayName("after adding an entry")
    class AfterAddingAnEntry {
      private final String id = UUID.randomUUID().toString();
      private final JsonObject entry = createEntry(id, "foo");

      @BeforeEach
      void addEntry() {
        identityMap.add(entry);
      }

      @Test
      void includesTheEntry() {
        /*
          This is somewhat counter-intuitive.
          At the moment, the only client cares about an entry not being present
          so that it can fail fast with an error, and hence no `entryPresent`
          method is needed at the moment
        */
        assertThat(identityMap.entryNotPresent(id), is(false));
      }

      @Test
      void theEntryCanBeRetrieved() {
        final var fetchedEntry = identityMap.get(id);

        assertThat(fetchedEntry, is(notNullValue()));
        assertThat(getProperty(fetchedEntry, "id"), is(id));
        assertThat(getProperty(fetchedEntry, "name"), is("foo"));
      }

      @Test
      void theRetrievedEntryIsACopy() {
        final var fetchedEntry = identityMap.get(id);

        /*
          As JsonObject is mutable, the entry in the identity map must be a copy
          to stop it from being changed by a different reference
         */
        assertThat(fetchedEntry, is(not(sameInstance(entry))));
      }
    }

    @Nested
    @DisplayName("after adding multiple entries")
    class AfterAddingMultipleEntry {
      private final String firstId = UUID.randomUUID().toString();
      private final String secondId = UUID.randomUUID().toString();
      private final String thirdId = UUID.randomUUID().toString();

      @BeforeEach
      void addEntries() {
        final var multipleEntries = new MultipleRecords<>(
          List.of(createEntry(firstId, "foo"),
            createEntry(secondId, "bar"), createEntry(thirdId, "squiggle")), 3);

        identityMap.add(multipleEntries);
      }

      @Test
      void includesTheEntries() {
        assertThat(identityMap.entryNotPresent(firstId), is(false));
        assertThat(identityMap.entryNotPresent(secondId), is(false));
        assertThat(identityMap.entryNotPresent(thirdId), is(false));
      }

      @Test
      void doesNotIncludeOtherEntries() {
        assertThat(identityMap.entryNotPresent(UUID.randomUUID().toString()), is(true));
      }

      @Test
      void theEntriesCanBeRetrieved() {
        assertThat(getName(firstId), is("foo"));
        assertThat(getName(secondId), is("bar"));
        assertThat(getName(thirdId), is("squiggle"));
      }

      private String getName(String firstId) {
        return getProperty(identityMap.get(firstId), "name");
      }
    }
  }

  private JsonObject createEntry(String id, String name) {
    return new JsonObject()
      .put("id", id)
      .put("name", name);
  }
}

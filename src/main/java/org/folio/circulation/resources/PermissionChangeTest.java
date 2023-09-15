package org.folio.circulation.resources;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.infrastructure.storage.users.AddressTypeRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.WebContext;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.List;


public class PermissionChangeTest extends Resource {

  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public PermissionChangeTest(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    final String rootPath = "/circulation/test-permission-change-for-timed-process";
    log.info("TIMER-PROCESS test permission changes: Registering timed process {}", rootPath);
    RouteRegistration routeRegistration = new RouteRegistration(rootPath, router);
    routeRegistration.create(this::process);
  }

  private void process(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);
    AddressTypeRepository repo = new AddressTypeRepository(clients);
    final Collection<String> uuids = List.of("93d3d88d-499b-45d0-9bc7-ac73c3a19880");
    repo.getAddressTypesByIds(uuids).whenComplete(
      (result, exception) -> {
        log.info("TIMER-PROCESS test permission changes. Result: {}", result);
        if (result.succeeded()) {
          if (result.value() != null) {
            log.info("TIMER-PROCESS succeeded, address types found: {}", result.value().size());
          }
        }
      }
    );
  }
}

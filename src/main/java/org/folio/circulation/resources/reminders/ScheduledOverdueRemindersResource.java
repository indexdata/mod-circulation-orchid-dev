package org.folio.circulation.resources.reminders;

import io.vertx.ext.web.Router;
import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.RoutingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.resources.Resource;
import org.folio.circulation.services.RemindersForOverdueLoansService;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.NoContentResponse;
import org.folio.circulation.support.http.server.WebContext;

import java.lang.invoke.MethodHandles;

import static org.folio.circulation.support.results.MappingFunctions.toFixedValue;

public class ScheduledOverdueRemindersResource extends Resource {

  protected static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public ScheduledOverdueRemindersResource(HttpClient client) {
    super(client);
  }
  @Override
  public void register(Router router) {
    new RouteRegistration("/circulation/scheduled-overdue-reminders", router)
      .create(this::scheduledReminders);
  }

  private void scheduledReminders(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final var clients = Clients.create(context, client);
    final var itemRepository = new ItemRepository(clients);
    final var userRepository = new UserRepository(clients);
    final var loanRepository = new LoanRepository(clients, itemRepository, userRepository);
    RemindersForOverdueLoansService remindersService
      = new RemindersForOverdueLoansService(clients, itemRepository, loanRepository);
    remindersService.processReminders()
      .thenApply(r -> r.map(toFixedValue(NoContentResponse::noContent)))
      .thenAccept(context::writeResultToHttpResponse);
  }
}

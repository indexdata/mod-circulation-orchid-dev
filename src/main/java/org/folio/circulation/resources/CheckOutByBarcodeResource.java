package org.folio.circulation.resources;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.folio.circulation.domain.*;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.LoanPolicyRepository;
import org.folio.circulation.support.*;
import org.folio.circulation.support.http.server.WebContext;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;

import java.util.UUID;
import java.util.function.Function;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.LoanValidation.*;

public class CheckOutByBarcodeResource extends CollectionResource {
  public CheckOutByBarcodeResource(HttpClient client) {
    super(client);
  }

  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration(
      "/circulation/check-out-by-barcode", router);

    routeRegistration.create(this::checkOut);
  }

  private void checkOut(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);

    JsonObject request = routingContext.getBodyAsJson();

    final JsonObject loan = new JsonObject();
    loan.put("id", UUID.randomUUID().toString());

    defaultStatusAndAction(loan);
    copyOrDefaultLoanDate(request, loan);

    final String itemBarcode = request.getString("itemBarcode");
    final String userBarcode = request.getString("userBarcode");
    final String proxyUserBarcode = request.getString("proxyUserBarcode");

    final Clients clients = Clients.create(context, client);

    final UserFetcher userFetcher = new UserFetcher(clients);
    final InventoryFetcher inventoryFetcher = new InventoryFetcher(clients);
    final RequestQueueFetcher requestQueueFetcher = new RequestQueueFetcher(clients);
    final LoanRepository loanRepository = new LoanRepository(clients);
    final LoanPolicyRepository loanPolicyRepository = new LoanPolicyRepository(clients);
    final MaterialTypeRepository materialTypeRepository = new MaterialTypeRepository(clients);
    final LocationRepository locationRepository = new LocationRepository(clients);
    final ProxyRelationshipValidator proxyRelationshipValidator = new ProxyRelationshipValidator(
      clients, () -> new ValidationErrorFailure(
      "Cannot check out item via proxy when relationship is invalid", "proxyUserBarcode",
      proxyUserBarcode));

    final UpdateItem updateItem = new UpdateItem(clients);
    final UpdateRequestQueue requestQueueUpdate = new UpdateRequestQueue(clients);

    final LoanRepresentation loanRepresentation = new LoanRepresentation();

    completedFuture(HttpResult.success(new LoanAndRelatedRecords(Loan.from(loan))))
      .thenCombineAsync(userFetcher.getUserByBarcode(userBarcode), this::addUser)
      .thenCombineAsync(userFetcher.getProxyUserByBarcode(proxyUserBarcode), this::addProxyUser)
      .thenApply(r -> refuseWhenRequestingUserIsInactive(r, userBarcode))
      .thenApply(r -> refuseWhenProxyingUserIsInactive(r, proxyUserBarcode))
      .thenCombineAsync(inventoryFetcher.fetchByBarcode(itemBarcode), this::addInventoryRecords)
      .thenApply(r -> r.next(v -> refuseWhenItemBarcodeDoesNotExist(r, itemBarcode)))
      .thenApply(r -> r.map(mapBarcodes()))
      .thenApply(r -> refuseWhenItemIsAlreadyCheckedOut(r, itemBarcode))
      .thenComposeAsync(r -> r.after(proxyRelationshipValidator::refuseWhenInvalid))
      .thenComposeAsync(r -> r.after(records ->
        refuseWhenHasOpenLoan(records, loanRepository, itemBarcode)))
      .thenComposeAsync(r -> r.after(requestQueueFetcher::get))
      .thenApply(r -> refuseWhenUserIsNotAwaitingPickup(r, userBarcode))
      .thenComposeAsync(r -> r.after(materialTypeRepository::getMaterialType))
      .thenComposeAsync(r -> r.after(locationRepository::getLocation))
      .thenComposeAsync(r -> r.after(loanPolicyRepository::lookupLoanPolicy))
      .thenApply(r -> r.next(this::calculateDueDate))
      .thenComposeAsync(r -> r.after(requestQueueUpdate::onCheckOut))
      .thenComposeAsync(r -> r.after(updateItem::onCheckOut))
      .thenComposeAsync(r -> r.after(loanRepository::createLoan))
      .thenApply(r -> r.map(loanRepresentation::extendedLoan))
      .thenApply(this::createdLoanFrom)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  private HttpResult<LoanAndRelatedRecords> calculateDueDate(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    final Loan loan = loanAndRelatedRecords.loan;
    final LoanPolicy loanPolicy = loanAndRelatedRecords.loanPolicy;

    return loanPolicy.calculate(loan)
      .map(dueDate -> {
        loanAndRelatedRecords.loan.put("dueDate",
          dueDate.toString(ISODateTimeFormat.dateTime()));

        return loanAndRelatedRecords;
      });
  }

  private void copyOrDefaultLoanDate(JsonObject request, JsonObject loan) {
    final String loanDateProperty = "loanDate";
    if(request.containsKey(loanDateProperty)) {
      loan.put(loanDateProperty, request.getString(loanDateProperty));
    } else {
      loan.put(loanDateProperty, DateTime.now().toDateTime(DateTimeZone.UTC)
        .toString(ISODateTimeFormat.dateTime()));
    }
  }

  private Function<LoanAndRelatedRecords, LoanAndRelatedRecords> mapBarcodes() {
    return loanAndRelatedRecords -> {
      final Loan loan = loanAndRelatedRecords.loan;

      loan.put("userId", loanAndRelatedRecords.requestingUser.getString("id"));
      loan.put("itemId", loanAndRelatedRecords.inventoryRecords.item.getString("id"));

      if(loanAndRelatedRecords.proxyingUser != null) {
        loan.put("proxyUserId", loanAndRelatedRecords.proxyingUser.getString("id"));
      }

      return loanAndRelatedRecords;
    };
  }

  private WritableHttpResult<JsonObject> createdLoanFrom(HttpResult<JsonObject> result) {
    if(result.failed()) {
      return HttpResult.failure(result.cause());
    }
    else {
      return new CreatedJsonHttpResult(result.value(),
        String.format("/circulation/loans/%s", result.value().getString("id")));
    }
  }

  private HttpResult<LoanAndRelatedRecords> addProxyUser(
    HttpResult<LoanAndRelatedRecords> loanResult,
    HttpResult<User> getUserResult) {

    return HttpResult.combine(loanResult, getUserResult,
      LoanAndRelatedRecords::withProxyingUser);
  }

  private HttpResult<LoanAndRelatedRecords> addUser(
    HttpResult<LoanAndRelatedRecords> loanResult,
    HttpResult<User> getUserResult) {

    return HttpResult.combine(loanResult, getUserResult,
      LoanAndRelatedRecords::withRequestingUser);
  }

  private HttpResult<LoanAndRelatedRecords> addInventoryRecords(
    HttpResult<LoanAndRelatedRecords> loanResult,
    HttpResult<InventoryRecords> inventoryRecordsResult) {

    return HttpResult.combine(loanResult, inventoryRecordsResult,
      LoanAndRelatedRecords::withInventoryRecords);
  }
}

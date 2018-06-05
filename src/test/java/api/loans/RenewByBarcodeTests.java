package api.loans;

import api.APITestSuite;
import api.support.APITests;
import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.builders.FixedDueDateSchedule;
import api.support.builders.FixedDueDateSchedulesBuilder;
import api.support.builders.LoanPolicyBuilder;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Seconds;
import org.junit.Ignore;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static api.support.builders.ItemBuilder.CHECKED_OUT;
import static api.support.matchers.ItemStatusCodeMatcher.hasItemStatus;
import static api.support.matchers.JsonObjectMatchers.hasSoleErrorFor;
import static api.support.matchers.JsonObjectMatchers.hasSoleErrorMessageContaining;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static api.support.matchers.TextDateTimeMatcher.withinSecondsAfter;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class RenewByBarcodeTests extends APITests {
  @Test
  public void canRenewRollingLoanFromSystemDate()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    final UUID loanId = loansFixture.checkOutByBarcode(smallAngryPlanet, jessica,
      new DateTime(2018, 4, 21, 11, 21, 43))
      .getId();

    //TODO: Renewal based upon system date,
    // needs to be approximated, at least until we introduce a calendar and clock
    DateTime approximateRenewalDate = DateTime.now();

    final JsonObject renewedLoan = loansFixture
      .renewLoan(smallAngryPlanet, jessica)
      .getJson();

    assertThat(renewedLoan.getString("id"), is(loanId.toString()));

    assertThat("user ID should match barcode",
      renewedLoan.getString("userId"), is(jessica.getId().toString()));

    assertThat("item ID should match barcode",
      renewedLoan.getString("itemId"), is(smallAngryPlanet.getId().toString()));

    assertThat("status should be open",
      renewedLoan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("action should be renewed",
      renewedLoan.getString("action"), is("renewed"));

    assertThat("renewal count should be incremented",
      renewedLoan.getInteger("renewalCount"), is(1));

    assertThat("last loan policy should be stored",
      renewedLoan.getString("loanPolicyId"),
      is(APITestSuite.canCirculateRollingLoanPolicyId().toString()));

    assertThat("due date should be approximately 3 weeks after renewal date, based upon loan policy",
      renewedLoan.getString("dueDate"),
      withinSecondsAfter(Seconds.seconds(10), approximateRenewalDate.plusWeeks(3)));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }

  @Test
  public void canRenewRollingLoanFromCurrentDueDate()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    final IndividualResource loan = loansFixture.checkOutByBarcode(smallAngryPlanet, jessica,
      new DateTime(2018, 4, 21, 11, 21, 43));

    final UUID loanId = loan.getId();

    LoanPolicyBuilder currentDueDateRollingPolicy = new LoanPolicyBuilder()
      .withName("Current Due Date Rolling Policy")
      .rolling(Period.months(2))
      .renewFromCurrentDueDate();

    UUID dueDateLimitedPolicyId = loanPolicyClient.create(currentDueDateRollingPolicy).getId();

    //Need to remember in order to delete after test
    policiesToDelete.add(dueDateLimitedPolicyId);

    useLoanPolicyAsFallback(dueDateLimitedPolicyId);

    final JsonObject renewedLoan = loansFixture
      .renewLoan(smallAngryPlanet, jessica)
      .getJson();

    assertThat(renewedLoan.getString("id"), is(loanId.toString()));

    assertThat("user ID should match barcode",
      renewedLoan.getString("userId"), is(jessica.getId().toString()));

    assertThat("item ID should match barcode",
      renewedLoan.getString("itemId"), is(smallAngryPlanet.getId().toString()));

    assertThat("status should be open",
      renewedLoan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("action should be renewed",
      renewedLoan.getString("action"), is("renewed"));

    assertThat("renewal count should be incremented",
      renewedLoan.getInteger("renewalCount"), is(1));

    assertThat("last loan policy should be stored",
      renewedLoan.getString("loanPolicyId"), is(dueDateLimitedPolicyId.toString()));

    assertThat("due date should be 2 months after initial due date date",
      renewedLoan.getString("dueDate"),
        isEquivalentTo(new DateTime(2018, 7, 12, 11, 21, 43)));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }

  @Test
  public void canRenewUsingDueDateLimitedRollingLoanPolicy()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    FixedDueDateSchedulesBuilder dueDateLimitSchedule = new FixedDueDateSchedulesBuilder()
      .withName("March Only Due Date Limit")
      .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 3));

    final UUID dueDateLimitScheduleId = fixedDueDateScheduleClient.create(
      dueDateLimitSchedule).getId();

    //Need to remember in order to delete after test
    schedulesToDelete.add(dueDateLimitScheduleId);

    LoanPolicyBuilder dueDateLimitedPolicy = new LoanPolicyBuilder()
      .withName("Due Date Limited Rolling Policy")
      .rolling(Period.weeks(5))
      .limitedBySchedule(dueDateLimitScheduleId)
      .renewFromCurrentDueDate();

    UUID dueDateLimitedPolicyId = loanPolicyClient.create(dueDateLimitedPolicy).getId();

    //Need to remember in order to delete after test
    policiesToDelete.add(dueDateLimitedPolicyId);

    useLoanPolicyAsFallback(dueDateLimitedPolicyId);

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    final DateTime loanDate = new DateTime(2018, 3, 18, 11, 43, 54, DateTimeZone.UTC);

    loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .at(loanDate));

    final IndividualResource response = loansFixture.renewLoan(smallAngryPlanet, steve);

    final JsonObject loan = response.getJson();

    assertThat("last loan policy should be stored",
      loan.getString("loanPolicyId"), is(dueDateLimitedPolicyId.toString()));

    assertThat("due date should be limited by schedule",
      loan.getString("dueDate"),
      isEquivalentTo(new DateTime(2018, 3, 31, 23, 59, 59, DateTimeZone.UTC)));
  }

  @Test
  public void canRenewRollingLoanUsingDifferentPeriod()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    final IndividualResource loan = loansFixture.checkOutByBarcode(smallAngryPlanet, jessica,
      new DateTime(2018, 4, 21, 11, 21, 43));

    final UUID loanId = loan.getId();

    LoanPolicyBuilder currentDueDateRollingPolicy = new LoanPolicyBuilder()
      .withName("Current Due Date Different Period Rolling Policy")
      .rolling(Period.months(2))
      .renewFromCurrentDueDate()
      .renewWith(Period.months(1));

    UUID dueDateLimitedPolicyId = loanPolicyClient.create(currentDueDateRollingPolicy).getId();

    //Need to remember in order to delete after test
    policiesToDelete.add(dueDateLimitedPolicyId);

    useLoanPolicyAsFallback(dueDateLimitedPolicyId);

    final JsonObject renewedLoan = loansFixture
      .renewLoan(smallAngryPlanet, jessica)
      .getJson();

    assertThat(renewedLoan.getString("id"), is(loanId.toString()));

    assertThat("user ID should match barcode",
      renewedLoan.getString("userId"), is(jessica.getId().toString()));

    assertThat("item ID should match barcode",
      renewedLoan.getString("itemId"), is(smallAngryPlanet.getId().toString()));

    assertThat("status should be open",
      renewedLoan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("action should be renewed",
      renewedLoan.getString("action"), is("renewed"));

    assertThat("renewal count should be incremented",
      renewedLoan.getInteger("renewalCount"), is(1));

    assertThat("last loan policy should be stored",
      renewedLoan.getString("loanPolicyId"), is(dueDateLimitedPolicyId.toString()));

    assertThat("due date should be 2 months after initial due date date",
      renewedLoan.getString("dueDate"),
      isEquivalentTo(new DateTime(2018, 6, 12, 11, 21, 43)));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }

  @Test
  public void canRenewUsingAlternateDueDateLimitedRollingLoanPolicy()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    FixedDueDateSchedulesBuilder dueDateLimitSchedule = new FixedDueDateSchedulesBuilder()
      .withName("March Only Due Date Limit")
      .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 3));

    final UUID dueDateLimitScheduleId = fixedDueDateScheduleClient.create(
      dueDateLimitSchedule).getId();

    //Need to remember in order to delete after test
    schedulesToDelete.add(dueDateLimitScheduleId);

    LoanPolicyBuilder dueDateLimitedPolicy = new LoanPolicyBuilder()
      .withName("Due Date Limited Rolling Policy")
      .rolling(Period.weeks(5))
      .renewFromCurrentDueDate()
      .renewWith(Period.days(8), dueDateLimitScheduleId);

    UUID dueDateLimitedPolicyId = loanPolicyClient.create(dueDateLimitedPolicy).getId();

    //Need to remember in order to delete after test
    policiesToDelete.add(dueDateLimitedPolicyId);

    useLoanPolicyAsFallback(dueDateLimitedPolicyId);

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    final DateTime loanDate = new DateTime(2018, 3, 18, 11, 43, 54, DateTimeZone.UTC);

    loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .at(loanDate));

    final IndividualResource response = loansFixture.renewLoan(smallAngryPlanet, steve);

    final JsonObject loan = response.getJson();

    assertThat("last loan policy should be stored",
      loan.getString("loanPolicyId"), is(dueDateLimitedPolicyId.toString()));

    assertThat("due date should be limited by schedule",
      loan.getString("dueDate"),
      isEquivalentTo(new DateTime(2018, 3, 31, 23, 59, 59, DateTimeZone.UTC)));
  }

  @Test
  @Ignore("Need to be able to inject system date to run this reliably")
  public void canCheckOutUsingFixedDueDateLoanPolicy()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    FixedDueDateSchedulesBuilder fixedDueDateSchedules = new FixedDueDateSchedulesBuilder()
      .withName("February and March Only Due Date Limit")
      .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 2))
      .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 3));

    final UUID fixedDueDateSchedulesId = fixedDueDateScheduleClient.create(
      fixedDueDateSchedules).getId();

    //Need to remember in order to delete after test
    schedulesToDelete.add(fixedDueDateSchedulesId);

    LoanPolicyBuilder dueDateLimitedPolicy = new LoanPolicyBuilder()
      .withName("Fixed Due Date Policy")
      .fixed(fixedDueDateSchedulesId)
      .renewFromSystemDate();

    UUID fixedDueDatePolicyId = loanPolicyClient.create(dueDateLimitedPolicy).getId();

    //Need to remember in order to delete after test
    policiesToDelete.add(fixedDueDatePolicyId);

    useLoanPolicyAsFallback(fixedDueDatePolicyId);

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    final DateTime loanDate = new DateTime(2018, 2, 10, 34, 23, 12, DateTimeZone.UTC);

    loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .at(loanDate));

    //TODO: Need to be able to inject system date here
    final DateTime renewalDate = new DateTime(2018, 3, 14, 9, 21, 32, DateTimeZone.UTC);

    //e.g. Clock.freeze(renewalDate)

    final IndividualResource response = loansFixture.renewLoan(smallAngryPlanet, steve);

    final JsonObject loan = response.getJson();

    assertThat("last loan policy should be stored",
      loan.getString("loanPolicyId"), is(fixedDueDatePolicyId.toString()));

    assertThat("renewal count should be incremented",
      loan.getInteger("renewalCount"), is(1));

    assertThat("due date should be limited by schedule",
      loan.getString("dueDate"),
      isEquivalentTo(new DateTime(2018, 3, 31, 23, 59, 59, DateTimeZone.UTC)));
  }

  @Test
  public void canRenewMultipleTimesUpToRenewalLimit()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    LoanPolicyBuilder limitedRenewalsPolicy = new LoanPolicyBuilder()
      .withName("Limited Renewals Policy")
      .rolling(Period.days(2))
      .renewFromCurrentDueDate()
      .limitedRenewals(3);

    UUID limitedRenewalsPolicyId = loanPolicyClient.create(limitedRenewalsPolicy).getId();

    //Need to remember in order to delete after test
    policiesToDelete.add(limitedRenewalsPolicyId);

    useLoanPolicyAsFallback(limitedRenewalsPolicyId);

    final IndividualResource loan = loansFixture.checkOutByBarcode(smallAngryPlanet, jessica,
      new DateTime(2018, 4, 21, 11, 21, 43, DateTimeZone.UTC));

    final UUID loanId = loan.getId();

    loansFixture.renewLoan(smallAngryPlanet, jessica).getJson();

    loansFixture.renewLoan(smallAngryPlanet, jessica);

    final JsonObject renewedLoan = loansFixture
      .renewLoan(smallAngryPlanet, jessica)
      .getJson();

    assertThat(renewedLoan.getString("id"), is(loanId.toString()));

    assertThat("status should be open",
      renewedLoan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("action should be renewed",
      renewedLoan.getString("action"), is("renewed"));

    assertThat("renewal count should be incremented",
      renewedLoan.getInteger("renewalCount"), is(3));

    assertThat("last loan policy should be stored",
      renewedLoan.getString("loanPolicyId"), is(limitedRenewalsPolicyId.toString()));

    assertThat("due date should be 8 days after initial loan date date",
      renewedLoan.getString("dueDate"),
      isEquivalentTo(new DateTime(2018, 4, 29, 11, 21, 43, DateTimeZone.UTC)));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }

  @Test
  public void canGetRenewedLoan()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    final UUID loanId = loansFixture.checkOutByBarcode(smallAngryPlanet, jessica,
      new DateTime(2018, 4, 21, 11, 21, 43))
      .getId();

    //TODO: Renewal based upon system date,
    // needs to be approximated, at least until we introduce a calendar and clock
    DateTime approximateRenewalDate = DateTime.now();

    final IndividualResource response = loansFixture.renewLoan(smallAngryPlanet, jessica);

    final CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(APITestSuite.circulationModuleUrl(response.getLocation()),
      ResponseHandler.json(getCompleted));

    final Response getResponse = getCompleted.get(2, TimeUnit.SECONDS);

    assertThat(getResponse.getStatusCode(), is(HTTP_OK));

    JsonObject renewedLoan = getResponse.getJson();

    assertThat(renewedLoan.getString("id"), is(loanId.toString()));

    assertThat("user ID should match barcode",
      renewedLoan.getString("userId"), is(jessica.getId().toString()));

    assertThat("item ID should match barcode",
      renewedLoan.getString("itemId"), is(smallAngryPlanet.getId().toString()));

    assertThat("status should be open",
      renewedLoan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("action should be renewed",
      renewedLoan.getString("action"), is("renewed"));

    assertThat("renewal count should be incremented",
      renewedLoan.getInteger("renewalCount"), is(1));

    assertThat("last loan policy should be stored",
      renewedLoan.getString("loanPolicyId"),
      is(APITestSuite.canCirculateRollingLoanPolicyId().toString()));

    assertThat("due date should be approximately 3 weeks after renewal date, based upon loan policy",
      renewedLoan.getString("dueDate"),
      withinSecondsAfter(Seconds.seconds(10), approximateRenewalDate.plusWeeks(3)));
  }

  @Test
  public void cannotRenewLoanForDifferentUser()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    final IndividualResource jessica = usersFixture.jessica();

    loansFixture.checkOutByBarcode(smallAngryPlanet, jessica,
      new DateTime(2018, 4, 21, 11, 21, 43));

    final Response response = loansFixture.attemptRenewal(smallAngryPlanet, james);

    assertThat(response.getJson(), hasSoleErrorFor(
      "userBarcode", james.getJson().getString("barcode")));

    assertThat(response.getJson(),
      hasSoleErrorMessageContaining("Cannot renew item checked out to different user"));
  }

  @Test
  public void cannotRenewWhenLoanPolicyDoesNotExist()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    final UUID nonExistentloanPolicyId = UUID.randomUUID();

    loansFixture.checkOutByBarcode(smallAngryPlanet, jessica,
      new DateTime(2018, 4, 21, 11, 21, 43));

    useLoanPolicyAsFallback(nonExistentloanPolicyId);

    final Response response = loansFixture.attemptRenewal(500, smallAngryPlanet, jessica);

    assertThat(response.getBody(), is(String.format(
      "Loan policy %s could not be found, please check loan rules", nonExistentloanPolicyId)));
  }

  @Test
  public void cannotRenewWhenRenewalLimitReached()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();

    LoanPolicyBuilder limitedRenewalsPolicy = new LoanPolicyBuilder()
      .withName("Limited Renewals Policy")
      .rolling(Period.days(2))
      .renewFromCurrentDueDate()
      .limitedRenewals(3);

    UUID limitedRenewalsPolicyId = loanPolicyClient.create(limitedRenewalsPolicy).getId();

    //Need to remember in order to delete after test
    policiesToDelete.add(limitedRenewalsPolicyId);

    useLoanPolicyAsFallback(limitedRenewalsPolicyId);

    loansFixture.checkOutByBarcode(smallAngryPlanet, jessica,
      new DateTime(2018, 4, 21, 11, 21, 43, DateTimeZone.UTC));

    loansFixture.renewLoan(smallAngryPlanet, jessica);

    loansFixture.renewLoan(smallAngryPlanet, jessica);

    loansFixture.renewLoan(smallAngryPlanet, jessica);

    final Response response = loansFixture.attemptRenewal(smallAngryPlanet, jessica);

    assertThat(response.getJson(), hasSoleErrorFor(
      "loanPolicyId", limitedRenewalsPolicyId.toString()));

    assertThat(response.getJson(), hasSoleErrorMessageContaining(
        "Item can't be renewed as it has reached it's maximum number of renewals"));
  }
}

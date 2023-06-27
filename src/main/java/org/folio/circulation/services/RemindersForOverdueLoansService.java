package org.folio.circulation.services;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.StoreLoanAndItem;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.notice.schedule.LoanScheduledNoticeService;
import org.folio.circulation.domain.policy.OverdueFinePolicyRemindersPolicy;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.loans.OverdueFinePolicyRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.fetching.PageableFetcher;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.utils.ClockUtil;

import java.util.concurrent.CompletableFuture;

import static org.folio.circulation.domain.ItemStatus.*;
import static org.folio.circulation.support.CqlSortBy.ascending;
import static org.folio.circulation.support.http.client.CqlQuery.*;
import static org.folio.circulation.support.http.client.CqlQuery.notEqual;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.utils.DateFormatUtil.formatDateTime;

public class RemindersForOverdueLoansService {
  private static final Logger log = LogManager.getLogger(RemindersForOverdueLoansService.class);
  private final OverdueFinePolicyRepository overdueFinePolicyRepository;
  private final ItemRepository itemRepository;
  private final StoreLoanAndItem storeLoanAndItem;
  private final EventPublisher eventPublisher;
  private final PageableFetcher<Loan> loanPageableFetcher;
  private final LoanScheduledNoticeService loanScheduledNoticeService;

  public RemindersForOverdueLoansService (Clients clients,
                                          ItemRepository itemRepository,
                                          LoanRepository loanRepository) {
    this.itemRepository = itemRepository;
    this.storeLoanAndItem = new StoreLoanAndItem(loanRepository, itemRepository);
    overdueFinePolicyRepository = new OverdueFinePolicyRepository(clients);
    eventPublisher = new EventPublisher(clients.pubSubPublishingService());
    loanPageableFetcher = new PageableFetcher<>(loanRepository);
    this.loanScheduledNoticeService = LoanScheduledNoticeService.using(clients);
  }

  public CompletableFuture<Result<Void>> processReminders() {
    return overdueLoansQuery()
      .after(overdueLoansQuery -> loanPageableFetcher
        .processPages(overdueLoansQuery, this::processReminders));
  }

  public CompletableFuture<Result<Void>> processReminders (MultipleRecords<Loan> loans) {
    if (loans.isEmpty()) {
      return ofAsync(() -> null);
    }
    return overdueFinePolicyRepository.findOverdueFinePoliciesForLoans(loans)
      .thenApply(this::getLoansThatAreUpForReminders)
      .thenApply(null);
  }

  private Result<MultipleRecords<Loan>> getLoansThatAreUpForReminders(
    Result<MultipleRecords<Loan>> loansResult) {
    return loansResult.map(loans -> loans.filter(this::loanIsUpForNewReminder));
  }

  private boolean loanIsUpForNewReminder(Loan loan) {
    return loanIsEligibleAndDueForNewReminder(loan);
  }

  private boolean loanIsSubjectToReminders(Loan loan) {
    return loan.getOverdueFinePolicy().isReminderFeesPolicy();
  }

  private boolean loanIsEligibleAndDueForNewReminder(Loan loan) {
    return loanIsSubjectToReminders(loan) && getRemindersPolicy(loan).loanIsDueForReminder(loan);
  }

  private OverdueFinePolicyRemindersPolicy getRemindersPolicy (Loan loan) {
    return loan.getOverdueFinePolicy().getRemindersPolicy();
  }

  private Result<CqlQuery> overdueLoansQuery() {
    final Result<CqlQuery> statusQuery = exactMatch("status.name", "Open");
    final Result<CqlQuery> dueDateQuery = lessThan("dueDate", formatDateTime(ClockUtil.getZonedDateTime()));
    final Result<CqlQuery> claimedReturnedQuery = notEqual("itemStatus", CLAIMED_RETURNED.getValue());
    final Result<CqlQuery> agedToLostQuery = notEqual("itemStatus", AGED_TO_LOST.getValue());
    final Result<CqlQuery> declaredLostQuery = notEqual("itemStatus", DECLARED_LOST.getValue());

    return statusQuery.combine(dueDateQuery, CqlQuery::and)
      .combine(claimedReturnedQuery, CqlQuery::and)
      .combine(agedToLostQuery, CqlQuery::and)
      .combine(declaredLostQuery, CqlQuery::and)
      .map(query -> query.sortBy(ascending("dueDate")));
  }


}

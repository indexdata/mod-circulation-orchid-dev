package org.folio.circulation.domain.policy;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Loan;

import java.lang.invoke.MethodHandles;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getArrayProperty;

public class OverdueFinePolicyRemindersPolicy {

  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  final JsonArray schedule;

  private OverdueFinePolicyRemindersPolicy(JsonArray schedule) {
    this.schedule = schedule;
    log.info("Schedule set to " + schedule.encodePrettily() + " in " + this);
  }
  public static OverdueFinePolicyRemindersPolicy from (JsonObject json) {
    JsonArray schedule = getArrayProperty(json, "reminderSchedule");
    //return schedule.isEmpty() ? null : new OverdueFinePolicyRemindersPolicy(schedule);
    return new OverdueFinePolicyRemindersPolicy(schedule);
  }

  /**
   * WiP. Returns always false for now.
   * @param loan
   * @return
   */
  public boolean loanIsDueForReminder(Loan loan) {
    return false;
  }
}

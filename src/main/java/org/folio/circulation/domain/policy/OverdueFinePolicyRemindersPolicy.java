package org.folio.circulation.domain.policy;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.notice.NoticeFormat;

import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getArrayProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getBigDecimalProperty;

public class OverdueFinePolicyRemindersPolicy {

  private final ReminderSequence sequence;

  public static OverdueFinePolicyRemindersPolicy from (JsonObject json) {
    ReminderSequence sequence =
      ReminderSequence.from(getArrayProperty(json, "reminderSchedule"));
    return new OverdueFinePolicyRemindersPolicy(sequence);
  }

  private OverdueFinePolicyRemindersPolicy(ReminderSequence sequence) {
    this.sequence = sequence;
  }

  public boolean hasReminderSchedule () {
    return !sequence.isEmpty();
  }

  public ReminderSequence getReminderSchedule() {
    return sequence;
  }

  public ReminderSequenceEntry getReminderSequenceEntry(int reminderNumber) {
    return sequence.getEntry(reminderNumber);
  }

  public static class ReminderSequence {
    private final Map<Integer,ReminderSequenceEntry> reminderSequenceEntries;

    private ReminderSequence () {
      reminderSequenceEntries = new HashMap<>();
    }

    /**
     * Creates schedule of reminder entries ordered by sequence numbers starting with 1 (not zero)
     * @param remindersArray JsonArray 'reminderSchedule' from the reminder fees policy
     */
    public static ReminderSequence from (JsonArray remindersArray) {
      ReminderSequence sequence = new ReminderSequence();
      for (int i = 1; i<=remindersArray.size(); i++) {
        sequence.reminderSequenceEntries.put(
          i, ReminderSequenceEntry.from(i, remindersArray.getJsonObject(i-1)));
      }
      return sequence;
    }

    public boolean isEmpty() {
      return reminderSequenceEntries.isEmpty();
    }

    public ReminderSequenceEntry getEntry(int sequenceNumber) {
      if (reminderSequenceEntries.size() >= sequenceNumber) {
        return reminderSequenceEntries.get(sequenceNumber);
      } else {
        return null;
      }
    }

    public String toString() {
      if (isEmpty()) {
        return "No reminder schedule";
      } else {
        StringBuilder builder = new StringBuilder();
        for (ReminderSequenceEntry entry : reminderSequenceEntries.values()) {
          builder.append(entry.toString());
        }
        return builder.toString();
      }
    }

  }

  @Getter
  public static class ReminderSequenceEntry {
    private static final String INTERVAL = "interval";
    private static final String TIME_UNIT_ID = "timeUnitId";
    private static final String REMINDER_FEE = "reminderFee";
    private static final String NOTICE_METHOD_ID = "noticeMethodId";
    private static final String NOTICE_TEMPLATE_ID = "noticeTemplateId";
    private static final String BLOCK_TEMPLATE_ID = "blockTemplateId";

    private final int sequenceNumber;
    private final Period period;
    private final BigDecimal reminderFee;
    private final String noticeMethodId;
    private final String noticeTemplateId;
    private final String blockTemplateId;

    public ReminderSequenceEntry (
      int sequenceNumber,
      Period period,
      BigDecimal reminderFee,
      String noticeMethodId,
      String noticeTemplateId,
      String blockTemplateId) {
      this.sequenceNumber = sequenceNumber;
      this.period = period;
      this.reminderFee = reminderFee;
      this.noticeMethodId = noticeMethodId;
      this.noticeTemplateId= noticeTemplateId;
      this.blockTemplateId = blockTemplateId;
    }
    public static ReminderSequenceEntry from (int sequenceNumber, JsonObject entry) {
      Period period = Period.from(
        entry.getInteger(INTERVAL),
        entry.getString(TIME_UNIT_ID)+"s");
      BigDecimal fee = getBigDecimalProperty(entry,REMINDER_FEE);
      return new ReminderSequenceEntry(
        sequenceNumber, period, fee,
        entry.getString(NOTICE_METHOD_ID),
        entry.getString(NOTICE_TEMPLATE_ID),
        entry.getString(BLOCK_TEMPLATE_ID));
    }

    public NoticeFormat getNoticeFormat () {
      return NoticeFormat.from(StringUtils.capitalize(noticeMethodId));
    }

    public String toString() {
      return "Reminder #" + sequenceNumber +
        ", period: " + period.toString() +
        ", method: " + noticeMethodId + ". ";
    }
  }
}

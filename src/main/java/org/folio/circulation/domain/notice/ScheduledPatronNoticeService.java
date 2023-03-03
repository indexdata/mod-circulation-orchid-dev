package org.folio.circulation.domain.notice;

import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.notice.schedule.ScheduledNoticeConfig;
import org.folio.circulation.domain.representations.logs.NoticeLogContext;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;

public class ScheduledPatronNoticeService extends PatronNoticeService {

  private static final Logger log = LogManager.getLogger(ScheduledPatronNoticeService.class);
  public ScheduledPatronNoticeService(Clients clients) {
    super(clients);
  }

  public CompletableFuture<Result<Void>> sendNotice(ScheduledNoticeConfig noticeConfig,
    String recipientId, JsonObject context, NoticeLogContext noticeLogContext) {
    log.info("Inside schedule patron notice service with event group context {} , notice log context {} ",context,noticeLogContext);
    return sendNotice(new PatronNotice(recipientId, context, noticeConfig), noticeLogContext);
  }
}

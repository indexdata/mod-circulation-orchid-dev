package org.folio.circulation.domain.notice.combiner;

import static java.util.stream.Collectors.collectingAndThen;
import static org.folio.circulation.domain.notice.TemplateContextUtil.createUserContext;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.notice.PatronNoticeEvent;
import org.folio.circulation.domain.representations.logs.NoticeLogContext;

import io.vertx.core.json.JsonObject;

public class LoanNoticeContextCombiner implements NoticeContextCombiner {

  private static final Logger log = LogManager.getLogger(LoanNoticeContextCombiner.class);
  @Override
  public JsonObject buildCombinedNoticeContext(Collection<PatronNoticeEvent> events) {
    log.info("Inside buildCombinedNoticeContext events {}",events);
    log.info("Inside buildCombinedNoticeContext user {}",events.iterator().next().getUser());
    log.info("Inside buildCombinedNoticeContext items {}",events.iterator().next().getItem());
    return events.stream()
      .map(PatronNoticeEvent::getNoticeContext)
      .filter(Objects::nonNull)
      .collect(collectingAndThen(
        Collectors.toList(),
        contexts -> new JsonObject()
          .put("user", createUserContext(events.iterator().next().getUser()))
          .put("loans", contexts)
          .put("item.loanType",events.iterator().next().getItem().getLoanTypeName())
      ));
  }

  @Override
  public NoticeLogContext buildCombinedNoticeLogContext(Collection<PatronNoticeEvent> events) {
    return events.stream()
      .map(PatronNoticeEvent::getNoticeLogContext)
      .filter(Objects::nonNull)
      .map(NoticeLogContext::getItems)
      .flatMap(Collection::stream)
      .filter(Objects::nonNull)
      .collect(collectingAndThen(
        Collectors.toList(),
        items -> new NoticeLogContext()
          .withUser(events.iterator().next().getUser())
          .withItems(items)
      ));
  }
}

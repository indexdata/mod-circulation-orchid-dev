package org.folio.circulation.support;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandles;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.stream.Collectors;

public class CqlHelper {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private CqlHelper() { }

  public static String multipleRecordsCqlQuery(Collection<String> recordIds) {
    if(recordIds.isEmpty()) {
      return null;
    }
    else {
      String query = String.format("id==(%s)", recordIds.stream()
        .map(String::toString)
        .distinct()
        .collect(Collectors.joining(" or ")));

      try {
        return URLEncoder.encode(query, "UTF-8");

      } catch (UnsupportedEncodingException e) {
        log.error(String.format("Cannot encode query %s", query));
        return null;
      }
    }
  }

  public static String buildIsValidUserProxyQuery(String proxyUserId, String sponsorUserId){
    //we got the id of the proxy and sponsor user IDs, look for a record that indicates
    // there is a proxy relationship that is active and the expiration date is in the future
    if(proxyUserId != null) {
      DateTime expDate = new DateTime(DateTimeZone.UTC);
      String validateProxyQuery ="proxyUserId="+ proxyUserId
          +" and userId="+sponsorUserId
          +" and meta.status=Active"
          +" and meta.expirationDate>"+expDate.toString().trim();
      try {
        return URLEncoder.encode(validateProxyQuery, String.valueOf(StandardCharsets.UTF_8));
      } catch (UnsupportedEncodingException e) {
        log.error("Failed to encode query for proxies");
        return null;
      }
    }
    return null;
  }
}

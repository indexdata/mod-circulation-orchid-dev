package api.support.builders;

import io.vertx.core.json.JsonObject;

import java.util.UUID;

public class LoanPolicyBuilder extends JsonBuilder implements Builder {
  private final UUID id;
  private final String name;
  private final String description;
  private final String profile;
  private final JsonObject loanPeriod;

  public LoanPolicyBuilder() {
    this(null, "Example Loan Policy", "An example loan policy");
  }

  private LoanPolicyBuilder(UUID id, String name, String description) {
    this.id = id;
    this.name = name;
    this.description = description;
    profile = "Rolling";
    loanPeriod = createPeriod(Period.weeks(3));
  }

  @Override
  public JsonObject create() {
    JsonObject request = new JsonObject();

    if(id != null) {
      request.put("id", id.toString());
    }

    request.put("name", this.name);
    request.put("description", this.description);
    request.put("loanable", true);
    request.put("renewable", true);

    JsonObject loansPolicy = new JsonObject();

    loansPolicy.put("profileId", profile);
    loansPolicy.put("period", loanPeriod);
    loansPolicy.put("closedLibraryDueDateManagementId", "KEEP_CURRENT_DATE");

    request.put("loansPolicy", loansPolicy);

    JsonObject renewalsPolicy = new JsonObject();

    renewalsPolicy.put("unlimited", true);
    renewalsPolicy.put("renewFromId", "CURRENT_DUE_DATE");
    renewalsPolicy.put("differentPeriod", false);

    request.put("renewalsPolicy", renewalsPolicy);

    return request;
  }

  public LoanPolicyBuilder withId(UUID id) {
    return new LoanPolicyBuilder(id, this.name, this.description);
  }

  public LoanPolicyBuilder withName(String name) {
    return new LoanPolicyBuilder(this.id, name, this.description);
  }

  public LoanPolicyBuilder withDescription(String description) {
    return new LoanPolicyBuilder(this.id, this.name, description);
  }

  private JsonObject createPeriod(Period period) {
    JsonObject representation = new JsonObject();

    representation.put("duration", period.duration);
    representation.put("intervalId", period.interval);

    return representation;
  }
}

package api.support.builders;

import api.APITestSuite;
import io.vertx.core.json.JsonObject;

import java.util.Optional;
import java.util.UUID;

public class ItemBuilder extends JsonBuilder implements Builder {

  public static final String AVAILABLE = "Available";
  public static final String CHECKED_OUT = "Checked out";
  public static final String CHECKED_OUT_HELD = "Checked out - Held";
  public static final String CHECKED_OUT_RECALLED = "Checked out - Recalled";
  public static final String AWAITING_PICKUP = "Awaiting pickup";

  private final UUID id;
  private final UUID holdingId;
  private final String barcode;
  private final String status;
  private final UUID materialTypeId;
  private final UUID temporaryLocationId;
  private final UUID permanentLoanTypeId;
  private final UUID temporaryLoanTypeId;

  public ItemBuilder() {
    this(UUID.randomUUID(), null, "565578437802", AVAILABLE,
      null, null, null);
  }

  private ItemBuilder(
    UUID id,
    UUID holdingId,
    String barcode,
    String status,
    UUID temporaryLocationId,
    UUID materialTypeId,
    UUID temporaryLoanTypeId) {

    this.id = id;
    this.holdingId = holdingId;
    this.barcode = barcode;
    this.status = status;
    this.temporaryLocationId = temporaryLocationId;
    this.materialTypeId = materialTypeId;
    this.temporaryLoanTypeId = temporaryLoanTypeId;

    //TODO: Figure out a better way of injecting defaults in different situations
    permanentLoanTypeId = Optional.ofNullable(APITestSuite.canCirculateLoanTypeId())
      .orElse(null);
  }

  public JsonObject create() {
    JsonObject itemRequest = new JsonObject();

    put(itemRequest, "id", id);
    put(itemRequest, "barcode", barcode);
    put(itemRequest, "holdingsRecordId", holdingId);
    put(itemRequest, "materialTypeId", materialTypeId);
    put(itemRequest, "permanentLoanTypeId", permanentLoanTypeId);
    put(itemRequest, "temporaryLoanTypeId", temporaryLoanTypeId);
    put(itemRequest, "temporaryLocationId", temporaryLocationId);
    put(itemRequest, "status", status, new JsonObject().put("name", status));

    return itemRequest;
  }

  public ItemBuilder checkOut() {
    return withStatus(CHECKED_OUT);
  }

  public ItemBuilder available() {
    return withStatus(AVAILABLE);
  }

  private ItemBuilder withStatus(String status) {
    return new ItemBuilder(
      this.id,
      this.holdingId,
      this.barcode,
      status,
      this.temporaryLocationId,
      this.materialTypeId,
      this.temporaryLoanTypeId);
  }

  public ItemBuilder withBarcode(String barcode) {
    return new ItemBuilder(
      this.id,
      this.holdingId,
      barcode,
      this.status,
      this.temporaryLocationId,
      this.materialTypeId,
      this.temporaryLoanTypeId);
  }

  public ItemBuilder withNoBarcode() {
    return withBarcode(null);
  }

  public ItemBuilder withNoTemporaryLocation() {
    return withTemporaryLocation(null);
  }

  public ItemBuilder withTemporaryLocation(UUID temporaryLocationId) {
    return new ItemBuilder(
      this.id,
      this.holdingId,
      this.barcode,
      this.status,
      temporaryLocationId,
      this.materialTypeId,
      this.temporaryLoanTypeId);
  }

  public ItemBuilder forHolding(UUID holdingId) {
    return new ItemBuilder(
      this.id,
      holdingId,
      this.barcode,
      this.status,
      this.temporaryLocationId,
      this.materialTypeId,
      this.temporaryLoanTypeId);
  }

  public ItemBuilder withMaterialType(UUID materialTypeId) {
    return new ItemBuilder(
      this.id,
      this.holdingId,
      this.barcode,
      this.status,
      this.temporaryLocationId,
      materialTypeId,
      this.temporaryLoanTypeId);
  }

  public ItemBuilder withTemporaryLoanType(UUID loanTypeId) {
    return new ItemBuilder(
      this.id,
      this.holdingId,
      this.barcode,
      this.status,
      this.temporaryLocationId,
      this.materialTypeId,
      loanTypeId);

  }
}

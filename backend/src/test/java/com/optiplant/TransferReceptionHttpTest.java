package com.optiplant;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * RF-0036/RF-0037: the destination operator confirms the single arrival of a dispatch; what was received leaves transit
 * and enters the destination at the frozen dispatch cost, once, whether repeated with the same key, with another key or
 * concurrently; a shortage (including a zero arrival) leaves the transfer «Recibida con discrepancia» with its pending
 * transit. RF-0038/RF-0039: the shortage opens a linked alert that origin/destination managers resolve with a treatment
 * without changing anything else; the origin manager writes off the pending transit apart, once; a resend is a new
 * transfer referencing the shortage.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"app.admin-code=reception-initial", "app.admin-password=private initial password"})
class TransferReceptionHttpTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.11")
        .withInitScript("db/test_roles.sql");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", () -> "optiplant_test_runtime");
        r.add("spring.datasource.password", () -> "test-runtime-not-for-deployment");
        r.add("spring.flyway.user", POSTGRES::getUsername);
        r.add("spring.flyway.password", POSTGRES::getPassword);
    }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired JsonMapper json;
    /** a: origin, b: destination, c: an unrelated branch. */
    UUID a, b, c, product, other;
    String suffix;
    Browser admin, originOperator, originManager, destinationOperator, destinationManager, otherOperator, otherManager;

    @BeforeEach void data() throws Exception {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        a = branch("a");
        b = branch("b");
        c = branch("c");
        product = product("Maceta", a, b, c);
        other = product("Sustrato", a, b, c);
        admin = account("ADMIN", null);
        originOperator = account("OPERATOR", a);
        originManager = account("MANAGER", a);
        destinationOperator = account("OPERATOR", b);
        destinationManager = account("MANAGER", b);
        otherOperator = account("OPERATOR", c);
        otherManager = account("MANAGER", c);
        // RF-0036-CA-02 / RF-0037-CA-02: 10 units at cost 9.
        stock(product, "10", "9");
        stock(other, "4", "5");
        assertThat(admin.post("/logistics/routes", Map.of("code", "AB" + suffix, "origin", a.toString(), "destination",
            b.toString(), "priority", "HIGH")).statusCode()).isEqualTo(201);
    }

    // ---------------------------------------------------------------- RF-0036 complete reception

    @Test void aCompleteReceptionEntersTheDestinationOnceAtTheFrozenCost() throws Exception {
        String id = inTransit(line(product, "unidad", "10"));
        // Before the arrival the contract sends its fields as null, never omits them.
        JsonNode pending = read(destinationOperator.get("/transfers/" + id));
        assertThat(pending.has("arrival") && pending.get("arrival").isNull()).isTrue();
        assertThat(pending.has("discrepancy") && pending.get("discrepancy").isNull()).isTrue();
        assertThat(pending.at("/lines/0/receivedQuantity").isNull()).isTrue();
        assertThat(pending.at("/lines/0/inTransitBaseQuantity").asText()).isEqualTo("10.000");
        String key = UUID.randomUUID().toString();

        HttpResponse<String> done = destinationOperator.post("/transfers/" + id + "/receive", quantities(1, "10"), key);
        assertThat(done.statusCode()).as(done.body()).isEqualTo(200);
        JsonNode transfer = read(done);
        assertThat(transfer.get("status").asText()).isEqualTo("RECEIVED_COMPLETE");
        assertThat(transfer.at("/arrival/actor/fullName").asText()).isEqualTo("Persona OPERATOR");
        assertThat(transfer.at("/lines/0/receivedQuantity").asText()).isEqualTo("10.000");
        assertThat(transfer.at("/lines/0/missingQuantity").asText()).isEqualTo("0.000");
        assertThat(transfer.at("/lines/0/inTransitBaseQuantity").asText()).isEqualTo("0.000");
        assertThat(transfer.get("discrepancy").isNull()).isTrue();
        assertThat(transfer.at("/events/4/toStatus").asText()).isEqualTo("RECEIVED_COMPLETE");

        // RF-0036-CA-01: one entry in the destination with date, actor, reason and quantity.
        assertThat(balance(b, product)).isEqualTo("10.000|90.000000000000000000|9.000000");
        assertThat(jdbc.queryForList("SELECT m.direction||':'||m.base_qty||':'||m.historical_base_cost||':'||o.reason "
            + "FROM inventory_movement m JOIN inventory_operation o ON o.id=m.operation_id WHERE o.kind='ARRIVAL' "
            + "AND m.branch_id=(SELECT id FROM identity_branch WHERE external_id=?)", String.class, b))
            .containsExactly("IN:10.000:9.000000:Recepción de la transferencia " + transfer.get("code").asText() + " desde Sucursal a");
        assertThat(transit(id)).containsExactly("0.000|0.000000000000000000|0.000000000000000000");

        // RF-0036-CA-02: repeating the confirmation replays it; another attempt is refused; nothing is applied twice.
        Snapshot after = snapshot(id);
        HttpResponse<String> replay = destinationOperator.post("/transfers/" + id + "/receive", quantities(1, "10"), key);
        assertThat(replay.statusCode()).isEqualTo(200);
        assertThat(read(replay)).isEqualTo(transfer);
        HttpResponse<String> again = destinationOperator.post("/transfers/" + id + "/receive", quantities(1, "10"));
        assertError(again, 409, "ARRIVAL_ALREADY_CONFIRMED");
        assertThat(read(again).get("detail").asText()).contains("nueva transferencia");
        assertThat(snapshot(id)).isEqualTo(after);
        assertThat(balance(b, product)).isEqualTo("10.000|90.000000000000000000|9.000000");
        assertThat(read(originManager.get("/transfers/" + id)).get("status").asText()).isEqualTo("RECEIVED_COMPLETE");
    }

    @Test void concurrentConfirmationsWithDifferentKeysReceiveOnce() throws Exception {
        String id = inTransit(line(product, "unidad", "10"), line(other, "unidad", "4"));
        List<Integer> statuses = race(
            () -> destinationOperator.post("/transfers/" + id + "/receive", quantities(1, "10", 2, "4")).statusCode(),
            () -> destinationOperator.post("/transfers/" + id + "/receive", quantities(1, "7", 2, "4")).statusCode());
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM logistics_arrival a JOIN logistics_dispatch d ON d.id=a.dispatch_id "
            + "JOIN transfers_transfer t ON t.id=d.transfer_id WHERE t.external_id=?::uuid", Integer.class, id)).isEqualTo(1);
        assertThat(arrivalMovements(id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transfers_event e JOIN transfers_transfer t ON t.id=e.transfer_id "
            + "WHERE t.external_id=?::uuid AND e.from_status='IN_TRANSIT'", Integer.class, id)).isEqualTo(1);
        // Whichever won, transit and destination add up to what was dispatched.
        List<String> transit = transit(id);
        String received = balance(b, product).split("\\|")[0];
        assertThat(new java.math.BigDecimal(received).add(new java.math.BigDecimal(transit.getFirst().split("\\|")[0])))
            .isEqualByComparingTo("10");
    }

    // ---------------------------------------------------------------- RF-0037 partial and zero receptions

    @Test void aPartialReceptionKeepsTheShortageInTransitWithALinkedAlert() throws Exception {
        String id = inTransit(line(product, "unidad", "10"));

        HttpResponse<String> done = destinationOperator.post("/transfers/" + id + "/receive", quantities(1, "7"));
        assertThat(done.statusCode()).as(done.body()).isEqualTo(200);
        JsonNode transfer = read(done);
        // RF-0037-CA-01 / CA-03: 7 received and 3 missing, linked to the dispatch, «Recibida con discrepancia».
        assertThat(transfer.get("status").asText()).isEqualTo("RECEIVED_DISCREPANCY");
        assertThat(transfer.at("/lines/0/dispatchedQuantity").asText()).isEqualTo("10.000");
        assertThat(transfer.at("/lines/0/receivedQuantity").asText()).isEqualTo("7.000");
        assertThat(transfer.at("/lines/0/missingQuantity").asText()).isEqualTo("3.000");
        assertThat(transfer.at("/lines/0/inTransitBaseQuantity").asText()).isEqualTo("3.000");
        // RF-0038-CA-01: an open alert linked to the transfer identifies the difference.
        assertThat(transfer.at("/discrepancy/status").asText()).isEqualTo("OPEN");
        assertThat(transfer.at("/discrepancy/treatment").isNull()).isTrue();
        assertThat(jdbc.queryForList("SELECT xl.missing_base_qty::text FROM logistics_discrepancy_line xl "
            + "JOIN logistics_discrepancy x ON x.id=xl.discrepancy_id WHERE x.external_id=?::uuid", String.class,
            transfer.at("/discrepancy/externalId").asText())).containsExactly("3.000");

        // RF-0037-CA-02: origin down 10, destination up 7, transit keeps 3 at value 27; no loss or replenishment.
        assertThat(balance(a, product)).startsWith("0.000|");
        assertThat(balance(b, product)).isEqualTo("7.000|63.000000000000000000|9.000000");
        assertThat(transit(id)).containsExactly("3.000|27.000000000000000000|0.000000000000000000");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_operation WHERE kind='TRANSIT_LOSS' AND discrepancy_id="
            + "(SELECT id FROM logistics_discrepancy WHERE external_id=?::uuid)", Integer.class,
            transfer.at("/discrepancy/externalId").asText())).isZero();

        // A second reception, of 4, 1 or 3, is refused without changing stock, value, transit or receptions.
        Snapshot after = snapshot(id);
        for (String quantity : List.of("4", "1", "3")) {
            assertError(destinationOperator.post("/transfers/" + id + "/receive", quantities(1, quantity)), 409, "ARRIVAL_ALREADY_CONFIRMED");
        }
        assertThat(snapshot(id)).isEqualTo(after);
        JsonNode seen = read(originOperator.get("/transfers/" + id));
        assertThat(seen.get("status").asText()).isEqualTo("RECEIVED_DISCREPANCY");
        assertThat(seen.at("/lines/0/missingQuantity").asText()).isEqualTo("3.000");
    }

    @Test void aZeroArrivalCreatesNoMovementAndLeavesEverythingInTransit() throws Exception {
        String id = inTransit(line(product, "unidad", "10"));
        int movements = jdbc.queryForObject("SELECT count(*) FROM inventory_movement", Integer.class);

        HttpResponse<String> done = destinationOperator.post("/transfers/" + id + "/receive", quantities(1, "0"));
        assertThat(done.statusCode()).as(done.body()).isEqualTo(200);
        JsonNode transfer = read(done);
        // RF-0037-CA-04: no physical movement, 0 received and 10 missing, pending transit and a linked alert.
        assertThat(transfer.get("status").asText()).isEqualTo("RECEIVED_DISCREPANCY");
        assertThat(transfer.at("/lines/0/receivedQuantity").asText()).isEqualTo("0.000");
        assertThat(transfer.at("/lines/0/missingQuantity").asText()).isEqualTo("10.000");
        assertThat(transfer.at("/discrepancy/status").asText()).isEqualTo("OPEN");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_movement", Integer.class)).isEqualTo(movements);
        assertThat(arrivalOperations(id)).isEqualTo(1);
        assertThat(balance(b, product)).isEqualTo("none");
        assertThat(transit(id)).containsExactly("10.000|90.000000000000000000|0.000000000000000000");
        assertError(destinationOperator.post("/transfers/" + id + "/receive", quantities(1, "10")), 409, "ARRIVAL_ALREADY_CONFIRMED");
        assertThat(transit(id)).containsExactly("10.000|90.000000000000000000|0.000000000000000000");
    }

    @Test void onlyTheDestinationOperatorReceivesValidQuantitiesOfATransferInTransit() throws Exception {
        // Line 2 (one box) is prepared at zero: it is dispatched with zero quantity.
        String id = inTransit(List.of(line(product, "unidad", "10"), line(other, "caja", "1"), line(other, "unidad", "4")),
            1, "10", 2, "0", 3, "4");
        Snapshot before = snapshot(id);

        for (Browser profile : List.of(destinationManager, originManager, admin)) {
            assertThat(profile.post("/transfers/" + id + "/receive", quantities(1, "10", 2, "0", 3, "4")).statusCode()).isEqualTo(403);
        }
        assertError(originOperator.post("/transfers/" + id + "/receive", quantities(1, "10", 2, "0", 3, "4")), 403, "TRANSFER_FORBIDDEN");
        assertError(otherOperator.post("/transfers/" + id + "/receive", quantities(1, "10", 2, "0", 3, "4")), 404, "TRANSFER_NOT_FOUND");
        // Surpluses are rejected before confirming, and one bad line rejects the whole arrival.
        HttpResponse<String> surplus = destinationOperator.post("/transfers/" + id + "/receive", quantities(1, "10", 2, "0", 3, "5"));
        assertError(surplus, 409, "RECEIVED_EXCEEDS_DISPATCHED");
        assertThat(read(surplus).get("detail").asText()).startsWith("Línea 3: se despacharon 4 unidad");
        assertError(destinationOperator.post("/transfers/" + id + "/receive", quantities(1, "10", 2, "1", 3, "4")), 409,
            "RECEIVED_EXCEEDS_DISPATCHED");
        assertError(destinationOperator.post("/transfers/" + id + "/receive", quantities(1, "10", 3, "4")), 400, "VALIDATION_FAILED");
        assertError(destinationOperator.post("/transfers/" + id + "/receive", quantities(1, "9.5", 2, "0", 3, "4")), 400,
            "VALIDATION_FAILED");
        assertError(destinationOperator.post("/transfers/" + id + "/receive", quantities(1, "10", 2, "0", 3, "4", 4, "1")), 400,
            "VALIDATION_FAILED");
        assertError(destinationOperator.post("/transfers/" + id + "/receive", quantities(1, "-1", 2, "0", 3, "4")), 400,
            "VALIDATION_FAILED");
        assertError(destinationOperator.post("/transfers/" + id + "/receive", Map.of("lines", List.of(), "arrivedAt", "x")), 400,
            "VALIDATION_FAILED");
        assertThat(snapshot(id)).isEqualTo(before);

        // An approved transfer is not received before its dispatch.
        String approved = approved(line(other, "unidad", "1"));
        assertError(destinationOperator.post("/transfers/" + approved + "/receive", quantities(1, "1")), 409, "TRANSFER_STATUS_CONFLICT");

        // The valid attempt still succeeds: a line dispatched at zero receives zero and is not a shortage.
        HttpResponse<String> done = destinationOperator.post("/transfers/" + id + "/receive", quantities(1, "10", 2, "0", 3, "4"));
        assertThat(done.statusCode()).as(done.body()).isEqualTo(200);
        assertThat(read(done).get("status").asText()).isEqualTo("RECEIVED_COMPLETE");
        assertThat(arrivalMovements(id)).isEqualTo(2);
    }

    @Test void aPartialArrivalCarriesItsShareOfTheResidualAndTheLossTheRest() throws Exception {
        // 3 units valued 1 leave at 0.333333 with residual 0.000001 (RF-0035); receive 2 of them.
        jdbc.update("UPDATE inventory_balance SET physical_qty=3,inventory_value=1,average_base_cost=0.333333 "
            + "WHERE branch_id=(SELECT id FROM identity_branch WHERE external_id=?) AND product_id=(SELECT id FROM catalog_product "
            + "WHERE external_id=?)", a, other);
        String id = inTransit(line(other, "unidad", "3"));
        assertThat(transit(id)).containsExactly("3.000|0.999999000000000000|0.000001000000000000");

        assertThat(destinationOperator.post("/transfers/" + id + "/receive", quantities(1, "2")).statusCode()).isEqualTo(200);
        // Base 2 × 0.333333 and two thirds of the residual enter the destination; the rest stays in transit.
        assertThat(balance(b, other)).isEqualTo("2.000|0.666666666666666667|0.333333");
        assertThat(transit(id)).containsExactly("1.000|0.333333000000000000|0.000000333333333333");
        assertThat(originManager.post("/transfers/" + id + "/discrepancy/transit-loss", loss()).statusCode()).isEqualTo(200);
        assertThat(transit(id)).containsExactly("0.000|0.000000000000000000|0.000000000000000000");
        // Value is conserved: what entered the destination plus what was written off is what left the origin.
        assertThat(jdbc.queryForObject("SELECT sum(e.base_value_delta+e.rounding_value_delta) FROM inventory_transit_entry e "
            + "JOIN inventory_transit it ON it.id=e.transit_id JOIN logistics_dispatch_line dl ON dl.id=it.dispatch_line_id "
            + "JOIN transfers_line l ON l.id=dl.transfer_line_id JOIN transfers_transfer t ON t.id=l.transfer_id "
            + "WHERE t.external_id=?::uuid AND e.entry_kind IN ('ARRIVAL','LOSS')", java.math.BigDecimal.class, id))
            .isEqualByComparingTo("-1");
    }

    // ---------------------------------------------------------------- RF-0038 shortage alert

    @Test void managersOfTheTransferResolveTheAlertWithATreatmentWithoutChangingAnythingElse() throws Exception {
        String id = partial();
        Snapshot before = snapshot(id);
        String alert = alertState(id);

        // RF-0038-CA-02: without a treatment the resolution is refused; operators, the administrator and other managers cannot resolve.
        assertError(originManager.post("/transfers/" + id + "/discrepancy/resolve", Map.of()), 400, "TREATMENT_REQUIRED");
        assertError(originManager.post("/transfers/" + id + "/discrepancy/resolve", Map.of("treatment", "LOSS")), 400, "VALIDATION_FAILED");
        for (Browser profile : List.of(destinationOperator, originOperator, admin)) {
            assertThat(profile.post("/transfers/" + id + "/discrepancy/resolve", Map.of("treatment", "ADJUSTMENT")).statusCode())
                .isEqualTo(403);
        }
        assertError(otherManager.post("/transfers/" + id + "/discrepancy/resolve", Map.of("treatment", "ADJUSTMENT")), 404,
            "TRANSFER_NOT_FOUND");
        assertThat(alertState(id)).isEqualTo(alert);
        assertThat(snapshot(id)).isEqualTo(before);

        HttpResponse<String> resolved = destinationManager.post("/transfers/" + id + "/discrepancy/resolve", Map.of("treatment", "RESEND"));
        assertThat(resolved.statusCode()).as(resolved.body()).isEqualTo(200);
        JsonNode transfer = read(resolved);
        assertThat(transfer.at("/discrepancy/status").asText()).isEqualTo("RESOLVED");
        assertThat(transfer.at("/discrepancy/treatment").asText()).isEqualTo("RESEND");
        assertThat(transfer.at("/discrepancy/resolvedBy/fullName").asText()).isEqualTo("Persona MANAGER");
        assertThat(transfer.at("/discrepancy/resolvedAt").isTextual()).isTrue();
        // The transfer stays «Recibida con discrepancia» with the same quantities, value, movements and balances.
        assertThat(transfer.get("status").asText()).isEqualTo("RECEIVED_DISCREPANCY");
        assertThat(transfer.at("/lines/0/missingQuantity").asText()).isEqualTo("3.000");
        assertThat(snapshot(id)).isEqualTo(before);
        assertThat(transit(id)).containsExactly("3.000|27.000000000000000000|0.000000000000000000");

        // A single resolution: it is not changed afterwards.
        assertError(originManager.post("/transfers/" + id + "/discrepancy/resolve", Map.of("treatment", "CLAIM")), 409,
            "DISCREPANCY_ALREADY_RESOLVED");
        assertThat(read(originOperator.get("/transfers/" + id)).at("/discrepancy/treatment").asText()).isEqualTo("RESEND");
        // A transfer without shortage has no alert to resolve.
        String complete = inTransit(line(other, "unidad", "1"));
        assertThat(destinationOperator.post("/transfers/" + complete + "/receive", quantities(1, "1")).statusCode()).isEqualTo(200);
        assertError(originManager.post("/transfers/" + complete + "/discrepancy/resolve", Map.of("treatment", "CLAIM")), 409,
            "NO_DISCREPANCY");
    }

    @Test void theAlertsSectionListsOpenAndResolvedShortagesWithinEachScope() throws Exception {
        String open = partial();
        String resolved = partial();
        assertThat(originManager.post("/transfers/" + resolved + "/discrepancy/resolve", Map.of("treatment", "CLAIM")).statusCode())
            .isEqualTo(200);
        String complete = inTransit(line(other, "unidad", "1"));
        assertThat(destinationOperator.post("/transfers/" + complete + "/receive", quantities(1, "1")).statusCode()).isEqualTo(200);

        assertThat(ids(destinationOperator.get("/transfers?discrepancy=OPEN"))).containsExactly(open);
        assertThat(ids(originManager.get("/transfers?discrepancy=RESOLVED"))).containsExactly(resolved);
        assertThat(ids(destinationManager.get("/transfers?discrepancy=ALL"))).containsExactly(resolved, open);
        assertThat(ids(admin.get("/transfers?discrepancy=ALL&branch=" + a))).containsExactly(resolved, open);
        assertThat(ids(admin.get("/transfers?discrepancy=OPEN"))).contains(open).doesNotContain(resolved, complete);
        assertThat(ids(otherOperator.get("/transfers?discrepancy=ALL"))).isEmpty();
        assertThat(otherOperator.get("/transfers?discrepancy=ALL&branch=" + a).statusCode()).isEqualTo(403);
        assertError(destinationOperator.get("/transfers?discrepancy=CLOSED"), 400, "VALIDATION_FAILED");
    }

    // ---------------------------------------------------------------- RF-0039 treatment, transit loss and resend

    @Test void aClaimMovesNothingAndTheOriginManagerWritesOffThePendingTransitOnce() throws Exception {
        String id = partial();
        assertThat(destinationManager.post("/transfers/" + id + "/discrepancy/resolve", Map.of("treatment", "CLAIM")).statusCode())
            .isEqualTo(200);
        // RF-0039-CA-02: the claim mark changes no pending balance.
        assertThat(transit(id)).containsExactly("3.000|27.000000000000000000|0.000000000000000000");
        Snapshot before = snapshot(id);

        for (Browser profile : List.of(destinationOperator, originOperator, admin)) {
            assertThat(profile.post("/transfers/" + id + "/discrepancy/transit-loss", loss()).statusCode()).isEqualTo(403);
        }
        assertError(destinationManager.post("/transfers/" + id + "/discrepancy/transit-loss", loss()), 403, "TRANSFER_FORBIDDEN");
        assertError(originManager.post("/transfers/" + id + "/discrepancy/transit-loss", Map.of("reason", "Robo")), 400, "VALIDATION_FAILED");
        assertError(originManager.post("/transfers/" + id + "/discrepancy/transit-loss", Map.of("reason", " ", "evidence", "Acta")), 400,
            "VALIDATION_FAILED");
        assertThat(snapshot(id)).isEqualTo(before);
        assertThat(transit(id)).containsExactly("3.000|27.000000000000000000|0.000000000000000000");

        String key = UUID.randomUUID().toString();
        HttpResponse<String> done = originManager.post("/transfers/" + id + "/discrepancy/transit-loss", loss(), key);
        assertThat(done.statusCode()).as(done.body()).isEqualTo(200);
        JsonNode transfer = read(done);
        assertThat(transfer.at("/discrepancy/transitLoss/evidence").asText()).isEqualTo("Acta 15 del transportista");
        assertThat(transfer.at("/discrepancy/transitLoss/authorizedBy/fullName").asText()).isEqualTo("Persona MANAGER");
        assertThat(transfer.at("/lines/0/inTransitBaseQuantity").asText()).isEqualTo("0.000");
        // Transit and value are written off once; physical balances, movements and the state do not change.
        assertThat(transit(id)).containsExactly("0.000|0.000000000000000000|0.000000000000000000");
        assertThat(transfer.get("status").asText()).isEqualTo("RECEIVED_DISCREPANCY");
        assertThat(balances()).isEqualTo(before.balances());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_movement", Integer.class)).isEqualTo(before.movements());
        assertThat(jdbc.queryForList("SELECT e.entry_kind||'|'||e.qty_delta||'|'||e.base_value_delta FROM inventory_transit_entry e "
            + "JOIN inventory_operation o ON o.id=e.operation_id WHERE o.kind='TRANSIT_LOSS' AND o.discrepancy_id="
            + "(SELECT id FROM logistics_discrepancy WHERE external_id=?::uuid)", String.class,
            transfer.at("/discrepancy/externalId").asText())).containsExactly("LOSS|-3.000|-27.000000000000000000");

        // Replaying the key returns the same result; another write-off finds nothing pending.
        assertThat(read(originManager.post("/transfers/" + id + "/discrepancy/transit-loss", loss(), key))).isEqualTo(transfer);
        assertError(originManager.post("/transfers/" + id + "/discrepancy/transit-loss", loss()), 409, "TRANSIT_ALREADY_SETTLED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_operation WHERE kind='TRANSIT_LOSS'", Integer.class)).isEqualTo(1);
    }

    @Test void aResendIsANewTransferReferencingTheShortageAndNeverAnotherArrival() throws Exception {
        String id = partial();
        String shortage = read(destinationOperator.get("/transfers/" + id)).at("/discrepancy/externalId").asText();

        HttpResponse<String> resend = destinationOperator.post("/transfers", Map.of("origin", a.toString(), "sourceDiscrepancy", shortage,
            "lines", List.of(line(product, "unidad", "3"))));
        assertThat(resend.statusCode()).as(resend.body()).isEqualTo(201);
        JsonNode created = read(resend);
        assertThat(created.get("status").asText()).isEqualTo("REQUESTED");
        assertThat(created.at("/sourceDiscrepancy/externalId").asText()).isEqualTo(shortage);
        assertThat(created.at("/sourceDiscrepancy/transferExternalId").asText()).isEqualTo(id);
        JsonNode original = read(originManager.get("/transfers/" + id));
        assertThat(original.at("/discrepancy/resends/0/code").asText()).isEqualTo(created.get("code").asText());
        // The original keeps its state and pending transit: the resend settles nothing.
        assertThat(original.get("status").asText()).isEqualTo("RECEIVED_DISCREPANCY");
        assertThat(transit(id)).containsExactly("3.000|27.000000000000000000|0.000000000000000000");

        int transfers = jdbc.queryForObject("SELECT count(*) FROM transfers_transfer", Integer.class);
        assertError(destinationOperator.post("/transfers", Map.of("origin", c.toString(), "sourceDiscrepancy", shortage,
            "lines", List.of(line(product, "unidad", "3")))), 409, "SOURCE_DISCREPANCY_MISMATCH");
        assertError(otherOperator.post("/transfers", Map.of("origin", a.toString(), "sourceDiscrepancy", shortage,
            "lines", List.of(line(product, "unidad", "3")))), 409, "SOURCE_DISCREPANCY_MISMATCH");
        assertError(destinationOperator.post("/transfers", Map.of("origin", a.toString(), "sourceDiscrepancy", UUID.randomUUID().toString(),
            "lines", List.of(line(product, "unidad", "3")))), 404, "DISCREPANCY_NOT_FOUND");
        assertError(destinationOperator.post("/transfers", Map.of("origin", a.toString(), "sourceDiscrepancy", "TR-1",
            "lines", List.of(line(product, "unidad", "3")))), 400, "VALIDATION_FAILED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transfers_transfer", Integer.class)).isEqualTo(transfers);
    }

    // ---------------------------------------------------------------- helpers

    /** A one-line transfer of 10 units at cost 9, received at 7. */
    String partial() throws Exception {
        jdbc.update("UPDATE inventory_balance SET physical_qty=10,inventory_value=90,average_base_cost=9 "
            + "WHERE branch_id=(SELECT id FROM identity_branch WHERE external_id=?) AND product_id=(SELECT id FROM catalog_product "
            + "WHERE external_id=?)", a, product);
        String id = inTransit(line(product, "unidad", "10"));
        HttpResponse<String> done = destinationOperator.post("/transfers/" + id + "/receive", quantities(1, "7"));
        assertThat(done.statusCode()).as(done.body()).isEqualTo(200);
        return id;
    }

    /** Requested and approved, prepared at the requested quantities. */
    String approved(Map<?, ?>... lines) throws Exception {
        List<Object> prepared = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            prepared.add(i + 1);
            prepared.add(lines[i].get("quantity"));
        }
        return approved(List.of(lines), prepared.toArray());
    }

    String approved(List<Map<?, ?>> lines, Object... prepared) throws Exception {
        HttpResponse<String> created = destinationOperator.post("/transfers", Map.of("origin", a.toString(), "lines", lines));
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        String id = read(created).get("externalId").asText();
        HttpResponse<String> prepare = originOperator.post("/transfers/" + id + "/prepare", quantities(prepared));
        assertThat(prepare.statusCode()).as(prepare.body()).isEqualTo(200);
        assertThat(originManager.post("/transfers/" + id + "/approve", Map.of()).statusCode()).isEqualTo(200);
        return id;
    }

    /** Requested, prepared at the requested quantities, approved and dispatched. */
    String inTransit(Map<?, ?>... lines) throws Exception {
        return dispatched(approved(lines));
    }

    String inTransit(List<Map<?, ?>> lines, Object... prepared) throws Exception {
        return dispatched(approved(lines, prepared));
    }

    String dispatched(String id) throws Exception {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.ofHours(-5));
        HttpResponse<String> dispatched = originOperator.post("/transfers/" + id + "/dispatch", Map.of("carrier", "Transportes Andinos",
            "departedAt", now.toString(), "estimatedArrivalAt", now.plus(Duration.ofHours(4)).toString()));
        assertThat(dispatched.statusCode()).as(dispatched.body()).isEqualTo(200);
        return id;
    }

    static Map<String, Object> line(UUID item, String unit, String quantity) {
        return Map.of("product", item.toString(), "unit", unit, "quantity", quantity);
    }

    static Map<String, Object> quantities(Object... lineAndQuantity) {
        List<Map<String, Object>> lines = new ArrayList<>();
        for (int i = 0; i < lineAndQuantity.length; i += 2) lines.add(Map.of("lineNo", lineAndQuantity[i], "quantity", lineAndQuantity[i + 1]));
        return Map.of("lines", lines);
    }

    static Map<String, Object> loss() {
        return Map.of("reason", "Mercancía extraviada en ruta", "evidence", "Acta 15 del transportista");
    }

    UUID branch(String code) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO identity_branch(external_id,code,name) VALUES (?,?,?)", id, code + suffix, "Sucursal " + code);
        return id;
    }

    /** An indivisible product with base unit "unidad" and commercial unit "caja" of 12, enabled in the given branches. */
    UUID product(String name, UUID... branches) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO catalog_product(external_id,sku,name,base_unit_name,base_divisible) VALUES (?,?,?,'unidad',false)",
            id, "SKU-" + UUID.randomUUID(), name);
        jdbc.update("INSERT INTO catalog_product_unit(product_id,name,factor,divisible,is_base) "
            + "SELECT id,'unidad',1,false,true FROM catalog_product WHERE external_id=?", id);
        jdbc.update("INSERT INTO catalog_product_unit(product_id,name,factor,divisible,is_base) "
            + "SELECT id,'caja',12,false,false FROM catalog_product WHERE external_id=?", id);
        for (UUID branch : branches) {
            jdbc.update("INSERT INTO catalog_branch_product(branch_id,product_id,enabled,minimum_qty) SELECT b.id,p.id,true,0 "
                + "FROM identity_branch b CROSS JOIN catalog_product p WHERE b.external_id=? AND p.external_id=?", branch, id);
        }
        return id;
    }

    Browser account(String role, UUID branch) throws Exception {
        String code = role + UUID.randomUUID();
        jdbc.update("INSERT INTO identity_user_account(external_id,code,full_name,password_hash,role,branch_id,must_change_password) "
                + "VALUES (?,?,?,?,?,(SELECT id FROM identity_branch WHERE external_id=?),false)",
            UUID.randomUUID(), code, "Persona " + role, passwords.encode("private individual password"), role, branch);
        Browser browser = new Browser();
        assertThat(browser.post("/auth/login", Map.of("code", code, "password", "private individual password"), null).statusCode())
            .isEqualTo(200);
        return browser;
    }

    /** Valued entry of {@code quantity} base units at {@code cost} in the origin, posted through the inventory ledger. */
    void stock(UUID item, String quantity, String cost) throws Exception {
        HttpResponse<String> posted = originOperator.post("/inventory/movements", Map.of("kind", "MANUAL_ENTRY",
            "product", item.toString(), "unit", "unidad", "quantity", quantity, "reason", "Inventario inicial", "unitCost", cost));
        assertThat(posted.statusCode()).as(posted.body()).isEqualTo(201);
    }

    JsonNode read(HttpResponse<String> response) { return json.readTree(response.body()); }

    void assertError(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(read(response).path("code").asText()).as(response.body()).isEqualTo(code);
    }

    List<String> ids(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        List<String> ids = new ArrayList<>();
        read(response).get("items").forEach(item -> ids.add(item.get("externalId").asText()));
        return ids;
    }

    /** quantity|value|average of a product in a branch, or "none" when the branch never had a balance for it. */
    String balance(UUID branch, UUID item) {
        return jdbc.queryForList("SELECT physical_qty||'|'||inventory_value||'|'||average_base_cost FROM inventory_balance "
            + "WHERE branch_id=(SELECT id FROM identity_branch WHERE external_id=?) "
            + "AND product_id=(SELECT id FROM catalog_product WHERE external_id=?)", String.class, branch, item)
            .stream().findFirst().orElse("none");
    }

    /** Physical quantity and value of the test's products in A, B and C. */
    List<String> balances() {
        return jdbc.queryForList("SELECT b.code||':'||p.name||':'||ib.physical_qty||':'||ib.inventory_value FROM inventory_balance ib "
            + "JOIN identity_branch b ON b.id=ib.branch_id JOIN catalog_product p ON p.id=ib.product_id "
            + "WHERE b.external_id IN (?,?,?) ORDER BY 1", String.class, a, b, c);
    }

    /** pending quantity|value|rounding of each transit balance of a transfer, by line. */
    List<String> transit(String id) {
        return jdbc.queryForList("SELECT it.pending_base_qty||'|'||it.pending_base_value||'|'||it.pending_rounding_value "
            + "FROM inventory_transit it JOIN logistics_dispatch_line dl ON dl.id=it.dispatch_line_id "
            + "JOIN transfers_line l ON l.id=dl.transfer_line_id JOIN transfers_transfer t ON t.id=l.transfer_id "
            + "WHERE t.external_id=?::uuid ORDER BY l.line_no", String.class, id);
    }

    int arrivalMovements(String id) {
        return jdbc.queryForObject("SELECT count(*) FROM inventory_movement m JOIN logistics_arrival_line al ON al.id=m.arrival_line_id "
            + "JOIN logistics_arrival a ON a.id=al.arrival_id JOIN logistics_dispatch d ON d.id=a.dispatch_id "
            + "JOIN transfers_transfer t ON t.id=d.transfer_id WHERE t.external_id=?::uuid", Integer.class, id);
    }

    int arrivalOperations(String id) {
        return jdbc.queryForObject("SELECT count(*) FROM inventory_operation o JOIN logistics_arrival a ON a.id=o.arrival_id "
            + "JOIN logistics_dispatch d ON d.id=a.dispatch_id JOIN transfers_transfer t ON t.id=d.transfer_id "
            + "WHERE t.external_id=?::uuid", Integer.class, id);
    }

    String alertState(String id) {
        return jdbc.queryForObject("SELECT x.status||'|'||coalesce(x.treatment,'')||'|'||coalesce(x.resolved_by::text,'') "
            + "FROM logistics_discrepancy x JOIN logistics_arrival a ON a.id=x.arrival_id JOIN logistics_dispatch d ON d.id=a.dispatch_id "
            + "JOIN transfers_transfer t ON t.id=d.transfer_id WHERE t.external_id=?::uuid", String.class, id);
    }

    record Snapshot(String transfer, List<String> balances, int movements, int arrivals, List<String> transit, int operations,
                    int discrepancies) {}

    /** Everything a rejected or repeated reception, resolution or loss must leave untouched. */
    Snapshot snapshot(String id) {
        return new Snapshot(jdbc.queryForObject("SELECT t.status||'|'||(SELECT count(*) FROM transfers_event e WHERE e.transfer_id=t.id) "
                + "FROM transfers_transfer t WHERE t.external_id=?::uuid", String.class, id), balances(),
            jdbc.queryForObject("SELECT count(*) FROM inventory_movement", Integer.class),
            jdbc.queryForObject("SELECT count(*) FROM logistics_arrival", Integer.class), transit(id),
            jdbc.queryForObject("SELECT count(*) FROM inventory_operation", Integer.class),
            jdbc.queryForObject("SELECT count(*) FROM logistics_discrepancy_line", Integer.class));
    }

    /** Runs the calls at the same time and returns their HTTP statuses. */
    @SafeVarargs
    static List<Integer> race(Callable<Integer>... calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.length);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> call : calls) futures.add(pool.submit(() -> { start.await(); return call.call(); }));
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) statuses.add(future.get());
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    class Browser {
        final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        HttpResponse<String> get(String path) throws Exception {
            return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        }
        /** POST with a fresh idempotency key. */
        HttpResponse<String> post(String path, Object body) throws Exception { return post(path, body, UUID.randomUUID().toString()); }
        HttpResponse<String> post(String path, Object body, String key) throws Exception {
            JsonNode csrf = json.readTree(get("/auth/csrf").body());
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            if (csrf.has("headerName")) request.header(csrf.get("headerName").asText(), csrf.get("token").asText());
            if (key != null) request.header("Idempotency-Key", key);
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}

package com.optiplant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * RF-0050–RF-0055: direct download of real XLSX workbooks of movements, sales and transfers. Each test opens the file
 * with POI and checks the approved columns, the Bogotá date filter of its category, the limits of 10.000 rows and 12
 * months, the empty file and the permissions; an export never changes operational data.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"app.admin-code=report-initial", "app.admin-password=private initial password"})
class ReportExportHttpTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.11")
        .withInitScript("db/test_roles.sql");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", () -> "optiplant_test_runtime");
        r.add("spring.datasource.password", () -> "test-runtime-not-for-deployment");
        r.add("spring.flyway.user", POSTGRES::getUsername);
        r.add("spring.flyway.password", POSTGRES::getPassword);
    }
    static final ZoneOffset BOGOTA = ZoneOffset.ofHours(-5);
    static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    static final LocalDate DAY = LocalDate.of(2025, 3, 10);

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired JsonMapper json;
    JdbcTemplate owner;
    UUID a, b, product;
    String suffix, list, sku;
    Browser admin, aOperator, aManager, bOperator, bManager;

    @BeforeEach void data() throws Exception {
        owner = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        suffix = UUID.randomUUID().toString().substring(0, 8);
        a = branch("a");
        b = branch("b");
        product = product();
        admin = account("ADMIN", null);
        aOperator = account("OPERATOR", a);
        aManager = account("MANAGER", a);
        bOperator = account("OPERATOR", b);
        bManager = account("MANAGER", b);
        HttpResponse<String> created = admin.post("/catalog/price-lists", Map.of("code", "LP-" + suffix, "name", "Detal"));
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        list = read(created).get("externalId").asText();
        assertThat(admin.post("/catalog/price-lists/" + list + "/prices", Map.of("product", product.toString(), "unit", "unidad",
            "price", "100")).statusCode()).isEqualTo(200);
        assertThat(admin.post("/logistics/routes", Map.of("code", "AB" + suffix, "origin", a.toString(),
            "destination", b.toString(), "priority", "HIGH")).statusCode()).isEqualTo(201);
        movement(aOperator, "100", "Inventario inicial");
    }

    // ---------------------------------------------------------------- RF-0050 / RF-0051 / RF-0052

    @Test void movementsAreARealWorkbookWithTheApprovedColumnsAndWithoutCosts() throws Exception {
        movement(aOperator, "5", "=SUM(1,1)");
        String before = state();

        HttpResponse<byte[]> response = aManager.download("/reports/movements?from=" + today() + "&to=" + today());
        assertThat(state()).isEqualTo(before);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).contains(XLSX);
        assertThat(response.headers().firstValue("Content-Disposition").orElseThrow())
            .startsWith("attachment").contains("movimientos_" + today() + "_" + today() + ".xlsx");
        assertThat(response.headers().firstValue("X-Report-Rows")).contains("2");
        assertThat(new String(response.body(), 0, 2, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("PK");

        Report report = report(response);
        assertThat(report.headers()).containsExactly("ID público del movimiento", "Fecha de registro", "Sucursal", "SKU", "Producto",
            "Clase", "Motivo", "Cantidad", "Unidad", "Usuario responsable", "Documento origen");
        assertThat(report.metadata()).containsEntry("Fecha del filtro", "Fecha de registro del movimiento")
            .containsEntry("Zona horaria", "America/Bogota").containsEntry("Filas", "2");
        Map<String, String> first = report.rows().getFirst();
        String publicId = jdbc.queryForObject("SELECT m.external_id::text FROM inventory_movement m JOIN inventory_operation o "
            + "ON o.id=m.operation_id WHERE o.reason='=SUM(1,1)' AND m.sku_snapshot=?", String.class, sku);
        assertThat(first).containsEntry("ID público del movimiento", publicId).containsEntry("SKU", sku)
            .containsEntry("Producto", "Maceta").containsEntry("Clase", "Ingreso ordinario").containsEntry("Cantidad", "5")
            .containsEntry("Unidad", "unidad");
        assertThat(first.get("Sucursal")).isEqualTo("a" + suffix + " · Sucursal a");
        // Stored as text: the spreadsheet shows what the user wrote and never evaluates it.
        assertThat(first).containsEntry("Motivo", "=SUM(1,1)");
        assertThat(report.formulaCells()).isZero();
    }

    @Test void aMovementNamesTheDocumentThatOriginatedIt() throws Exception {
        String sale = sale(aOperator, "2", "0");
        String code = read(aOperator.get("/sales/" + sale)).get("code").asText();

        Report report = report(aManager.download("/reports/movements?from=" + today() + "&to=" + today()));

        Map<String, String> exit = report.rows().stream().filter(row -> row.get("Clase").equals("Venta")).findFirst().orElseThrow();
        assertThat(exit).containsEntry("Documento origen", "Venta " + code).containsEntry("Cantidad", "-2");
        Map<String, String> manual = report.rows().stream().filter(row -> row.get("Clase").equals("Ingreso ordinario")).findFirst().orElseThrow();
        assertThat(manual.get("Documento origen")).isNull();
    }

    // ---------------------------------------------------------------- RF-0053

    @Test void salesExportOneRowPerLineAndTheirLinesAddUpToTheSale() throws Exception {
        HttpResponse<String> confirmed = aOperator.post("/sales", Map.of("priceList", list, "lines", List.of(
            Map.of("product", product.toString(), "unit", "unidad", "quantity", "2", "discountPercent", "10"),
            Map.of("product", product.toString(), "unit", "unidad", "quantity", "3", "discountPercent", "0"))));
        assertThat(confirmed.statusCode()).as(confirmed.body()).isEqualTo(201);
        JsonNode sale = read(confirmed);
        String before = state();

        Report report = report(aManager.download("/reports/sales?from=" + today() + "&to=" + today()));

        assertThat(state()).isEqualTo(before);
        assertThat(report.headers()).containsExactly("Número de venta", "Fecha de confirmación", "Sucursal", "Responsable", "SKU",
            "Producto", "Unidad", "Cantidad", "Precio unitario (COP)", "Descuento (%)", "Importe neto de línea (COP)");
        assertThat(report.metadata()).containsEntry("Fecha del filtro", "Fecha de confirmación de la venta");
        assertThat(report.rows()).hasSize(2);
        assertThat(report.rows()).allSatisfy(row -> assertThat(row).containsEntry("Número de venta", sale.get("code").asText()));
        assertThat(report.rows().getFirst()).containsEntry("Cantidad", "2").containsEntry("Precio unitario (COP)", "100")
            .containsEntry("Descuento (%)", "10").containsEntry("Importe neto de línea (COP)", "180");
        BigDecimal total = report.rows().stream().map(row -> new BigDecimal(row.get("Importe neto de línea (COP)")))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo(sale.get("totalNet").asText());
    }

    // ---------------------------------------------------------------- RF-0054

    @Test void transfersExportEachLineOnceWithTheSingleArrivalAndItsDifference() throws Exception {
        String id = inTransit("10");
        String key = UUID.randomUUID().toString();
        assertThat(bOperator.post("/transfers/" + id + "/receive", quantities("7"), key).statusCode()).isEqualTo(200);
        assertThat(bOperator.post("/transfers/" + id + "/receive", quantities("7"), key).statusCode()).isEqualTo(200);
        String pending = requested("4");
        String before = state();

        Report report = report(bManager.download("/reports/transfers?from=" + today() + "&to=" + today()));

        assertThat(state()).isEqualTo(before);
        assertThat(report.headers()).containsExactly("Número de transferencia", "Origen", "Destino", "Fecha de solicitud",
            "Fecha de despacho", "Fecha de recepción", "Estado", "SKU", "Unidad", "Cantidad solicitada", "Cantidad despachada",
            "Cantidad recibida", "Diferencia pendiente", "Tratamiento");
        assertThat(report.metadata()).containsEntry("Fecha del filtro", "Fecha de solicitud de la transferencia")
            .containsEntry("Estado", "Todos");
        assertThat(report.rows()).hasSize(2);
        Map<String, String> received = report.row("Número de transferencia", code(id));
        assertThat(received).containsEntry("Estado", "Recibida con discrepancia").containsEntry("Cantidad solicitada", "10")
            .containsEntry("Cantidad despachada", "10").containsEntry("Cantidad recibida", "7")
            .containsEntry("Diferencia pendiente", "3").containsEntry("Tratamiento", "Sin tratamiento (alerta abierta)");
        assertThat(received.get("Fecha de despacho")).isNotNull();
        assertThat(received.get("Fecha de recepción")).isNotNull();
        Map<String, String> requested = report.row("Número de transferencia", code(pending));
        assertThat(requested).containsEntry("Estado", "Solicitada").containsEntry("Cantidad solicitada", "4");
        assertThat(requested.get("Cantidad despachada")).isNull();
        assertThat(requested.get("Diferencia pendiente")).isNull();
        assertThat(requested.get("Tratamiento")).isNull();
    }

    @Test void aResolvedAlertIsNotAReceptionAndAWriteOffClearsThePendingDifference() throws Exception {
        String id = inTransit("10");
        assertThat(bOperator.post("/transfers/" + id + "/receive", quantities("6")).statusCode()).isEqualTo(200);
        assertThat(aManager.post("/transfers/" + id + "/discrepancy/resolve", Map.of("treatment", "CLAIM")).statusCode()).isEqualTo(200);

        Map<String, String> resolved = report(aManager.download("/reports/transfers?from=" + today() + "&to=" + today()))
            .row("Número de transferencia", code(id));
        assertThat(resolved).containsEntry("Estado", "Recibida con discrepancia").containsEntry("Diferencia pendiente", "4")
            .containsEntry("Cantidad recibida", "6").containsEntry("Tratamiento", "Reclamación");

        assertThat(aManager.post("/transfers/" + id + "/discrepancy/transit-loss", Map.of("reason", "Mercancía extraviada",
            "evidence", "Acta 12")).statusCode()).isEqualTo(200);
        Map<String, String> written = report(aManager.download("/reports/transfers?from=" + today() + "&to=" + today()))
            .row("Número de transferencia", code(id));
        assertThat(written).containsEntry("Diferencia pendiente", "0").containsEntry("Cantidad recibida", "6")
            .containsEntry("Tratamiento", "Reclamación; pérdida en tránsito registrada");
    }

    @Test void aResendKeepsItsOwnRowWithoutAddingItsReceptionToTheOriginal() throws Exception {
        String id = inTransit("10");
        assertThat(bOperator.post("/transfers/" + id + "/receive", quantities("6")).statusCode()).isEqualTo(200);
        assertThat(aManager.post("/transfers/" + id + "/discrepancy/resolve", Map.of("treatment", "RESEND")).statusCode()).isEqualTo(200);
        String discrepancy = read(bOperator.get("/transfers/" + id)).at("/discrepancy/externalId").asText();
        HttpResponse<String> created = bOperator.post("/transfers", Map.of("origin", a.toString(), "sourceDiscrepancy", discrepancy,
            "lines", List.of(Map.of("product", product.toString(), "unit", "unidad", "quantity", "4"))));
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        String resend = read(created).get("externalId").asText();
        dispatch(resend, "4");
        assertThat(bOperator.post("/transfers/" + resend + "/receive", quantities("4")).statusCode()).isEqualTo(200);

        Report report = report(bManager.download("/reports/transfers?from=" + today() + "&to=" + today()));

        assertThat(report.rows()).hasSize(2);
        assertThat(report.row("Número de transferencia", code(id))).containsEntry("Cantidad recibida", "6")
            .containsEntry("Diferencia pendiente", "4").containsEntry("Tratamiento", "Reenvío");
        Map<String, String> own = report.row("Número de transferencia", code(resend));
        assertThat(own).containsEntry("Estado", "Recibida completa").containsEntry("Cantidad recibida", "4")
            .containsEntry("Diferencia pendiente", "0");
        assertThat(own.get("Tratamiento")).isNull();
    }

    @Test void aWriteOffOnAnOpenAlertKeepsTheAlertVisible() throws Exception {
        String id = inTransit("10");
        assertThat(bOperator.post("/transfers/" + id + "/receive", quantities("6")).statusCode()).isEqualTo(200);
        assertThat(aManager.post("/transfers/" + id + "/discrepancy/transit-loss", Map.of("reason", "Mercancía extraviada",
            "evidence", "Acta 12")).statusCode()).isEqualTo(200);

        Map<String, String> row = report(aManager.download("/reports/transfers?from=" + today() + "&to=" + today()))
            .row("Número de transferencia", code(id));

        assertThat(row).containsEntry("Diferencia pendiente", "0")
            .containsEntry("Tratamiento", "Sin tratamiento (alerta abierta); pérdida en tránsito registrada");
    }

    @Test void transfersAreFilteredByTheOptionalStatusAndAnUnknownStatusIsRejected() throws Exception {
        String pending = requested("4");
        String dispatched = inTransit("2");

        Report report = report(admin.download("/reports/transfers?from=" + today() + "&to=" + today() + "&branch=" + b + "&status=IN_TRANSIT"));

        assertThat(report.metadata()).containsEntry("Estado", "En tránsito");
        assertThat(report.rows()).extracting(row -> row.get("Número de transferencia")).containsExactly(code(dispatched)).doesNotContain(code(pending));
        assertError(admin.download("/reports/transfers?from=" + today() + "&to=" + today() + "&status=DONE"), 400, "VALIDATION_FAILED");
    }

    // ---------------------------------------------------------------- RF-0055

    @Test void eachCategoryFiltersByItsOwnInclusiveBogotaDatesWithoutTheNextDay() throws Exception {
        OffsetDateTime[] instants = {DAY.minusDays(1).atTime(23, 59, 59).atOffset(BOGOTA), DAY.atStartOfDay().atOffset(BOGOTA),
            DAY.plusDays(1).atTime(12, 0).atOffset(BOGOTA), DAY.plusDays(1).atTime(23, 59, 59).atOffset(BOGOTA),
            DAY.plusDays(2).atStartOfDay().atOffset(BOGOTA)};
        String period = "?from=" + DAY + "&to=" + DAY.plusDays(1);
        List<String> sales = new ArrayList<>();
        List<String> transfers = new ArrayList<>();
        for (OffsetDateTime instant : instants) {
            String sale = sale(aOperator, "1", "0");
            owner.update("UPDATE sales_sale SET confirmed_at=? WHERE external_id=?::uuid", instant, sale);
            sales.add(read(aOperator.get("/sales/" + sale)).get("code").asText());
            String transfer = requested("1");
            owner.update("UPDATE transfers_transfer SET requested_at=? WHERE external_id=?::uuid", instant, transfer);
            transfers.add(code(transfer));
            owner.update("UPDATE inventory_operation SET occurred_at=? WHERE sale_id=(SELECT id FROM sales_sale WHERE external_id=?::uuid)", instant, sale);
        }

        assertThat(report(aManager.download("/reports/sales" + period)).rows()).extracting(row -> row.get("Número de venta"))
            .containsExactlyInAnyOrder(sales.get(1), sales.get(2), sales.get(3));
        assertThat(report(aManager.download("/reports/transfers" + period)).rows()).extracting(row -> row.get("Número de transferencia"))
            .containsExactlyInAnyOrder(transfers.get(1), transfers.get(2), transfers.get(3));
        assertThat(report(aManager.download("/reports/movements" + period)).rows()).extracting(row -> row.get("Documento origen"))
            .containsExactlyInAnyOrder("Venta " + sales.get(1), "Venta " + sales.get(2), "Venta " + sales.get(3));
    }

    @Test void theDatesAreMandatoryAndThePeriodCannotBeInvertedOrExceedTwelveMonths() throws Exception {
        for (String report : List.of("movements", "sales", "transfers")) {
            String path = "/reports/" + report;
            assertError(aManager.download(path), 400, "VALIDATION_FAILED");
            assertError(aManager.download(path + "?from=2026-01-01"), 400, "VALIDATION_FAILED");
            assertError(aManager.download(path + "?from=2026-02-01&to=2026-01-01"), 400, "VALIDATION_FAILED");
            HttpResponse<byte[]> exceeded = aManager.download(path + "?from=2026-01-01&to=2027-01-01");
            assertError(exceeded, 400, "VALIDATION_FAILED");
            assertThat(new String(exceeded.body(), java.nio.charset.StandardCharsets.UTF_8)).contains("12 meses");
            assertThat(aManager.download(path + "?from=2026-01-01&to=2026-12-31").statusCode()).isEqualTo(200);
        }
    }

    // ---------------------------------------------------------------- RF-0050: empty file and limits

    @Test void aReportWithoutResultsIsAValidWorkbookWithItsHeadersAndAnExplicitNote() throws Exception {
        for (String category : List.of("movements", "sales", "transfers")) {
            HttpResponse<byte[]> response = aManager.download("/reports/" + category + "?from=2020-01-01&to=2020-01-31");
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("X-Report-Rows")).contains("0");
            Report report = report(response);
            assertThat(report.headers()).isNotEmpty();
            assertThat(report.rows()).isEmpty();
            assertThat(report.metadata()).containsEntry("Filas", "0")
                .containsEntry("Nota", "Sin resultados: no hay registros para los filtros indicados.");
        }
    }

    @Test void tenThousandRowsAreExportedAndOneMoreIsRejectedWithoutTruncating() throws Exception {
        String sale = sale(aOperator, "1", "0");
        lines(sale, 2, 10_000);
        String before = state();

        HttpResponse<byte[]> allowed = aManager.download("/reports/sales?from=" + today() + "&to=" + today());
        assertThat(allowed.statusCode()).isEqualTo(200);
        assertThat(allowed.headers().firstValue("X-Report-Rows")).contains("10000");
        assertThat(report(allowed).rows()).hasSize(10_000);

        lines(sale, 10_001, 10_001);
        HttpResponse<byte[]> rejected = aManager.download("/reports/sales?from=" + today() + "&to=" + today());
        assertError(rejected, 422, "REPORT_TOO_LARGE");
        assertThat(new String(rejected.body(), java.nio.charset.StandardCharsets.UTF_8)).contains("10.000");
        assertThat(rejected.headers().firstValue("Content-Disposition")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_movement", Integer.class).toString()).isEqualTo(before.split("/")[0]);
    }

    // ---------------------------------------------------------------- RF-0050-CA-04

    @Test void operatorsExportNothingAndManagersOnlyTheirOwnBranch() throws Exception {
        movement(bOperator, "7", "Inventario B");
        String period = "?from=" + today() + "&to=" + today();
        for (String category : List.of("movements", "sales", "transfers")) {
            String path = "/reports/" + category;
            assertError(aOperator.download(path + period), 403, "ROLE_FORBIDDEN");
            assertError(aOperator.download(path), 403, "ROLE_FORBIDDEN");
            assertError(aOperator.download(path + period + "&status=DONE"), 403, "ROLE_FORBIDDEN");
            assertError(aManager.download(path + period + "&branch=" + b), 403, "BRANCH_FORBIDDEN");
            assertThat(aManager.download(path + period + "&branch=" + a).statusCode()).isEqualTo(200);
            assertThat(new Browser().download(path + period).statusCode()).isEqualTo(401);
            assertError(admin.download(path + period + "&branch=" + UUID.randomUUID()), 404, "BRANCH_NOT_FOUND");
        }
        assertThat(report(aManager.download("/reports/movements" + period)).rows()).extracting(row -> row.get("Motivo"))
            .containsExactly("Inventario inicial");
        Report own = report(admin.download("/reports/movements" + period + "&branch=" + b));
        assertThat(own.metadata()).containsEntry("Sucursal", "b" + suffix + " · Sucursal b");
        assertThat(own.rows()).extracting(row -> row.get("Motivo")).containsExactly("Inventario B");
        Report network = report(admin.download("/reports/movements" + period));
        assertThat(network.metadata()).containsEntry("Sucursal", "Toda la red");
        assertThat(network.rows()).extracting(row -> row.get("Motivo")).contains("Inventario inicial", "Inventario B");
    }

    @Test void aManagerOfADestinationSeesItsTransferAndAThirdBranchDoesNot() throws Exception {
        String id = requested("1");
        UUID c = branch("c");
        Browser cManager = account("MANAGER", c);
        String period = "?from=" + today() + "&to=" + today();

        assertThat(report(aManager.download("/reports/transfers" + period)).rows()).extracting(row -> row.get("Número de transferencia"))
            .contains(code(id));
        assertThat(report(bManager.download("/reports/transfers" + period)).rows()).extracting(row -> row.get("Número de transferencia"))
            .contains(code(id));
        assertThat(report(cManager.download("/reports/transfers" + period)).rows()).isEmpty();
    }

    // ---------------------------------------------------------------- fixtures

    LocalDate today() { return LocalDate.now(BOGOTA); }

    String code(String transfer) throws Exception { return read(bOperator.get("/transfers/" + transfer)).get("code").asText(); }

    /** Stock, movements and sale lines: what an export must never change. */
    String state() {
        return jdbc.queryForObject("SELECT (SELECT count(*) FROM inventory_movement)||'/'||(SELECT count(*) FROM sales_line)||'/'||"
            + "(SELECT count(*) FROM transfers_event)||'/'||(SELECT coalesce(sum(physical_qty),0) FROM inventory_balance)", String.class);
    }

    void movement(Browser operator, String quantity, String reason) throws Exception {
        // The cost is only accepted while the branch has no stock; afterwards the entry takes the current average.
        Map<String, Object> body = new LinkedHashMap<>(Map.of("kind", "MANUAL_ENTRY", "product", product.toString(),
            "unit", "unidad", "quantity", quantity, "reason", reason));
        if (reason.startsWith("Inventario")) body.put("unitCost", "9");
        HttpResponse<String> posted = operator.post("/inventory/movements", body);
        assertThat(posted.statusCode()).as(posted.body()).isEqualTo(201);
    }

    String sale(Browser operator, String quantity, String discount) throws Exception {
        HttpResponse<String> confirmed = operator.post("/sales", Map.of("priceList", list, "lines", List.of(Map.of(
            "product", product.toString(), "unit", "unidad", "quantity", quantity, "discountPercent", discount))));
        assertThat(confirmed.statusCode()).as(confirmed.body()).isEqualTo(201);
        return read(confirmed).get("externalId").asText();
    }

    /** Copies the first line of a sale as lines {@code from}..{@code to}; only the owner may bypass the sale flow. */
    void lines(String sale, int from, int to) {
        owner.update("""
            INSERT INTO sales_line(sale_id,line_no,product_id,product_unit_id,price_list_id,list_name_snapshot,sku_snapshot,
                product_name_snapshot,unit_name_snapshot,factor_snapshot,commercial_qty,base_qty,gross_unit_price,discount_pct,
                net_amount,historical_base_cost)
            SELECT l.sale_id,g,l.product_id,l.product_unit_id,l.price_list_id,l.list_name_snapshot,l.sku_snapshot,
                l.product_name_snapshot,l.unit_name_snapshot,l.factor_snapshot,l.commercial_qty,l.base_qty,l.gross_unit_price,
                l.discount_pct,l.net_amount,l.historical_base_cost
            FROM sales_line l CROSS JOIN generate_series(?,?) g
            WHERE l.line_no=1 AND l.sale_id=(SELECT id FROM sales_sale WHERE external_id=?::uuid)""", from, to, sale);
    }

    String requested(String quantity) throws Exception {
        HttpResponse<String> created = bOperator.post("/transfers", Map.of("origin", a.toString(),
            "lines", List.of(Map.of("product", product.toString(), "unit", "unidad", "quantity", quantity))));
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        return read(created).get("externalId").asText();
    }

    String inTransit(String quantity) throws Exception {
        return dispatch(requested(quantity), quantity);
    }

    String dispatch(String id, String quantity) throws Exception {
        assertThat(aOperator.post("/transfers/" + id + "/prepare", quantities(quantity)).statusCode()).isEqualTo(200);
        assertThat(aManager.post("/transfers/" + id + "/approve", Map.of()).statusCode()).isEqualTo(200);
        OffsetDateTime now = OffsetDateTime.now(BOGOTA);
        HttpResponse<String> dispatched = aOperator.post("/transfers/" + id + "/dispatch", Map.of("carrier", "Transportes Andinos",
            "departedAt", now.toString(), "estimatedArrivalAt", now.plus(Duration.ofHours(4)).toString()));
        assertThat(dispatched.statusCode()).as(dispatched.body()).isEqualTo(200);
        return id;
    }

    static Map<String, Object> quantities(String quantity) {
        return Map.of("lines", List.of(Map.of("lineNo", 1, "quantity", quantity)));
    }

    UUID branch(String code) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO identity_branch(external_id,code,name) VALUES (?,?,?)", id, code + suffix, "Sucursal " + code);
        return id;
    }

    UUID product() {
        UUID id = UUID.randomUUID();
        sku = "SKU-" + UUID.randomUUID();
        jdbc.update("INSERT INTO catalog_product(external_id,sku,name,base_unit_name,base_divisible) VALUES (?,?,'Maceta','unidad',false)", id, sku);
        jdbc.update("INSERT INTO catalog_product_unit(product_id,name,factor,divisible,is_base) "
            + "SELECT id,'unidad',1,false,true FROM catalog_product WHERE external_id=?", id);
        for (UUID branch : List.of(a, b)) {
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

    JsonNode read(HttpResponse<String> response) { return json.readTree(response.body()); }

    void assertError(HttpResponse<byte[]> response, int status, String code) {
        String body = new String(response.body(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(response.statusCode()).as(body).isEqualTo(status);
        assertThat(json.readTree(body).path("code").asText()).as(body).isEqualTo(code);
    }

    /** What a reader of the file sees: the metadata above the header and one map per data row. */
    record Report(Map<String, String> metadata, List<String> headers, List<Map<String, String>> rows, int formulaCells) {
        Map<String, String> row(String column, String value) {
            return rows.stream().filter(row -> value.equals(row.get(column))).findFirst().orElseThrow();
        }
    }

    Report report(HttpResponse<byte[]> response) throws Exception {
        assertThat(response.statusCode()).as(new String(response.body(), java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(200);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response.body()))) {
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, String> metadata = new LinkedHashMap<>();
            int index = 1;
            for (; sheet.getRow(index) != null; index++) metadata.put(text(sheet.getRow(index).getCell(0)), text(sheet.getRow(index).getCell(1)));
            int header = index + 1;
            List<String> headers = new ArrayList<>();
            sheet.getRow(header).forEach(cell -> headers.add(text(cell)));
            List<Map<String, String>> rows = new ArrayList<>();
            int formulas = 0;
            for (int i = header + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                Map<String, String> values = new LinkedHashMap<>();
                for (int c = 0; c < headers.size(); c++) {
                    Cell cell = row.getCell(c);
                    if (cell != null && cell.getCellType() == CellType.FORMULA) formulas++;
                    values.put(headers.get(c), text(cell));
                }
                rows.add(values);
            }
            return new Report(metadata, headers, rows, formulas);
        }
    }

    static String text(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() != CellType.NUMERIC) return cell.getCellType() == CellType.FORMULA ? cell.getCellFormula() : cell.getStringCellValue();
        if (DateUtil.isCellDateFormatted(cell)) return cell.getLocalDateTimeCellValue().toString();
        return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
    }

    class Browser {
        final HttpClient http = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        HttpResponse<String> get(String path) throws Exception {
            return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        }
        /** As the client downloads: an error must still arrive as a ProblemDetail despite the requested type. */
        HttpResponse<byte[]> download(String path) throws Exception {
            return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .header("Accept", XLSX + ", application/problem+json").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        }
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

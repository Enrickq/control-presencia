package software.ulpgc.register.query.adapter.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.ulpgc.register.query.model.DayQuery;
import software.ulpgc.register.query.port.QueryException;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class HttpAttendanceGatewayTest {
    private static final URI BASE = URI.create("http://localhost/proxy/");
    private static final DayQuery QUERY = DayQuery.parse("DEM+O &Ñ", "2026-11-20");
    private static final String LEGACY = """
            {"employeeId":"DEM+O &Ñ","date":"2026-11-20","expectedMinutes":480,"workedMinutes":465,
             "lateMinutes":10,"balanceMinutes":-15,"status":"PRESENT"}""";

    @Test
    void preservesSignedValuesAndDistinguishesAnOlderServerFromNoIncidents() {
        var old = reply(200, LEGACY).attendance(QUERY).join();
        assertThat(old.balanceMinutes()).isEqualTo(-15);
        assertThat(old.incidents()).isEmpty();
        String current = LEGACY.replace("}", ",\"incidents\":[],\"futureField\":true}");
        assertThat(reply(200, current).attendance(QUERY).join().incidents()).hasValue(java.util.List.of());
    }

    @Test
    void buildsReadOnlyEncodedRequestsUnderTheConfiguredPrefix() {
        var requested = new AtomicReference<java.net.http.HttpRequest>();
        var gateway = new HttpAttendanceGateway(BASE, request -> {
            requested.set(request);
            return CompletableFuture.completedFuture(new HttpAttendanceGateway.Reply(200, "[]"));
        });
        assertThat(gateway.punches(QUERY).join()).isEmpty();
        assertThat(requested.get().method()).isEqualTo("GET");
        assertThat(requested.get().uri().toASCIIString())
                .isEqualTo("http://localhost/proxy/api/v1/punches?employeeId=DEM%2BO+%26%C3%91&date=2026-11-20");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "[]", "{", "", "<html>Error</html>"})
    void rejectsInvalidSummaryDocuments(String body) {
        assertThatThrownBy(() -> reply(200, body).attendance(QUERY).join()).hasCauseInstanceOf(QueryException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"480\"", "-1", "0.75", "9223372036854775808"})
    void rejectsInvalidMinuteValues(String value) {
        assertThatThrownBy(() -> reply(200, LEGACY.replace("480", value)).attendance(QUERY).join())
                .hasCauseInstanceOf(QueryException.class);
    }

    @Test
    void rejectsDifferentEmployeeDayAndMalformedPunches() {
        for (String body : new String[]{LEGACY.replace("DEM+O &Ñ", "OTRO"), LEGACY.replace("11-20", "11-21")})
            assertThatThrownBy(() -> reply(200, body).attendance(QUERY).join()).hasCauseInstanceOf(QueryException.class);
        String punch = """
                [{"employeeId":"DEM+O &Ñ","placeId":"SALA","timestamp":"2026-11-20T08:10:00Z","type":"IN"}]""";
        for (String body : new String[]{"{}", "[null]", punch.replace("11-20", "11-21"), punch.replace("IN", "???"),
                punch.replace("DEM+O &Ñ", "OTRO"), punch.replace("08:10:00Z", "28:10:00Z")})
            assertThatThrownBy(() -> reply(200, body).punches(QUERY).join()).hasCauseInstanceOf(QueryException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 404, 409, 503})
    void retainsHttpStatusEvenWhenAnErrorBodyIsNotJson(int status) {
        assertThatThrownBy(() -> reply(status, "pagina del servidor").punches(QUERY).join())
                .cause().isInstanceOf(QueryException.class).hasMessageContaining("HTTP " + status);
    }

    @Test
    void reportsTransportFailureWithoutShowingAnEmptyDay() {
        var gateway = new HttpAttendanceGateway(BASE, request -> CompletableFuture.failedFuture(new IOException()));
        assertThatThrownBy(() -> gateway.attendance(QUERY).join()).cause().hasMessageContaining("conexión");
    }

    @ParameterizedTest
    @ValueSource(strings = {"file:///tmp", "http://a:b@host", "http://localhost/?q=x", "https://host/#f", "http://host:0"})
    void rejectsInvalidBaseUrls(String address) {
        assertThatIllegalArgumentException().isThrownBy(() -> new HttpAttendanceGateway(URI.create(address),
                request -> CompletableFuture.failedFuture(new AssertionError())));
    }

    private HttpAttendanceGateway reply(int status, String body) {
        return new HttpAttendanceGateway(BASE, request ->
                CompletableFuture.completedFuture(new HttpAttendanceGateway.Reply(status, body)));
    }
}

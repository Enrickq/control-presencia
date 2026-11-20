package software.ulpgc.register.query.adapter.http;

import software.ulpgc.register.query.model.*;
import software.ulpgc.register.query.port.AttendanceGateway;
import software.ulpgc.register.query.port.QueryException;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public final class HttpAttendanceGateway implements AttendanceGateway {
    record Reply(int status, String body) { }

    private final AttendanceJson json = new AttendanceJson();
    private final URI api;
    private final Function<HttpRequest, CompletableFuture<Reply>> exchange;

    public HttpAttendanceGateway(URI base, HttpClient client) {
        this(base, request -> client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> new Reply(response.statusCode(), response.body())));
    }

    HttpAttendanceGateway(URI base, Function<HttpRequest, CompletableFuture<Reply>> exchange) {
        if (!("http".equalsIgnoreCase(base.getScheme()) || "https".equalsIgnoreCase(base.getScheme()))
                || base.getHost() == null || base.getUserInfo() != null || base.getRawQuery() != null
                || base.getRawFragment() != null || base.getPort() == 0 || base.getPort() > 65535)
            throw new IllegalArgumentException("Introduce una URL base HTTP o HTTPS válida.");
        this.api = URI.create(base.toASCIIString().replaceAll("/+$", "") + "/api/v1/");
        this.exchange = exchange;
    }

    @Override
    public CompletableFuture<DailyIndicators> attendance(DayQuery query) {
        return get("attendance", query).thenApply(body -> json.indicators(body, query));
    }

    @Override
    public CompletableFuture<List<Punch>> punches(DayQuery query) {
        return get("punches", query).thenApply(body -> json.punches(body, query));
    }

    private CompletableFuture<String> get(String resource, DayQuery query) {
        String employee = URLEncoder.encode(query.employeeId(), StandardCharsets.UTF_8);
        URI uri = api.resolve(resource + "?employeeId=" + employee + "&date=" + query.date());
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json").GET().build();
        return exchange.apply(request).exceptionally(error -> {
            throw new QueryException("No se ha recibido respuesta. Comprueba la conexión con el servidor.", error);
        }).thenApply(response -> {
            if (response.status() != 200) throw json.httpError(response.status(), response.body());
            return response.body();
        });
    }
}

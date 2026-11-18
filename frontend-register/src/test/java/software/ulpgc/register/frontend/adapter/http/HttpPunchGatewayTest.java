package software.ulpgc.register.frontend.adapter.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import software.ulpgc.register.frontend.model.PunchData;
import software.ulpgc.register.frontend.model.PunchType;
import software.ulpgc.register.frontend.port.PunchException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static java.util.concurrent.TimeUnit.SECONDS;

class HttpPunchGatewayTest {
    private static final PunchData PUNCH = new PunchData("EMP-1", "José \"Pepe\"", "CENTRO-1", "Oficina Ñ", PunchType.IN);
    private static final String RECEIPT = """
            {"employeeId":"EMP-1","placeId":"CENTRO-1","type":"IN","timestamp":"2026-11-15T09:31:00Z"}
            """;
    private HttpServer server;
    private ExecutorService workers;
    private URI base;
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> requestMethod = new AtomicReference<>();
    private final AtomicReference<String> contentType = new AtomicReference<>();
    private final CountDownLatch requestReceived = new CountDownLatch(1);
    private volatile int status = 201;
    private volatile String response = RECEIPT;
    private volatile long delayMillis;
    private volatile CountDownLatch responseGate;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        workers = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(workers);
        server.createContext("/api/v1/punches", exchange -> {
            requests.incrementAndGet();
            requestMethod.set(exchange.getRequestMethod());
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            requestReceived.countDown();
            try {
                CountDownLatch gate = responseGate;
                if (gate != null && !gate.await(10, SECONDS))
                    throw new IOException("The test did not release the response");
                if (delayMillis > 0) Thread.sleep(delayMillis);
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        workers.shutdownNow();
    }

    @Test
    void postsExactContractWithUnicodeAndReadsServerTimestamp() throws Exception {
        try (var gateway = new HttpPunchGateway(URI.create(base + "/"))) {
            var receipt = gateway.register(PUNCH).get(5, SECONDS);
            assertThat(receipt.employeeId()).isEqualTo("EMP-1");
            assertThat(receipt.placeId()).isEqualTo("CENTRO-1");
            assertThat(receipt.type()).isEqualTo(PunchType.IN);
            assertThat(receipt.timestamp()).isEqualTo(Instant.parse("2026-11-15T09:31:00Z"));
        }
        assertThat(requestMethod.get()).isEqualTo("POST");
        assertThat(contentType.get()).isEqualTo("application/json; charset=UTF-8");
        var json = new ObjectMapper().readTree(requestBody.get());
        assertThat(json.size()).isEqualTo(5);
        assertThat(json.get("employeeId").textValue()).isEqualTo("EMP-1");
        assertThat(json.get("employeeName").textValue()).isEqualTo("José \"Pepe\"");
        assertThat(json.get("placeId").textValue()).isEqualTo("CENTRO-1");
        assertThat(json.get("placeName").textValue()).isEqualTo("Oficina Ñ");
        assertThat(json.get("type").textValue()).isEqualTo("IN");
        assertThat(requests.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"400,BAD_REQUEST", "404,BAD_REQUEST", "401,BAD_REQUEST", "403,BAD_REQUEST",
            "409,CONFLICT", "500,SERVER_ERROR", "503,SERVER_ERROR", "200,INVALID_RESPONSE", "302,INVALID_RESPONSE"})
    void handlesFailuresEvenWithNonJsonBodyWithoutRetry(int code, PunchException.Kind kind) {
        status = code;
        response = "<html>Error del servidor</html>";
        try (var gateway = new HttpPunchGateway(base)) {
            assertThat(awaitFailure(gateway.register(PUNCH)).kind()).isEqualTo(kind);
        }
        assertThat(requests.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"Employee is already checked in,Ya hay una entrada abierta",
            "Cannot check out without a previous entry,No hay una entrada abierta"})
    void explainsSequenceConflictsInSpanish(String message, String explanation) throws Exception {
        status = 409;
        response = new ObjectMapper().writeValueAsString(java.util.Map.of("error", "CONFLICT", "message", message));
        try (var gateway = new HttpPunchGateway(base)) {
            assertThat(awaitFailure(gateway.register(PUNCH))).hasMessageContaining(explanation);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "[]", "{}", "not json",
            "{\"employeeId\":\"EMP-1\",\"placeId\":\"CENTRO-1\",\"type\":\"IN\",\"timestamp\":\"invalid\"}",
            "{\"employeeId\":\"someone-else\",\"placeId\":\"CENTRO-1\",\"type\":\"IN\",\"timestamp\":\"2026-11-15T09:31:00Z\"}",
            "{\"employeeId\":\"EMP-1\",\"placeId\":\"CENTRO-1\",\"type\":\"OUT\",\"timestamp\":\"2026-11-15T09:31:00Z\"}"})
    void neverReportsSuccessForInvalidOrMismatchedReceipts(String body) {
        response = body;
        try (var gateway = new HttpPunchGateway(base)) {
            var error = awaitFailure(gateway.register(PUNCH));
            assertThat(error.kind()).isEqualTo(PunchException.Kind.INVALID_RESPONSE);
            assertThat(error).hasMessageContaining("Consulta los registros antes de repetirlo");
        }
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void timesOutWithoutRetryAndWarnsOfUncertainOutcome() {
        delayMillis = 1000;
        try (var gateway = new HttpPunchGateway(base, Duration.ofMillis(100))) {
            var error = awaitFailure(gateway.register(PUNCH));
            assertThat(error.kind()).isEqualTo(PunchException.Kind.UNAVAILABLE);
            assertThat(error).hasMessageContaining("Consulta los registros antes de repetirlo");
        }
        assertThat(requests.get()).isLessThanOrEqualTo(1);
    }

    @Test
    void explainsConnectionFailure() {
        server.stop(0);
        try (var gateway = new HttpPunchGateway(base)) {
            assertThat(awaitFailure(gateway.register(PUNCH)).kind()).isEqualTo(PunchException.Kind.UNAVAILABLE);
        }
    }

    @Test
    void returnsAPendingFutureBeforeTheServerRespondsAndSendsOnlyOnce() throws Exception {
        responseGate = new CountDownLatch(1);
        try (var gateway = new HttpPunchGateway(base)) {
            var invocation = workers.submit(() -> gateway.register(PUNCH));
            try {
                var result = invocation.get(5, SECONDS);
                assertThat(requestReceived.await(5, SECONDS)).isTrue();
                assertThat(result).isNotDone();

                responseGate.countDown();

                assertThat(result.get(5, SECONDS).type()).isEqualTo(PunchType.IN);
                assertThat(requests.get()).isOne();
            } finally {
                responseGate.countDown();
                invocation.cancel(true);
            }
        } finally {
            responseGate.countDown();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp://localhost", "/localhost", "http://localhost?x=1", "http://localhost#x",
            "http://user:secret@localhost", "http://localhost:0", "http://localhost:70000", "file:///tmp"})
    void rejectsInvalidBaseUrls(String url) {
        assertThatThrownBy(() -> new HttpPunchGateway(URI.create(url))).isInstanceOf(IllegalArgumentException.class);
        assertThat(requests.get()).isZero();
    }

    private static PunchException awaitFailure(CompletableFuture<?> future) {
        Throwable failure = catchThrowable(() -> future.get(5, SECONDS));
        assertThat(failure).isInstanceOf(ExecutionException.class);
        assertThat(failure.getCause()).isInstanceOf(PunchException.class);
        return (PunchException) failure.getCause();
    }
}

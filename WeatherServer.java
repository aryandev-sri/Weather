import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;

public class WeatherServer {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/api/weather", WeatherServer::weather);
        server.createContext("/api/locations", WeatherServer::locations);
        server.createContext("/", WeatherServer::home);
        server.start();
        System.out.println("Weather is running at http://localhost:" + PORT);
    }

    private static void weather(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("GET")) { send(ex, 405, "{\"error\":\"Use GET\"}", "application/json"); return; }
        Map<String, String> q = query(ex.getRequestURI().getRawQuery());
        if (!q.containsKey("latitude") || !q.containsKey("longitude")) {
            send(ex, 400, "{\"error\":\"latitude and longitude are required\"}", "application/json"); return;
        }
        try {
            double lat = Double.parseDouble(q.get("latitude")), lon = Double.parseDouble(q.get("longitude"));
            if (lat < -90 || lat > 90 || lon < -180 || lon > 180) throw new IllegalArgumentException();
            String url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                    + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code,wind_speed_10m"
                    + "&hourly=temperature_2m,precipitation_probability,weather_code"
                    + "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max,precipitation_probability_max"
                    + "&temperature_unit=celsius&wind_speed_unit=kmh&timezone=auto&forecast_days=7";
            proxy(ex, url);
        } catch (IllegalArgumentException e) {
            send(ex, 400, "{\"error\":\"Latitude or longitude is invalid\"}", "application/json");
        }
    }

    private static void locations(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("GET")) { send(ex, 405, "{\"error\":\"Use GET\"}", "application/json"); return; }
        String term = query(ex.getRequestURI().getRawQuery()).getOrDefault("name", "").trim();
        if (term.length() < 2) { send(ex, 400, "{\"error\":\"Enter at least two characters\"}", "application/json"); return; }
        proxy(ex, "https://geocoding-api.open-meteo.com/v1/search?name=" + enc(term) + "&count=6&language=en&format=json");
    }

    private static void proxy(HttpExchange ex, String url) throws IOException {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json").GET().build();
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            send(ex, response.statusCode(), response.body(), "application/json; charset=utf-8");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            send(ex, 502, "{\"error\":\"Weather provider request interrupted\"}", "application/json");
        } catch (Exception e) {
            send(ex, 502, "{\"error\":\"Could not reach Open-Meteo. Please try again.\"}", "application/json");
        }
    }

    private static void home(HttpExchange ex) throws IOException {
        if (!ex.getRequestURI().getPath().equals("/")) { send(ex, 404, "Not found", "text/plain; charset=utf-8"); return; }
        Path file = Path.of("index.html");
        if (!Files.exists(file)) { send(ex, 500, "index.html is missing", "text/plain; charset=utf-8"); return; }
        send(ex, 200, Files.readString(file), "text/html; charset=utf-8");
    }

    private static Map<String, String> query(String raw) {
        if (raw == null || raw.isEmpty()) return Map.of();
        return java.util.Arrays.stream(raw.split("&")).map(s -> s.split("=", 2))
                .collect(java.util.stream.Collectors.toMap(a -> dec(a[0]), a -> a.length > 1 ? dec(a[1]) : "", (a,b) -> b));
    }
    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
    private static String dec(String s) { return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8); }
    private static void send(HttpExchange ex, int status, String body, String type) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, bytes.length);
        try (var out = ex.getResponseBody()) { out.write(bytes); }
    }
}

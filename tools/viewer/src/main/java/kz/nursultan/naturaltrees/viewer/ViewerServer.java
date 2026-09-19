/*
 * Copyright 2026 Nursultan Akim
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package kz.nursultan.naturaltrees.viewer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;
import kz.nursultan.naturaltrees.treecore.FoliageParams;
import kz.nursultan.naturaltrees.treecore.PlacementLimits;
import kz.nursultan.naturaltrees.treecore.TreeGenerator;
import kz.nursultan.naturaltrees.treecore.TreeResult;
import kz.nursultan.naturaltrees.treecore.TrunkParams;
import kz.nursultan.naturaltrees.treecore.WorldRead;

/**
 * The tuning tool of spec section 17: the real generator behind the JDK's {@link HttpServer}, and a static
 * page that draws what it returns. It listens on the loopback address only.
 *
 * <p>{@code POST /generate} takes {@code {"trunk_placer": {…}, "foliage_placer": {…}, "height": n, "seed": n}}
 * and answers with a {@link VoxelBuffer}, or with status 400 and the validation message as text.
 */
public final class ViewerServer {

    /** Species files shipped with the viewer, in menu order. */
    static final String[] SPECIES = {"oak", "fancy_oak", "birch", "tall_birch", "test_28_tips"};

    private static final WorldRead ABOVE_GROUND = (x, y, z) -> y >= 0;

    private final TreeGenerator generator = new TreeGenerator();
    private final HttpServer server;

    ViewerServer(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        // One thread: the generator is not thread-safe, and a request takes well under a millisecond.
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.createContext("/generate", this::generate);
        server.createContext("/species/", this::species);
        server.createContext("/", this::page);
    }

    int port() {
        return server.getAddress().getPort();
    }

    void start() {
        server.start();
    }

    void stop() {
        server.stop(0);
    }

    /** Runs the generator on a request body; shared with the tests. */
    byte[] generate(String body) {
        Map<String, Object> request = PlacerJson.object(MiniJson.parse(body), "request");
        TrunkParams trunk = PlacerJson.trunk(PlacerJson.object(request.get("trunk_placer"), "trunk_placer"));
        FoliageParams foliage = request.get("foliage_placer") == null ? null
                : PlacerJson.foliage(PlacerJson.object(request.get("foliage_placer"), "foliage_placer"));
        int height = PlacerJson.integer(request, "height", null);
        // Seeds travel as decimal strings: a 64-bit seed does not fit a JSON number.
        Object seedValue = request.get("seed");
        long seed = seedValue instanceof String s ? Long.parseLong(s) : (long) PlacerJson.number(request, "seed", null);
        TreeResult result = generator.generate(trunk, foliage, seed, height, PlacementLimits.NONE, ABOVE_GROUND, ABOVE_GROUND);
        return VoxelBuffer.encode(result);
    }

    private void generate(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!"POST".equals(exchange.getRequestMethod())) {
                send(exchange, 405, "text/plain", "POST only".getBytes(StandardCharsets.UTF_8));
                return;
            }
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] answer;
            try {
                answer = generate(body);
            } catch (IllegalArgumentException e) {
                send(exchange, 400, "text/plain", String.valueOf(e.getMessage()).getBytes(StandardCharsets.UTF_8));
                return;
            }
            send(exchange, 200, "application/octet-stream", answer);
        }
    }

    private void species(HttpExchange exchange) throws IOException {
        try (exchange) {
            String name = exchange.getRequestURI().getPath().substring("/species/".length());
            if (name.equals("index.json")) {
                StringBuilder list = new StringBuilder("[");
                for (int i = 0; i < SPECIES.length; i++) {
                    list.append(i == 0 ? "" : ",").append('"').append(SPECIES[i]).append('"');
                }
                send(exchange, 200, "application/json", list.append(']').toString().getBytes(StandardCharsets.UTF_8));
                return;
            }
            for (String known : SPECIES) {
                if (name.equals(known + ".json")) {
                    send(exchange, 200, "application/json", resource("/species/" + known + ".json"));
                    return;
                }
            }
            send(exchange, 404, "text/plain", "no such species".getBytes(StandardCharsets.UTF_8));
        }
    }

    private void page(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/") || path.equals("/index.html")) {
                send(exchange, 200, "text/html; charset=utf-8", resource("/web/index.html"));
            } else {
                send(exchange, 404, "text/plain", "not found".getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    static byte[] resource(String path) throws IOException {
        try (InputStream in = ViewerServer.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("missing resource " + path);
            }
            return in.readAllBytes();
        }
    }

    private static void send(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8765;
        ViewerServer viewer = new ViewerServer(port);
        viewer.start();
        System.out.println("Natural Trees viewer: http://localhost:" + viewer.port() + "/   (Ctrl+C stops it)");
    }
}

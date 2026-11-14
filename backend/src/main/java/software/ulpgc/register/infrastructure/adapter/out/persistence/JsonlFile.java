
package software.ulpgc.register.infrastructure.adapter.out.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class JsonlFile {

    private final Path path;
    private final ObjectMapper mapper = new ObjectMapper();

    public JsonlFile(Path path) {
        this.path = Objects.requireNonNull(path);
    }

    public ObjectNode newObject() {
        return mapper.createObjectNode();
    }

    public synchronized void append(ObjectNode value) {
        Objects.requireNonNull(value);

        try {
            Files.createDirectories(
                    path.toAbsolutePath().getParent()
            );

            Files.writeString(
                    path,
                    mapper.writeValueAsString(value) + "\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Cannot persist data in " + path, e
            );
        }
    }

    public synchronized List<JsonNode> readAll() {
        if (Files.notExists(path)) {
            return List.of();
        }

        try {
            List<JsonNode> result = new ArrayList<>();

            for (String line : Files.readAllLines(
                    path, StandardCharsets.UTF_8
            )) {
                if (line.isBlank()) {
                    continue;
                }

                JsonNode node = mapper.readTree(line);

                if (node == null || !node.isObject()) {
                    throw new IllegalStateException(
                            "Invalid JSONL record in " + path
                    );
                }

                result.add(node);
            }

            return List.copyOf(result);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Cannot read data from " + path, e
            );
        }
    }
}

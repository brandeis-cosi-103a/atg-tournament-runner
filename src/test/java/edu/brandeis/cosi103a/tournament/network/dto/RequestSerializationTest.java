package edu.brandeis.cosi103a.tournament.network.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import edu.brandeis.cosi103a.tournament.network.config.ObjectMapperFactory;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The assignment spec defines the player UUID field as snake_case
 * <code>player_uuid</code>. Several student servers validate that the
 * field is present and non-blank; sending camelCase
 * <code>playerUuid</code> caused those servers to 400 every request.
 */
class RequestSerializationTest {

    private static final ObjectMapper MAPPER = ObjectMapperFactory.create();

    @Test
    void decisionRequest_emitsSnakeCasePlayerUuid() throws Exception {
        DecisionRequest req = new DecisionRequest(null, ImmutableList.of(), Optional.empty(), "abc-123");
        String json = MAPPER.writeValueAsString(req);
        assertTrue(json.contains("\"player_uuid\":\"abc-123\""),
            "Expected snake_case player_uuid, got: " + json);
        assertFalse(json.contains("\"playerUuid\""),
            "Should not emit camelCase playerUuid: " + json);
    }

    @Test
    void logEventRequest_emitsSnakeCasePlayerUuid() throws Exception {
        LogEventRequest req = new LogEventRequest(null, null, "abc-123");
        String json = MAPPER.writeValueAsString(req);
        assertTrue(json.contains("\"player_uuid\":\"abc-123\""),
            "Expected snake_case player_uuid, got: " + json);
        assertFalse(json.contains("\"playerUuid\""),
            "Should not emit camelCase playerUuid: " + json);
    }
}

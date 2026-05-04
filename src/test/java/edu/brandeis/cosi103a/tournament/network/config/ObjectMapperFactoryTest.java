package edu.brandeis.cosi103a.tournament.network.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.brandeis.cosi103a.tournament.network.dto.DecisionResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ObjectMapperFactoryTest {

    /**
     * Regression: a real-world tournament had a player whose /decide returned a 200
     * with extra wrapper fields ("@class" from Jackson Default Typing, plus a
     * custom "metadata" string). The runner failed deserialization on the unknown
     * fields, treating a successful response as a forfeit. The runner's mapper
     * should be lenient: as long as the documented fields are valid, accept.
     */
    @Test
    void deserializesDecisionResponseWithUnknownWrapperFields() throws Exception {
        ObjectMapper mapper = ObjectMapperFactory.create();
        String body = "{\"@class\":\"foo.Bar\","
                + "\"decision\":{\"type\":\"end_phase\",\"phase\":\"BUY\"},"
                + "\"metadata\":\"Using V3StrategyPlayer strategy\"}";

        DecisionResponse response = mapper.readValue(body, DecisionResponse.class);

        assertNotNull(response.getDecision());
    }
}

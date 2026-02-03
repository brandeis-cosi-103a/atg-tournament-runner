package edu.brandeis.cosi103a.tournament.network.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

/**
 * Shared ObjectMapper factory for consistent JSON serialization
 * across network client modules.
 */
public final class ObjectMapperFactory {

    private ObjectMapperFactory() {
        // Utility class
    }

    /**
     * Creates a pre-configured ObjectMapper with Guava and JDK8 module support.
     *
     * @return a new ObjectMapper instance
     */
    public static ObjectMapper create() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new GuavaModule());
        mapper.registerModule(new Jdk8Module());
        return mapper;
    }
}

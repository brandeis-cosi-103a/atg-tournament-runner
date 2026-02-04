package edu.brandeis.cosi103a.tournament.runner;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Optional annotation to provide a human-readable description for a Player implementation.
 * Used by PlayerDiscoveryService when scanning the classpath.
 *
 * <p>Example usage:
 * <pre>
 * {@literal @}PlayerDescription("A greedy strategy that prioritizes money cards")
 * public class BigMoneyPlayer implements Player {
 *     // ...
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface PlayerDescription {
    /**
     * A human-readable description of the player's strategy.
     */
    String value();
}

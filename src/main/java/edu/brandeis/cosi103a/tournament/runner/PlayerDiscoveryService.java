package edu.brandeis.cosi103a.tournament.runner;

import edu.brandeis.cosi.atg.player.Player;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for discovering Player implementations on the classpath.
 * Scans for classes implementing {@link edu.brandeis.cosi.atg.player.Player}
 * and caches their metadata for fast lookup.
 */
@Service
public class PlayerDiscoveryService {

    private final Map<String, DiscoveredPlayer> playersByClassName = new ConcurrentHashMap<>();
    private volatile List<DiscoveredPlayer> cachedPlayers = List.of();

    /**
     * Creates a new PlayerDiscoveryService.
     */
    public PlayerDiscoveryService() {
    }

    /**
     * Performs initial classpath scan on startup.
     */
    @PostConstruct
    public void initialize() {
        scanClasspath();
    }

    /**
     * Scans the application classpath for Player implementations.
     */
    public synchronized void scanClasspath() {
        List<RawPlayerInfo> rawPlayers = new ArrayList<>();

        try (ScanResult result = new ClassGraph()
                .enableClassInfo()
                .enableAnnotationInfo()
                .scan()) {

            for (ClassInfo classInfo : result.getClassesImplementing(Player.class.getName())) {
                // Skip interfaces and abstract classes
                if (classInfo.isInterface() || classInfo.isAbstract()) {
                    continue;
                }

                String className = classInfo.getName();
                try {
                    Class<?> playerClass = Class.forName(className);
                    RawPlayerInfo info = analyzePlayerClass(playerClass);
                    if (info.hasZeroArg || info.hasNameConstructor) {
                        rawPlayers.add(info);
                    }
                } catch (Exception e) {
                    // Skip classes that can't be loaded (e.g., missing dependencies)
                    System.err.println("Warning: Could not analyze Player class " + className + ": " + e.getMessage());
                }
            }
        }

        // Count occurrences of each simple name to detect duplicates
        Map<String, Integer> simpleNameCounts = new HashMap<>();
        for (RawPlayerInfo info : rawPlayers) {
            simpleNameCounts.merge(info.simpleName, 1, Integer::sum);
        }

        // Build final DiscoveredPlayer list with display names
        playersByClassName.clear();
        for (RawPlayerInfo info : rawPlayers) {
            boolean hasDuplicate = simpleNameCounts.get(info.simpleName) > 1;
            String displayName = hasDuplicate ? info.className : info.simpleName;

            DiscoveredPlayer player = new DiscoveredPlayer(
                info.simpleName,
                info.className,
                displayName,
                info.description,
                info.hasZeroArg,
                info.hasNameConstructor
            );
            playersByClassName.put(info.className, player);
        }

        // Rebuild the sorted list
        List<DiscoveredPlayer> allPlayers = new ArrayList<>(playersByClassName.values());
        allPlayers.sort(Comparator.comparing(DiscoveredPlayer::displayName));
        cachedPlayers = List.copyOf(allPlayers);
    }

    /**
     * Intermediate holder for player info before display name resolution.
     */
    private record RawPlayerInfo(
        String simpleName,
        String className,
        String description,
        boolean hasZeroArg,
        boolean hasNameConstructor
    ) {}

    /**
     * Analyzes a Player class to extract its metadata.
     */
    private RawPlayerInfo analyzePlayerClass(Class<?> playerClass) {
        String className = playerClass.getName();
        String simpleName = playerClass.getSimpleName();

        // Check for description annotation
        String description = "";
        PlayerDescription descAnnotation = playerClass.getAnnotation(PlayerDescription.class);
        if (descAnnotation != null) {
            description = descAnnotation.value();
        }

        // Check for constructors
        boolean hasZeroArg = false;
        boolean hasNameConstructor = false;

        for (Constructor<?> ctor : playerClass.getConstructors()) {
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length == 0) {
                hasZeroArg = true;
            } else if (params.length == 1 && params[0] == String.class) {
                hasNameConstructor = true;
            }
        }

        return new RawPlayerInfo(simpleName, className, description, hasZeroArg, hasNameConstructor);
    }

    /**
     * Returns all discovered players, sorted alphabetically by display name.
     */
    public List<DiscoveredPlayer> getDiscoveredPlayers() {
        return cachedPlayers;
    }

    /**
     * Finds a discovered player by class name.
     *
     * @param className the fully-qualified class name
     * @return the discovered player, or empty if not found
     */
    public Optional<DiscoveredPlayer> findByClassName(String className) {
        return Optional.ofNullable(playersByClassName.get(className));
    }

    /**
     * Creates a Player instance from a discovered player.
     * Prefers the String (name) constructor if available, otherwise uses the zero-arg constructor.
     *
     * @param discovered the discovered player metadata
     * @param name       the player name to use
     * @return a new Player instance
     * @throws ReflectiveOperationException if instantiation fails
     */
    public Player createPlayer(DiscoveredPlayer discovered, String name) throws ReflectiveOperationException {
        Class<?> playerClass = Class.forName(discovered.className());

        // Prefer name constructor if available
        if (discovered.hasNameConstructor()) {
            Constructor<?> ctor = playerClass.getConstructor(String.class);
            return (Player) ctor.newInstance(name);
        }

        // Fall back to zero-arg constructor
        if (discovered.hasZeroArgConstructor()) {
            Constructor<?> ctor = playerClass.getConstructor();
            return (Player) ctor.newInstance();
        }

        throw new IllegalStateException("No suitable constructor found for " + discovered.className());
    }

    /**
     * Clears all discovered players. Primarily for testing.
     */
    public synchronized void clear() {
        playersByClassName.clear();
        cachedPlayers = List.of();
    }
}

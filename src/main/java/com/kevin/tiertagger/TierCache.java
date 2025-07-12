package com.kevin.tiertagger;

import com.kevin.tiertagger.model.GameMode;
import com.kevin.tiertagger.model.PlayerInfo;
import com.kevin.tiertagger.model.PlayerList;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class TierCache {
    public static final List<GameMode> GAMEMODES = new ArrayList<>();
    private static final Map<UUID, Optional<PlayerInfo>> TIERS = new ConcurrentHashMap<>();
    public static Map<UUID, Optional<PlayerInfo>> getTIERS() {
        return TIERS;
    }

    private static Map<String, Optional<PlayerInfo>> TIERS_NAME = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<PlayerInfo>> PENDING_REQUESTS = new ConcurrentHashMap<>();
    private static final AtomicBoolean FETCH_UNKNOWN = new AtomicBoolean(true);
    
    private static final Map<String, Long> LAST_REQUEST_TIME = new ConcurrentHashMap<>();
    private static final long REQUEST_COOLDOWN = 5000;

    public static void init() {
        TierTagger.getLogger().info("Inciando TierCache");
        PlayerList.get(TierTagger.getClient()).thenAccept(list -> {
            Map<UUID, Optional<PlayerInfo>> players = list.players().stream().collect(Collectors.toMap(p -> parseUUID(p.uuid()), Optional::of));
            Map<UUID, Optional<PlayerInfo>> unknown = list.unknown().stream().collect(Collectors.toMap(u -> u, u -> Optional.empty()));

            TIERS.clear();
            TIERS.putAll(players);
            TIERS.putAll(unknown);

            if (list.fetchUnknown() != null) {
                FETCH_UNKNOWN.set(list.fetchUnknown());
                if (Boolean.FALSE.equals(list.fetchUnknown())) {
                    TierTagger.getLogger().warn("The remote API set `fetchUnknown` to false! Make sure you are using a tierlist that supports this feature!");
                }
            }

            TierTagger.getLogger().info("Loaded {} players and {} unknown", players.size(), unknown.size());
        });

        try {
            GAMEMODES.clear();
            GAMEMODES.addAll(GameMode.fetchGamemodes(TierTagger.getClient()).get());
            TierTagger.getLogger().info("Found {} tierlists: {}", GAMEMODES.size(), GAMEMODES.stream().map(GameMode::id).toList());
        } catch (ExecutionException e) {
            TierTagger.getLogger().error("Failed to load gamemodes!", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static Optional<PlayerInfo> getPlayerInfo(UUID uuid) {
        String baseUrl = TierTagger.getManager().getConfig().getBaseUrl();
        boolean isSATiers = baseUrl.contains("too-butler.gl.at.ply.gg:1247/api/profile");
        if (isSATiers) {
            return getPlayerInfoByName(getPlayerNameByUUID(uuid));
        }
        if (FETCH_UNKNOWN.get()) {
            return TIERS.computeIfAbsent(uuid, u -> {
                if (uuid.version() == 4) {
                    PlayerInfo.get(TierTagger.getClient(), uuid).thenAccept(info -> TIERS.put(uuid, Optional.ofNullable(info)));
                }
                return Optional.empty();
            });
        } else {
            return TIERS.getOrDefault(uuid, Optional.empty());
        }
    }

    public static Optional<PlayerInfo> getPlayerInfoByName(String name) {
        if (name == null) return Optional.empty();
        String normalizedName = name.toLowerCase();
        return TIERS_NAME.getOrDefault(normalizedName, Optional.empty());
    }

    public static String getPlayerNameByUUID(UUID uuid) {
        Optional<PlayerInfo> opt = TIERS.get(uuid);
        if (opt != null && opt.isPresent()) {
            return opt.get().name();
        }
        return null;
    }

    public static CompletableFuture<PlayerInfo> searchPlayer(String query) {
        if (query == null || query.trim().isEmpty() || !isValidPlayerName(query)) {
            return CompletableFuture.completedFuture(null);
        }
        
        String normalizedQuery = query.toLowerCase().trim();
        
        Optional<PlayerInfo> cached = TIERS_NAME.get(normalizedQuery);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached.orElse(null));
        }

        long currentTime = System.currentTimeMillis();
        Long lastRequest = LAST_REQUEST_TIME.get(normalizedQuery);
        if (lastRequest != null && (currentTime - lastRequest) < REQUEST_COOLDOWN) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<PlayerInfo> pendingRequest = PENDING_REQUESTS.get(normalizedQuery);
        if (pendingRequest != null) {
            return pendingRequest;
        }
        
        LAST_REQUEST_TIME.put(normalizedQuery, currentTime);
        TierTagger.getLogger().info("Fazendo nova requisicao: {}", normalizedQuery);
        
        CompletableFuture<PlayerInfo> newRequest = PlayerInfo.search(TierTagger.getClient(), query).thenApply(p -> {
            if (p == null) {
                TierTagger.getLogger().warn("SATiers PlayerInfo retornou null: {}", query);
                TIERS_NAME.put(normalizedQuery, Optional.empty());
                return null;
            }
            String baseUrl2 = TierTagger.getManager().getConfig().getBaseUrl();
            boolean isSATiers = baseUrl2.contains("too-butler.gl.at.ply.gg:1247/api/profile");
            UUID uuid = parseUUID(p.uuid());
            if (isSATiers) {
                TIERS_NAME.put(p.name().toLowerCase(), Optional.of(p));
                TierTagger.getLogger().info("[SATiers] Registrando player {} (por nickname) no cache", p.name());
                TierTagger.getLogger().info("[SATiers] Cache agora tem {} coisos", TIERS_NAME.size());
            } else {
                TierTagger.getLogger().info("[SATiers] registrando player {} com uuid {} no cache", p.name(), uuid);
                TIERS.put(uuid, Optional.of(p));
            }
            return p;
        }).whenComplete((result, throwable) -> {
            PENDING_REQUESTS.remove(normalizedQuery);
        });
        
        PENDING_REQUESTS.put(normalizedQuery, newRequest);
        
        return newRequest;
    }

    public static void clearCache() {
        TIERS.clear();
        TIERS_NAME.clear();
        PENDING_REQUESTS.clear();
        LAST_REQUEST_TIME.clear();
    }

    public static GameMode findNextMode(GameMode current) {
        return GAMEMODES.get((GAMEMODES.indexOf(current) + 1) % GAMEMODES.size());
    }

    public static GameMode findMode(String id) {
        return GAMEMODES.stream().filter(m -> m.id().equalsIgnoreCase(id)).findFirst()
                .orElseGet(() -> new com.kevin.tiertagger.model.GameMode("vanilla", "Vanilla"));
    }

    public static boolean isValidPlayerName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        
        String illegalChars = "§ &?#%+=@!$'()*,;:\"<>[]\\^`{}|~";
        
        return name.chars().noneMatch(c -> illegalChars.indexOf(c) != -1);
    }

    private static UUID parseUUID(String uuid) {
        try {
            return UUID.fromString(uuid);
        } catch (Exception e) {
            long mostSignificant = Long.parseUnsignedLong(uuid.substring(0, 16), 16);
            long leastSignificant = Long.parseUnsignedLong(uuid.substring(16), 16);
            return new UUID(mostSignificant, leastSignificant);
        }
    }

    private TierCache() {
    }
}

package com.kevin.tiertagger.model;

import com.google.gson.annotations.SerializedName;
import com.kevin.tiertagger.TierCache;
import com.kevin.tiertagger.TierTagger;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public record PlayerInfo(String uuid, String name, Map<String, Ranking> rankings, String region, int points,
                         int overall, List<Badge> badges, @SerializedName("combat_master") boolean combatMaster) {
    public record Ranking(int tier, int pos, @Nullable @SerializedName("peak_tier") Integer peakTier,
                          @Nullable @SerializedName("peak_pos") Integer peakPos, long attained,
                          boolean retired) {

        /**
         * Lower is better.
         */
        public int comparableTier() {
            return tier * 2 + pos;
        }

        /**
         * Lower is better.
         */
        public int comparablePeak() {
            if (peakTier == null || peakPos == null) {
                return Integer.MAX_VALUE;
            } else {
                return peakTier * 2 + peakPos;
            }
        }

        public NamedRanking asNamed(GameMode mode) {
            return new NamedRanking(mode, this);
        }
    }

    public record NamedRanking(@Nullable GameMode mode, Ranking ranking) {
    }

    public record Badge(String title, String desc) {
    }

    private static final Map<String, Integer> REGION_COLORS = Map.of(
            "NA", 0xff6a6e,
            "EU", 0x6aff6e,
            "SA", 0xff9900,
            "AU", 0xf6b26b,
            "ME", 0xffd966,
            "AS", 0xc27ba0,
            "AF", 0x674ea7
    );

    public static CompletableFuture<PlayerInfo> get(HttpClient client, UUID uuid) {
        String endpoint = TierTagger.getManager().getConfig().getBaseUrl() + "/profile/" + uuid.toString().replace("-", "");
        final HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).GET().build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(s -> TierTagger.GSON.fromJson(s, PlayerInfo.class))
                .whenComplete((i, t) -> {
                    if (t != null) TierTagger.getLogger().warn("Error getting player info ({})", uuid, t);
                });
    }

    public static CompletableFuture<PlayerInfo> search(HttpClient client, String query) {
        String baseUrl = TierTagger.getManager().getConfig().getBaseUrl();
        boolean isSATiers = baseUrl.contains("too-butler.gl.at.ply.gg:1247/api/profile");
        String endpoint;
        if (isSATiers) {
            endpoint = baseUrl + "/" + query;
            com.kevin.tiertagger.TierTagger.getLogger().info("[SATiers] Endpoint usado: {}", endpoint);
        } else {
            endpoint = baseUrl + "/search_profile/" + query;
        }
        final HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).GET().build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(s -> {
                    if (isSATiers) {
                        com.kevin.tiertagger.TierTagger.getLogger().info("[SATiers] Body response: {}", s);

                        try {
                            com.google.gson.JsonObject obj = com.kevin.tiertagger.TierTagger.GSON.fromJson(s, com.google.gson.JsonObject.class);
                            com.kevin.tiertagger.TierTagger.getLogger().info("[SATiers] JSON parsed: {}", obj);
                            if (obj == null || !obj.has("data") || obj.get("data").isJsonNull()) {
                                com.kevin.tiertagger.TierTagger.getLogger().error("SATiers resposta invalida (sem data): {}", s);
                                return null;
                            }
                            com.google.gson.JsonObject data = obj.getAsJsonObject("data");
                            com.kevin.tiertagger.TierTagger.getLogger().info("[SATiers] JSON data: {}", data);
                            if (data == null || !data.has("jogador") || !data.has("ranking") || data.get("jogador").isJsonNull() || data.get("ranking").isJsonNull()) {
                                com.kevin.tiertagger.TierTagger.getLogger().error("SATiers resposta invalida (faltando jogador/ranking): {}", s);
                                return null;
                            }
                            String username = data.get("jogador").getAsString();
                            String tier = data.get("ranking").getAsString();
                            com.kevin.tiertagger.TierTagger.getLogger().info("[SATiers] username: {}, tier: {}", username, tier);
                            if (username == null || tier == null) {
                                com.kevin.tiertagger.TierTagger.getLogger().error("SATiers resposta invalida (username/tier nulo): {}", s);
                                return null;
                            }
                            java.util.Map<String, Ranking> rankings = new java.util.HashMap<>();
                            rankings.put("vanilla", new Ranking(parseTier(tier), parsePos(tier), null, null, 0L, false));
                            java.util.UUID fakeUuid = java.util.UUID.nameUUIDFromBytes(("SATiers:" + username).getBytes());
return new PlayerInfo(fakeUuid.toString(), username, rankings, "SA", 0, 0, java.util.Collections.emptyList(), false);
                        } catch (Exception e) {
                            com.kevin.tiertagger.TierTagger.getLogger().error("SATiers Excecao ao parsear resposta: {}", s, e);
                            return null;
                        }
                    }
                    return TierTagger.GSON.fromJson(s, PlayerInfo.class);
                })
                .whenComplete((i, t) -> {
                    if (t != null) TierTagger.getLogger().warn("Error searching player {}", query, t);
                });
    }


    private static int parseTier(String tier) {
        if (tier == null) return 5;
        if (tier.toLowerCase().contains("1")) return 1;
        if (tier.toLowerCase().contains("2")) return 2;
        if (tier.toLowerCase().contains("3")) return 3;
        if (tier.toLowerCase().contains("4")) return 4;
        if (tier.toLowerCase().contains("5")) return 5;
        return 5;
    }
    private static int parsePos(String tier) {
        if (tier == null) return 1;
        if (tier.toLowerCase().contains("high")) return 0;
        if (tier.toLowerCase().contains("low")) return 1;
        return 1;
    }

    public int getRegionColor() {
        return REGION_COLORS.getOrDefault(this.region.toUpperCase(Locale.ROOT), 0xffffff);
    }

    public Optional<NamedRanking> getHighestRanking() {
        return this.rankings.entrySet().stream()
                .filter(e -> e.getKey() != null)
                .min(Comparator.comparingInt(e -> e.getValue().comparableTier()))
                .map(e -> e.getValue().asNamed(TierCache.findMode(e.getKey())));
    }

    @Getter
    @AllArgsConstructor
    public enum PointInfo {
        COMBAT_MASTER("Combat Master", 0xFBB03B, 0xFFD13A),
        COMBAT_ACE("Combat Ace", 0xCD285C, 0xD65474),
        COMBAT_SPECIALIST("Combat Specialist", 0xAD78D8, 0xC7A3E8),
        COMBAT_CADET("Combat Cadet", 0x9291D9, 0xADACE2),
        COMBAT_NOVICE("Combat Novice", 0x9291D9, 0xFFFFFF),
        ROOKIE("Rookie", 0x6C7178, 0x8B979C),
        UNRANKED("Unranked", 0xFFFFFF, 0xFFFFFF);

        private final String title;
        private final int color;
        private final int accentColor;
    }

    public PointInfo getPointInfo() {
        if (this.combatMaster || this.points >= 200 && this.rankings.values().stream().allMatch(r -> r.tier <= 2 || (r.peakTier != null && r.peakTier <= 2))) {
            return PointInfo.COMBAT_MASTER;
        } else if (this.points >= 100) {
            return PointInfo.COMBAT_ACE;
        } else if (this.points >= 50) {
            return PointInfo.COMBAT_SPECIALIST;
        } else if (this.points >= 20) {
            return PointInfo.COMBAT_CADET;
        } else if (this.points >= 10) {
            return PointInfo.COMBAT_NOVICE;
        } else if (this.points >= 1) {
            return PointInfo.ROOKIE;
        } else {
            return PointInfo.UNRANKED;
        }
    }

    public List<NamedRanking> getSortedTiers() {
        List<NamedRanking> tiers = new ArrayList<>(this.rankings.entrySet().stream()
                .map(e -> e.getValue().asNamed(TierCache.findMode(e.getKey())))
                .toList());

        if (tiers.isEmpty() && this.rankings.containsKey("vanilla")) {
            tiers.add(this.rankings.get("vanilla").asNamed(TierCache.findMode("vanilla")));
        }

        tiers.sort(Comparator.comparing((NamedRanking a) -> a.ranking.retired, Boolean::compare)
                .thenComparingInt(a -> a.ranking.tier)
                .thenComparingInt(a -> a.ranking.pos));

        return tiers;
    }
}
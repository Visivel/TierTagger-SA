package com.kevin.tiertagger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kevin.tiertagger.config.TierTaggerConfig;
import com.kevin.tiertagger.model.GameMode;
import com.kevin.tiertagger.model.PlayerInfo;
import com.mojang.brigadier.context.CommandContext;
import lombok.Getter;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.minecraft.SharedConstants;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.uku3lig.ukulib.config.ConfigManager;
import net.uku3lig.ukulib.utils.PlayerArgumentType;
import net.uku3lig.ukulib.utils.Ukutils;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class TierTagger implements ModInitializer {
    public static final String MOD_ID = "tiertagger";
    private static final String UPDATE_URL_FORMAT = "https://api.modrinth.com/v2/project/iy2V1aSI/version?game_versions=%s";

    public static final Gson GSON = new GsonBuilder().create();

    @Getter
    private static final ConfigManager<TierTaggerConfig> manager = ConfigManager.createDefault(TierTaggerConfig.class, MOD_ID);
    @Getter
    private static final Logger logger = LoggerFactory.getLogger(TierTagger.class);
    @Getter
    private static final HttpClient client = HttpClient.newHttpClient();

    // === version checker stuff ===
    @Getter
    private static Version latestVersion = null;
    private static final AtomicBoolean isObsolete = new AtomicBoolean(false);

    @Override
    public void onInitialize() {
        TierCache.init();
        AutoSwitchManager.init();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> dispatcher.register(
                literal(MOD_ID)
                        .then(argument("player", PlayerArgumentType.player())
                                .executes(TierTagger::displayTierInfo))));

        Ukutils.registerKeybinding(new KeyBinding("tiertagger.keybind.gamemode", GLFW.GLFW_KEY_UNKNOWN, "tiertagger.name"),
                mc -> {
                    GameMode next = TierCache.findNextMode(manager.getConfig().getGameMode());
                    manager.getConfig().setGameMode(next.id());

                    if (mc.player != null) {
                        Text message = Text.literal("Displayed gamemode: ").append(next.asStyled(false));
                        mc.player.sendMessage(message, true);
                    }
                });

        AutoSwitchManager.fetchServerList();
        checkForUpdates();
    }

    public static Text appendTier(PlayerEntity player, Text text) {
        MutableText following = null;
        String baseUrl = manager.getConfig().getBaseUrl();
        boolean isSATiers = baseUrl.contains("too-butler.gl.at.ply.gg:1247/api/profile");
        switch (manager.getConfig().getShownStatistic()) {
            case TIER -> {
                Optional<PlayerInfo.NamedRanking> opt;
                if (isSATiers) {
                    String nickname = player.getName().getString();
                    if (!TierCache.isValidPlayerName(nickname)) {
                        return text;
                    }
                    
                    Optional<PlayerInfo> infoOpt = TierCache.getPlayerInfoByName(nickname.toLowerCase());
                    if (infoOpt.isPresent()) {
                        following = createTierText(infoOpt.get());
                    } else {
                        TierCache.searchPlayer(nickname);
                    }
                } else {
                    opt = getPlayerTier(player.getUuid());
                    if (opt.isPresent()) {
                        PlayerInfo.NamedRanking entry = opt.get();
                        String tier = getTierText(entry.ranking());
                        MutableText tierText = Text.literal(tier).withColor(getTierColor(tier));
                        if (entry.mode() != null && entry.mode().icon().isPresent() && manager.getConfig().isShowIcons()) {
                            following = Text.literal(entry.mode().icon().get().toString()).append(tierText);
                        } else {
                            following = tierText;
                        }
                    }
                }
            }
            case RANK -> {
                following = TierCache.getPlayerInfo(player.getUuid())
                        .map(i -> Text.literal("#" + i.overall()))
                        .orElse(null);
            }
        }

        if (following != null) {
            following.append(Text.literal(" | ").formatted(Formatting.GRAY));
            return following.append(text);
        }

        return text;
    }

    public static Optional<PlayerInfo.NamedRanking> getPlayerTier(UUID uuid) {
        GameMode mode = manager.getConfig().getGameMode();

        return TierCache.getPlayerInfo(uuid)
                .map(info -> {
                    PlayerInfo.Ranking ranking = info.rankings().get(mode.id());
                    Optional<PlayerInfo.NamedRanking> highest = info.getHighestRanking();
                    TierTaggerConfig.HighestMode highestMode = manager.getConfig().getHighestMode();

                    if (ranking == null) {
                        if (info.rankings().size() == 1 && info.rankings().containsKey("vanilla")) {
                            PlayerInfo.Ranking vanillaRanking = info.rankings().get("vanilla");
                            return vanillaRanking.asNamed(TierCache.findMode("vanilla"));
                        }
                        if (highestMode != TierTaggerConfig.HighestMode.NEVER && highest.isPresent()) {
                            return highest.get();
                        } else {
                            return null;
                        }
                    } else {
                        if (highestMode == TierTaggerConfig.HighestMode.ALWAYS && highest.isPresent()) {
                            return highest.get();
                        } else {
                            return ranking.asNamed(mode);
                        }
                    }
                });
    }

    @NotNull
    public static String getTierText(PlayerInfo.Ranking ranking) {
        if (manager.getConfig().isShowRetired() && ranking.retired() && ranking.peakTier() != null && ranking.peakPos() != null) {
            return "R" + (ranking.peakPos() == 0 ? "H" : "L") + "T" + ranking.peakTier();
        } else {
            return (ranking.pos() == 0 ? "H" : "L") + "T" + ranking.tier();
        }
    }

    private static MutableText createTierText(PlayerInfo info) {
        if (info.rankings().size() == 1 && info.rankings().containsKey("vanilla")) {
            GameMode mode = TierCache.findMode("vanilla");
            PlayerInfo.Ranking r = info.rankings().get("vanilla");
            String tier = getTierText(r);
            MutableText tierText = Text.literal(tier).withColor(getTierColor(tier));
            if (mode != null && mode.icon().isPresent() && manager.getConfig().isShowIcons()) {
                return Text.literal(mode.icon().get().toString()).append(tierText);
            } else {
                return tierText;
            }
        } else {
            for (var entry : info.rankings().entrySet()) {
                GameMode mode = TierCache.findMode(entry.getKey());
                PlayerInfo.Ranking r = entry.getValue();
                String tier = getTierText(r);
                MutableText tierText = Text.literal(tier).withColor(getTierColor(tier));
                if (mode != null && mode.icon().isPresent() && manager.getConfig().isShowIcons()) {
                    return Text.literal(mode.icon().get().toString()).append(tierText);
                } else {
                    return tierText;
                }
            }
        }
        return null;
    }

    private static int displayTierInfo(CommandContext<FabricClientCommandSource> ctx) {
        PlayerArgumentType.PlayerSelector selector = ctx.getArgument("player", PlayerArgumentType.PlayerSelector.class);

        Optional<PlayerInfo> info = ctx.getSource().getWorld().getPlayers().stream()
                .filter(p -> p.getNameForScoreboard().equalsIgnoreCase(selector.name()) || p.getUuidAsString().equalsIgnoreCase(selector.name()))
                .findFirst()
                .map(Entity::getUuid)
                .flatMap(TierCache::getPlayerInfo);

        if (info.isPresent()) {
            ctx.getSource().sendFeedback(printPlayerInfo(info.get()));
        } else {
            ctx.getSource().sendFeedback(Text.of("[TierTagger] Searching..."));
            TierCache.searchPlayer(selector.name())
                    .thenAccept(p -> {
                        if (p == null) {
                            TierTagger.getLogger().warn("SATiers PlayerInfo retornado pelo searchPlayer deu null no {}", selector.name());
                            ctx.getSource().sendError(Text.of("Could not find player " + selector.name()));
                        } else {
                            TierTagger.getLogger().info("[SATiers] return do PlayerInfo: {}", p);
                            ctx.getSource().sendFeedback(printPlayerInfo(p));
                        }
                    })
                    .exceptionally(t -> {
                        ctx.getSource().sendError(Text.of("Could not find player " + selector.name()));
                        return null;
                    });
        }

        return 0;
    }

    private static Text printPlayerInfo(PlayerInfo info) {
        MutableText text = Text.empty().append("=== Rankings do " + info.name() + " ===");
        if (info.rankings().size() == 1 && info.rankings().containsKey("vanilla")) {
            GameMode mode = TierCache.findMode("vanilla");
            PlayerInfo.Ranking r = info.rankings().get("vanilla");
            String tier = getTierText(r);
            Text tierText = Text.literal(tier).styled(s -> s.withColor(getTierColor(tier)));
            text.append(Text.literal("\n").append(mode.asStyled(true)).append(": ").append(tierText));
            return text;
        }
        info.rankings().forEach((m, r) -> {
            if (m == null) return;
            GameMode mode = TierCache.findMode(m);
            String tier = getTierText(r);

            Text tierText = Text.literal(tier).styled(s -> s.withColor(getTierColor(tier)));
            text.append(Text.literal("\n").append(mode.asStyled(true)).append(": ").append(tierText));
        });

        return text;
    }

    public static int getTierColor(String tier) {
        if (tier.startsWith("R")) {
            return manager.getConfig().getRetiredColor();
        } else {
            return manager.getConfig().getTierColors().getOrDefault(tier, 0xD3D3D3);
        }
    }

    private static void checkForUpdates() {
        String versionParam = "[\"%s\"]".formatted(SharedConstants.getGameVersion().getName());
        String fullUrl = UPDATE_URL_FORMAT.formatted(URLEncoder.encode(versionParam, StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder(URI.create(fullUrl)).GET().build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(r -> {
                    String body = r.body();
                    JsonArray array = GSON.fromJson(body, JsonArray.class);

                    if (!array.isEmpty()) {
                        JsonObject root = array.get(0).getAsJsonObject();

                        String versionName = root.get("name").getAsString();
                        if (versionName != null && versionName.toLowerCase(Locale.ROOT).startsWith("[o")) {
                            isObsolete.set(true);
                        }

                        String latestVer = root.get("version_number").getAsString();
                        try {
                            return Version.parse(latestVer);
                        } catch (VersionParsingException e) {
                            logger.warn("Could not parse version number {}", latestVer);
                        }
                    }

                    return null;
                })
                .exceptionally(t -> {
                    logger.warn("Error checking for updates", t);
                    return null;
                }).thenAccept(v -> {
                    logger.info("Found latest version {}", v.getFriendlyString());
                    latestVersion = v;
                });
    }

    public static boolean isObsolete() {
        return isObsolete.get();
    }
}
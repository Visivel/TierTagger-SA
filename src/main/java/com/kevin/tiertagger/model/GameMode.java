package com.kevin.tiertagger.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.kevin.tiertagger.TierTagger;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Pair;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public record GameMode(String id, String title) {
    public static CompletableFuture<List<GameMode>> fetchGamemodes(HttpClient client) {
        String endpoint = TierTagger.getManager().getConfig().getBaseUrl() + "/tierlists";
        final HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).GET().build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(r -> {
                    try {
                        JsonElement element = TierTagger.GSON.fromJson(r.body(), JsonElement.class);
                        if (!element.isJsonObject()) {
                            TierTagger.getLogger().error("Resposta inesperada ao buscar gamemodes: {}", r.body());
                            return List.of(new GameMode("vanilla", "Vanilla"));
                        }
                        JsonObject obj = element.getAsJsonObject();
                        return obj.entrySet().stream().map(e -> {
                            String id = e.getKey();
                            JsonObject valueObj = e.getValue().isJsonObject() ? e.getValue().getAsJsonObject() : null;
                            String title = valueObj != null && valueObj.has("name") ? valueObj.get("name").getAsString() : id;
                            return new GameMode(id, title);
                        }).toList();
                    } catch (Exception ex) {
                        TierTagger.getLogger().error("Erro ao parsear gamemodes: {}", r.body(), ex);
                        return List.of(new GameMode("vanilla", "Vanilla"));
                    }
                });
    }

    private Pair<Character, TextColor> iconAndColor() {
        return switch (this.id) {
            case "axe" -> new Pair<>('\uE701', TextColor.fromFormatting(Formatting.GREEN));
            case "mace" -> new Pair<>('\uE702', TextColor.fromFormatting(Formatting.GRAY));
            case "nethop", "neth_pot" -> new Pair<>('\uE703', TextColor.fromRgb(0x7d4a40));
            case "pot" -> new Pair<>('\uE704', TextColor.fromRgb(0xff0000));
            case "smp" -> new Pair<>('\uE705', TextColor.fromRgb(0xeccb45));
            case "sword" -> new Pair<>('\uE706', TextColor.fromRgb(0xa4fdf0));
            case "uhc" -> new Pair<>('\uE707', TextColor.fromFormatting(Formatting.RED));
            case "vanilla" -> new Pair<>('\uE708', TextColor.fromFormatting(Formatting.LIGHT_PURPLE));
            case "bed" -> new Pair<>('\uE801', TextColor.fromRgb(0xff0000));
            case "bow" -> new Pair<>('\uE802', TextColor.fromRgb(0x663d10));
            case "creeper" -> new Pair<>('\uE803', TextColor.fromFormatting(Formatting.GREEN));
            case "debuff" -> new Pair<>('\uE804', TextColor.fromFormatting(Formatting.DARK_GRAY));
            case "dia_crystal" -> new Pair<>('\uE805', TextColor.fromFormatting(Formatting.AQUA));
            case "dia_smp" -> new Pair<>('\uE806', TextColor.fromRgb(0x8c668b));
            case "elytra" -> new Pair<>('\uE807', TextColor.fromRgb(0x8d8db1));
            case "manhunt" -> new Pair<>('\uE808', TextColor.fromFormatting(Formatting.RED));
            case "minecart" -> new Pair<>('\uE809', TextColor.fromFormatting(Formatting.GRAY));
            case "og_vanilla" -> new Pair<>('\uE810', TextColor.fromFormatting(Formatting.GOLD));
            case "speed" -> new Pair<>('\uE811', TextColor.fromRgb(0x43a9d1));
            case "trident" -> new Pair<>('\uE812', TextColor.fromRgb(0x579b8c));
            default -> new Pair<>('•', TextColor.fromFormatting(Formatting.WHITE));
        };
    }

    public Optional<Character> icon() {
        Pair<Character, TextColor> pair = this.iconAndColor();

        return pair.getRight().getRgb() == 0xFFFFFF ? Optional.empty() : Optional.of(pair.getLeft());
    }

    public Text asStyled(boolean withDefaultDot) {
        Pair<Character, TextColor> pair = this.iconAndColor();

        if (pair.getRight().getRgb() == 0xFFFFFF && !withDefaultDot) {
            return Text.of(this.title);
        } else {
            Text name = Text.literal(this.title).styled(s -> s.withColor(pair.getRight()));
            return Text.literal(pair.getLeft() + " ").append(name);
        }
    }
}

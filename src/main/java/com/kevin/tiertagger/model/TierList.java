package com.kevin.tiertagger.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

@Getter
@AllArgsConstructor
public enum TierList {
    MCTIERS("MCTiers", "https://api.uku3lig.net/tiers", '\uE901'),
    SUBTIERS("SubTiers", "https://subtiers.net/api", '\uE902'),
    SATIERS("South Tiers", "http://too-butler.gl.at.ply.gg:1247/api/profile", '\uE903');

    private final String name;
    private final String url;
    private final char icon;

    public String styledName(boolean current) {
        String s = icon + " " + name;
        if (current) s += " (selecionado)";
        return s;
    }

    public static Optional<TierList> findByUrl(String url) {
        if (url.endsWith("/")) url = url.substring(0, url.length() - 1);

        final String finalUrl = url; // i :heart: java
        return Arrays.stream(values()).filter(list -> list.url.equals(finalUrl)).findFirst();
    }
}

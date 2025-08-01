package com.kevin.tiertagger.config;

import com.google.gson.internal.LinkedTreeMap;
import com.kevin.tiertagger.TierCache;
import com.kevin.tiertagger.model.AutoSwitchMode;
import com.kevin.tiertagger.model.GameMode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.minecraft.util.TranslatableOption;

import java.io.Serializable;
import java.util.NoSuchElementException;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TierTaggerConfig implements Serializable {
    private boolean enabled = true;
    private String gameMode = "vanilla";
    private boolean showRetired = true;
    private HighestMode highestMode = HighestMode.NOT_FOUND;
    private Statistic shownStatistic = Statistic.TIER;
    private boolean showIcons = true;
    private int retiredColor = 0xa2d6ff;
    // note: this is a GSON internal class. this *might* break in the future
    private LinkedTreeMap<String, Integer> tierColors = defaultColors();
    
    // === auto switch settings ===
    private AutoSwitchMode autoSwitchMode = AutoSwitchMode.API;

    // === internal stuff ===

    /**
     * <p>the field was renamed to do a little trolling and force it setting to the default value in players' config</p>
     * <p>previous name(s): {@code apiUrl}</p>
     */
    //private String baseUrl = "https://api.uku3lig.net/tiers";
    private String baseUrl = "http://too-butler.gl.at.ply.gg:1247/api/profile";

    public GameMode getGameMode() {
        try {
            return TierCache.findMode(this.gameMode);   
        } catch (NoSuchElementException e) {
            GameMode first = TierCache.GAMEMODES.getFirst();
            this.gameMode = first.id();
            return first;
        }
    }

    private static LinkedTreeMap<String, Integer> defaultColors() {
        LinkedTreeMap<String, Integer> colors = new LinkedTreeMap<>();
        colors.put("HT1", 0xe8ba3a);
        colors.put("LT1", 0xd5b355);
        colors.put("HT2", 0xc4d3e7);
        colors.put("LT2", 0xa0a7b2);
        colors.put("HT3", 0xf89f5a);
        colors.put("LT3", 0xc67b42);
        colors.put("HT4", 0x81749a);
        colors.put("LT4", 0x655b79);
        colors.put("HT5", 0x8f82a8);
        colors.put("LT5", 0x655b79);

        return colors;
    }

    @Getter
    @AllArgsConstructor
    public enum Statistic implements TranslatableOption {
        TIER(0, "tiertagger.stat.tier"),
        RANK(1, "tiertagger.stat.rank"),
        ;

        private final int id;
        private final String translationKey;
    }

    @Getter
    @AllArgsConstructor
    public enum HighestMode implements TranslatableOption {
        NEVER(0, "tiertagger.highest.never"),
        NOT_FOUND(1, "tiertagger.highest.not_found"),
        ALWAYS(2, "tiertagger.highest.always"),
        ;

        private final int id;
        private final String translationKey;
    }
}

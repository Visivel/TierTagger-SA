package com.kevin.tiertagger.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.util.TranslatableOption;

@Getter
@AllArgsConstructor
public enum AutoSwitchMode implements TranslatableOption {
    API(0, "tiertagger.autoswitch.api"),
    CUSTOM(1, "tiertagger.autoswitch.custom"),
    API_CUSTOM(2, "tiertagger.autoswitch.api_custom"),
    OFF(3, "tiertagger.autoswitch.off");

    private final int id;
    private final String translationKey;
}
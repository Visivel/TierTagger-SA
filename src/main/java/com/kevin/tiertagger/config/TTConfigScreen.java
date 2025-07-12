package com.kevin.tiertagger.config;

import com.kevin.tiertagger.TierCache;
import com.kevin.tiertagger.TierTagger;
import com.kevin.tiertagger.model.TierList;
import com.kevin.tiertagger.tierlist.PlayerSearchScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tab.Tab;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;
import net.uku3lig.ukulib.config.option.*;
import net.uku3lig.ukulib.config.option.widget.ButtonTab;
import net.uku3lig.ukulib.config.screen.TabbedConfigScreen;
import net.uku3lig.ukulib.utils.Ukutils;

import java.util.*;
import java.util.stream.Collectors;

public class TTConfigScreen extends TabbedConfigScreen<TierTaggerConfig> {
    private boolean firstInit = true;
    
    public TTConfigScreen(Screen parent) {
        super("TierTagger Config", parent, java.util.Objects.requireNonNull(TierTagger.getManager(), "Manager nulo na TTConfigScreen!"));
        System.out.println("[TTConfigScreen] Construtor chamado. Manager: " + TierTagger.getManager());
    }

    @Override
    protected void init() {
        System.out.println("[TTConfigScreen] init chamado");
        try {
            super.init();
            if (firstInit) {
                System.out.println("[TTConfigScreen] Primeira inicializacao completa");
                firstInit = false;
            }
        } catch (Exception e) {
            System.err.println("[TTConfigScreen] Erro na inicializacao: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    protected Tab[] getTabs(TierTaggerConfig config) {
        if (config == null) {
            System.err.println("[TTConfigScreen] Config nulo em getTabs!");
            throw new NullPointerException("Config nulo em getTabs!");
        }
        
        try {
            Tab[] tabs = new Tab[]{new MainSettingsTab(), new ColorsTab(), new TierlistTab()};
            System.out.println("[TTConfigScreen] getTabs chamado. Tabs: " + java.util.Arrays.toString(tabs));
            return tabs;
        } catch (Exception e) {
            System.err.println("[TTConfigScreen] Erro criando tabs: " + e.getMessage());
            e.printStackTrace();
            return new Tab[0];
        }
    }

    public class MainSettingsTab extends ButtonTab<TierTaggerConfig> {
        public MainSettingsTab() {
            super("tiertagger.config", TTConfigScreen.this.manager);
        }

        @Override
        protected WidgetCreator[] getWidgets(TierTaggerConfig config) {
            try {
                if (TierCache.GAMEMODES.isEmpty()) {
                    System.out.println("[MainSettingsTab] Gamemodes vazios, usando cpvp como default");
                    return new WidgetCreator[]{
                            CyclingOption.ofBoolean("tiertagger.config.enabled", config.isEnabled(), config::setEnabled),
                            CyclingOption.ofBoolean("tiertagger.config.retired", config.isShowRetired(), config::setShowRetired),
                            CyclingOption.ofTranslatableEnum("tiertagger.config.highest", TierTaggerConfig.HighestMode.class, config.getHighestMode(), config::setHighestMode, SimpleOption.constantTooltip(Text.translatable("tiertagger.config.highest.desc"))),
                            CyclingOption.ofTranslatableEnum("tiertagger.config.statistic", TierTaggerConfig.Statistic.class, config.getShownStatistic(), config::setShownStatistic),
                            CyclingOption.ofBoolean("tiertagger.config.icons", config.isShowIcons(), config::setShowIcons),
                            new SimpleButton("tiertagger.clear", b -> TierCache.clearCache()),
                            new ScreenOpenButton("tiertagger.config.search", PlayerSearchScreen::new)
                    };
                }
                
                return new WidgetCreator[]{
                        CyclingOption.ofBoolean("tiertagger.config.enabled", config.isEnabled(), config::setEnabled),
                        new CyclingOption<>("tiertagger.config.gamemode", TierCache.GAMEMODES, config.getGameMode(), m -> config.setGameMode(m.id()), m -> Text.literal(m.title())),
                        CyclingOption.ofBoolean("tiertagger.config.retired", config.isShowRetired(), config::setShowRetired),
                        CyclingOption.ofTranslatableEnum("tiertagger.config.highest", TierTaggerConfig.HighestMode.class, config.getHighestMode(), config::setHighestMode, SimpleOption.constantTooltip(Text.translatable("tiertagger.config.highest.desc"))),
                        CyclingOption.ofTranslatableEnum("tiertagger.config.statistic", TierTaggerConfig.Statistic.class, config.getShownStatistic(), config::setShownStatistic),
                        CyclingOption.ofBoolean("tiertagger.config.icons", config.isShowIcons(), config::setShowIcons),
                        new SimpleButton("tiertagger.clear", b -> TierCache.clearCache()),
                        new ScreenOpenButton("tiertagger.config.search", PlayerSearchScreen::new)
                };
            } catch (Exception e) {
                System.err.println("[MainSettingsTab] Erro criando widgets: " + e.getMessage());
                e.printStackTrace();
                return new WidgetCreator[0];
            }
        }
    }

    public class TierlistTab extends ButtonTab<TierTaggerConfig> {
        public TierlistTab() {
            super("tiertagger.config.tierlists", TTConfigScreen.this.manager);
        }

        @Override
        protected WidgetCreator[] getWidgets(TierTaggerConfig config) {
            try {
                Optional<TierList> current = TierList.findByUrl(config.getBaseUrl());

                List<WidgetCreator> widgets = Arrays.stream(TierList.values())
                        .map(t -> {
                            boolean isCurrent = current.isPresent() && current.get() == t;
                            return new SimpleButton(t.styledName(isCurrent), b -> {
                                config.setBaseUrl(t.getUrl());
                                TierTagger.getManager().saveConfig();
                                TTConfigScreen.this.close();
                                TierCache.init();
                                Ukutils.sendToast(Text.literal("Tierlist changed to " + t.getName() + "!"), Text.literal("Reloading tiers..."));
                            });
                        })
                        .collect(Collectors.toList());

                if (current.isEmpty()) {
                    widgets.add(new SimpleButton("Custom (selecionado, " + config.getBaseUrl() + ")", b -> {}));
                }

                return widgets.toArray(WidgetCreator[]::new);
            } catch (Exception e) {
                System.err.println("[TierlistTab] Erro criando widgets: " + e.getMessage());
                e.printStackTrace();
                return new WidgetCreator[0];
            }
        }
    }

    public class ColorsTab extends ButtonTab<TierTaggerConfig> {
        protected ColorsTab() {
            super("tiertagger.colors", TTConfigScreen.this.manager);
        }

        @Override
        protected WidgetCreator[] getWidgets(TierTaggerConfig config) {
            try {
                // i genuinely don't understand but chaining the calls just EXPLODES????
                Comparator<Map.Entry<String, Integer>> comparator = Comparator.comparing(e -> e.getKey().charAt(2));
                comparator = comparator.thenComparing(e -> e.getKey().charAt(0));

                List<ColorOption> tiers = config.getTierColors().entrySet().stream()
                        .sorted(comparator)
                        .map(e -> new ColorOption(e.getKey(), e.getValue(), val -> config.getTierColors().put(e.getKey(), val)))
                        .collect(Collectors.toList());

                tiers.addLast(new ColorOption("tiertagger.colors.retired", config.getRetiredColor(), config::setRetiredColor));

                return tiers.toArray(WidgetCreator[]::new);
            } catch (Exception e) {
                System.err.println("[ColorsTab] Erro criando widgets: " + e.getMessage());
                e.printStackTrace();
                return new WidgetCreator[0];
            }
        }
    }
}

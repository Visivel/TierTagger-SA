package com.kevin.tiertagger;

import com.google.gson.JsonSyntaxException;
import com.kevin.tiertagger.config.TierTaggerConfig;
import com.kevin.tiertagger.model.AutoSwitchMode;
import com.kevin.tiertagger.model.ServerListResponse;
import com.kevin.tiertagger.model.TierList;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class AutoSwitchManager {
    private static final String API_URL = "http://too-butler.gl.at.ply.gg:1247/api/servers";
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("tiertagger");
    private static final Path CUSTOM_SERVERS_FILE = CONFIG_DIR.resolve("custom_servers.txt");
    // melhor nao mexer, no custom_servers, a menos que saiba o que voce ta fazendo
    
    private static List<String> cachedNonSAServers = new ArrayList<>();
    private static List<String> cachedSAServers = new ArrayList<>();
    private static List<String> customNonSAServers = new ArrayList<>();
    private static List<String> customSAServers = new ArrayList<>();
    
    public static void init() {
        try {
            Files.createDirectories(CONFIG_DIR);
            loadCustomServers();
            useCustomAsApiDefault();
        } catch (IOException e) {
            TierTagger.getLogger().error("Falha ao inicializar o AutoSwitchManager", e);
        }
    }
    
    public static CompletableFuture<Void> fetchServerList() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(API_URL)).GET().build();
            
            return TierTagger.getClient().sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        try {
                            ServerListResponse serverList = TierTagger.GSON.fromJson(response.body(), ServerListResponse.class);
                            if (serverList.isSuccess() && serverList.getData() != null) {
                                cachedNonSAServers = new ArrayList<>(serverList.getData().getNonSAServers());
                                cachedSAServers = new ArrayList<>(serverList.getData().getSAServers());
                            } else {
                                useCustomAsApiDefault();
                            }
                        } catch (JsonSyntaxException e) {
                            TierTagger.getLogger().error("Falha na resposta da lista de servidores", e);
                            useCustomAsApiDefault();
                        }
                        return (Void) null;
                    })
                    .exceptionally(throwable -> {
                        TierTagger.getLogger().warn("Falha ao buscar lista de servidores da api, usando custom", throwable);
                        useCustomAsApiDefault();
                        return null;
                    });
        } catch (Exception e) {
            TierTagger.getLogger().warn("Falha ao criar requisicao da api, usando custom", e);
            useCustomAsApiDefault();
            return CompletableFuture.completedFuture(null);
        }
    }
    
    private static void useCustomAsApiDefault() {
        cachedNonSAServers = new ArrayList<>(customNonSAServers);
        cachedSAServers = new ArrayList<>(customSAServers);
        System.out.println("[AutoSwitch] Usando listas custom como fallback da API");
    }
    
    private static void loadCustomServers() {
        if (!Files.exists(CUSTOM_SERVERS_FILE)) {
            createDefaultCustomServers();
            return;
        }
        
        try {
            List<String> lines = Files.readAllLines(CUSTOM_SERVERS_FILE);
            customNonSAServers.clear();
            customSAServers.clear();
            
            boolean readingNonSA = false;
            boolean readingSA = false;
            
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                
                if (line.equals("[nonSAServers]")) {
                    readingNonSA = true;
                    readingSA = false;
                } else if (line.equals("[SAServers]")) {
                    readingNonSA = false;
                    readingSA = true;
                } else if (readingNonSA) {
                    customNonSAServers.add(line);
                } else if (readingSA) {
                    customSAServers.add(line);
                }
            }
        } catch (IOException e) {
            TierTagger.getLogger().error("Falha ao carregar as configuracoes", e);
            createDefaultCustomServers();
        }
    }
    
    private static void createDefaultCustomServers() {
        try {
            String defaultContent = """
                    # Configuracao custom pro Auto Switch do TierTaggerSA
                    # nonSAServerrs sao servers que nao sao SA
                    # SAServers sao servers que sao SA
                    # O Auto Switch vai trocar automaticamente pra Tierlist BR ou gringa
                    
                    [nonSAServers]
                    east.uspvp.org
                    uspvp.org
                    west.uspvp.com
                    crystalranked.org
                    auroraprac.com
                    east.mcpvp.club
                    mcpvp.club
                    
                    [SAServers]
                    balacobaco.net
                    cherryvanilla.club
                    jogar.tfgames.com.br
                    sapvp.com
                    """;
            
            Files.writeString(CUSTOM_SERVERS_FILE, defaultContent);
            loadCustomServers();
        } catch (IOException e) {
            TierTagger.getLogger().error("Falha ao criar o arquivo de configuracoes", e);
        }
    }
    
    public static void openConfigFolder() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;
            
            if (os.contains("win")) {
                pb = new ProcessBuilder("explorer", CONFIG_DIR.toString());
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", CONFIG_DIR.toString());
            } else {
                pb = new ProcessBuilder("xdg-open", CONFIG_DIR.toString());
            }
            
            pb.start();
        } catch (IOException e) {
            TierTagger.getLogger().error("Falha ao abrir a pasta de configuracoes", e);
        }
    }
    
    public static void handleServerJoin(String serverAddress) {
        TierTaggerConfig config = TierTagger.getManager().getConfig();
        AutoSwitchMode mode = config.getAutoSwitchMode();
        
        System.out.println("[AutoSwitch] handleServerJoin chamado com: " + serverAddress);
        System.out.println("[AutoSwitch] Modo atual: " + mode);
        System.out.println("[AutoSwitch] Servidores nonSA API: " + cachedNonSAServers);
        System.out.println("[AutoSwitch] Servidores SA API: " + cachedSAServers);
        System.out.println("[AutoSwitch] Servidores nonSA custom: " + customNonSAServers);
        System.out.println("[AutoSwitch] Servidores SA custom: " + customSAServers);
        
        if (mode == AutoSwitchMode.OFF) {
            System.out.println("[AutoSwitch] Modo esta OFF, retornando");
            return;
        }
        
        boolean isNonSA = false;
        boolean isSA = false;
        
        switch (mode) {
            case API -> {
                isNonSA = cachedNonSAServers.contains(serverAddress);
                isSA = cachedSAServers.contains(serverAddress);
                System.out.println("[AutoSwitch] Modo API - isNonSA: " + isNonSA + ", isSA: " + isSA);
            }
            case CUSTOM -> {
                isNonSA = customNonSAServers.contains(serverAddress);
                isSA = customSAServers.contains(serverAddress);
                System.out.println("[AutoSwitch] Modo CUSTOM - isNonSA: " + isNonSA + ", isSA: " + isSA);
            }
            case API_CUSTOM -> {
                isNonSA = cachedNonSAServers.contains(serverAddress) || customNonSAServers.contains(serverAddress);
                isSA = cachedSAServers.contains(serverAddress) || customSAServers.contains(serverAddress);
                System.out.println("[AutoSwitch] Modo API_CUSTOM - isNonSA: " + isNonSA + ", isSA: " + isSA);
            }
        }
        
        if (isNonSA) {
            System.out.println("[AutoSwitch] Trocando para MCTiers");
            config.setBaseUrl(TierList.MCTIERS.getUrl());
            TierTagger.getManager().saveConfig();
            TierCache.init();
        } else if (isSA) {
            System.out.println("[AutoSwitch] Trocando para SATiers");
            config.setBaseUrl(TierList.SATIERS.getUrl());
            TierTagger.getManager().saveConfig();
            TierCache.init();
        } else {
            System.out.println("[AutoSwitch] Servidor nao encontrado em nenhuma lista, mantendo tierlist atual");
        }
    }
    
    public static boolean shouldShowConfigButton() {
        AutoSwitchMode mode = TierTagger.getManager().getConfig().getAutoSwitchMode();
        return mode == AutoSwitchMode.CUSTOM || mode == AutoSwitchMode.API_CUSTOM;
    }
}
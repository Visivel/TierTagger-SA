package com.kevin.tiertagger.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ServerListResponse {
    private boolean success;
    private Data data;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Data {
        private List<String> nonSAServers;
        private List<String> SAServers;
    }
}
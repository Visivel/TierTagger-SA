package com.kevin.tiertagger.mixin;

import com.kevin.tiertagger.AutoSwitchManager;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class MixinClientPlayNetworkHandler {
    
    @Inject(method = "onGameJoin", at = @At("TAIL"))
    private void onGameJoin(GameJoinS2CPacket packet, CallbackInfo ci) {
        ClientPlayNetworkHandler handler = (ClientPlayNetworkHandler) (Object) this;
        if (handler.getConnection() != null && handler.getConnection().getAddress() != null) {
            String rawAddress = handler.getConnection().getAddress().toString();
            String serverAddress = rawAddress;
            
            if (serverAddress.startsWith("/")) {
                serverAddress = serverAddress.substring(1);
            }
            if (serverAddress.contains("/")) {
                serverAddress = serverAddress.split("/")[0];
            }
            if (serverAddress.contains(":")) {
                serverAddress = serverAddress.split(":")[0];
            }
            if (serverAddress.endsWith(".")) {
                serverAddress = serverAddress.substring(0, serverAddress.length() - 1);
            }
            
            System.out.println("[AutoSwitch] Endereco bruto: " + rawAddress);
            System.out.println("[AutoSwitch] Endereco processado: " + serverAddress);
            
            AutoSwitchManager.handleServerJoin(serverAddress);
        } else {
            System.out.println("[AutoSwitch] Conexao ou endereco e nulo");
        }
    }
}
package com.kevin.tiertagger.mixin;

import com.kevin.tiertagger.TierTagger;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerEntity.class)
public class MixinPlayerEntity {
    @ModifyReturnValue(method = "getDisplayName", at = @At("RETURN"))
    public Text prependTier(Text original) {
        try {
            if (original == null) return null;
            if (TierTagger.getManager() == null || TierTagger.getManager().getConfig() == null) {
                return original;
            }
            if (TierTagger.getManager().getConfig().isEnabled()) {
                PlayerEntity self = (PlayerEntity) (Object) this;
                Text result = TierTagger.appendTier(self, original);
                return result != null ? result : original;
            } else {
                return original;
            }
        } catch (Exception e) {
            TierTagger.getLogger().error("Erro no mixin: ", e);
            return original;
        }
    }
}

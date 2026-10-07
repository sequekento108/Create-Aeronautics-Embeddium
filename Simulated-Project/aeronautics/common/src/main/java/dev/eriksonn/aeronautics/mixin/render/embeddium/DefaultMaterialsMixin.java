package dev.eriksonn.aeronautics.mixin.render.embeddium;

import dev.eriksonn.aeronautics.index.client.AeroRenderTypes;
import net.minecraft.client.renderer.RenderType;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.DefaultMaterials;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DefaultMaterials.class)
public class DefaultMaterialsMixin {

    @Shadow
    @Final
    public static Material SOLID;

    @Inject(method = "forRenderLayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderType;translucent()Lnet/minecraft/client/renderer/RenderType;"), cancellable = true)
    private static void aeronautics$injectMaterialMapping(final RenderType layer, final CallbackInfoReturnable<Material> cir) {
        if (layer == AeroRenderTypes.levitite()) {
            cir.setReturnValue(SOLID);
        } else if (layer == AeroRenderTypes.levititeGhosts()) {
            cir.setReturnValue(SOLID);
        }
    }

}

package dev.ryanhcode.sable.mixin.sublevel_render.impl.embeddium;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.render.dispatcher.SubLevelRenderDispatcher;
import foundry.veil.api.client.render.VeilRenderBridge;
import foundry.veil.api.client.render.rendertype.VeilRenderType;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.embeddedt.embeddium.impl.Embeddium;
import org.embeddedt.embeddium.impl.render.EmbeddiumWorldRenderer;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;
import java.util.Objects;

@Mixin(value = EmbeddiumWorldRenderer.class, remap = false)
public abstract class EmbeddiumWorldRendererMixin {

    @Shadow
    private ClientLevel world;

    /**
     * @author RyanH
     * @reason Account for sub-levels in the visible chunk count
     */
    @ModifyReturnValue(method = "getVisibleChunkCount", at = @At("RETURN"))
    public int getVisibleChunkCount(final int original) {
        int sum = original;

        final Iterable<ClientSubLevel> sublevels = SubLevelContainer.getContainer(this.world).getAllSubLevels();
        for (final ClientSubLevel sublevel : sublevels) {
            sum += sublevel.getRenderData().getVisibleSectionCount();
        }

        return sum;
    }

    @Inject(method = "setupTerrain", at = @At(value = "INVOKE", target = "Lorg/embeddedt/embeddium/impl/render/chunk/RenderSectionManager;markGraphDirty()V"))
    public void sable$markGraphDirty(final Camera camera, final Viewport viewport, final int frame, final boolean spectator, final boolean updateChunksImmediately, final CallbackInfo ci) {
        final Iterable<ClientSubLevel> sublevels = ((ClientSubLevelContainer) ((SubLevelContainerHolder) this.world).sable$getPlotContainer()).getAllSubLevels();
        final Vec3 cameraPosition = camera.getPosition();
        final Minecraft minecraft = Minecraft.getInstance();
        final Frustum frustum = minecraft.levelRenderer.cullingFrustum;
        SubLevelRenderDispatcher.get().updateCulling(sublevels, cameraPosition.x, cameraPosition.y, cameraPosition.z, VeilRenderBridge.create(frustum), minecraft.player.isSpectator());
    }

    @Inject(method = "setupTerrain", at = @At("TAIL"))
    public void sable$setupTerrain(final Camera camera, final Viewport viewport, final int frame, final boolean spectator, final boolean updateChunksImmediately, final CallbackInfo ci) {
        final SubLevelRenderDispatcher dispatcher = SubLevelRenderDispatcher.get();

        dispatcher.preRenderChunks(camera);

        final Iterable<ClientSubLevel> sublevels = SubLevelContainer.getContainer(this.world).getAllSubLevels();
        final RenderRegionCache renderRegionCache = new RenderRegionCache();

        final boolean alwaysDefer = Embeddium.options().performance.alwaysDeferChunkUpdates;
        final PrioritizeChunkUpdates chunkUpdates = alwaysDefer ? PrioritizeChunkUpdates.NONE : PrioritizeChunkUpdates.NEARBY;
        for (final ClientSubLevel sublevel : sublevels) {
            sublevel.getRenderData().compileSections(chunkUpdates, renderRegionCache, camera);
        }
    }

    @Inject(method = "scheduleRebuildForChunk(IIIZ)V", at = @At("TAIL"))
    public void sable$scheduleRebuildForChunk(final int x, final int y, final int z, final boolean playerChanged, final CallbackInfo ci) {
        final ClientSubLevelContainer container = SubLevelContainer.getContainer(this.world);

        if (container != null && container.inBounds(x, z)) {
            final ClientSubLevel subLevel = (ClientSubLevel) Sable.HELPER.getContaining(this.world, new ChunkPos(x, z));

            if (subLevel != null) {
                subLevel.getRenderData().setDirty(x, y, z, playerChanged);
            }
        }
    }

    @Inject(method = "drawChunkLayer", at = @At("TAIL"))
    public void sable$drawRenderSources(final RenderType renderType, final Matrix4f modelViewMatrix, final double camX, final double camY, final double camZ, final CallbackInfo ci) {
        final SubLevelRenderDispatcher renderDispatcher = SubLevelRenderDispatcher.get();

        final Minecraft minecraft = Minecraft.getInstance();
        final float partialTicks = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        final List<ClientSubLevel> subLevels = SubLevelContainer.getContainer(this.world).getAllSubLevels();

        final Matrix4f modelView = new Matrix4f(modelViewMatrix);
        final Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());

        {
            renderType.setupRenderState();
            final ShaderInstance shader = Objects.requireNonNull(RenderSystem.getShader(), "shader");
            shader.setDefaultUniforms(VertexFormat.Mode.QUADS, modelView, projection, minecraft.getWindow());
            shader.apply();

            renderDispatcher.renderSectionLayer(subLevels, renderType, shader, camX, camY, camZ, modelView, projection, partialTicks);

            shader.clear();
            renderType.clearRenderState();
        }

        RenderType unwrappedRenderType = renderType;
        while (unwrappedRenderType instanceof final VeilRenderType.RenderTypeWrapper wrapper) {
            unwrappedRenderType = wrapper.get();
        }

        if (unwrappedRenderType instanceof final VeilRenderType.LayeredRenderType layered) {
            for (final RenderType layer : layered.getLayers()) {
                layer.setupRenderState();
                final ShaderInstance shader = Objects.requireNonNull(RenderSystem.getShader(), "shader");
                shader.setDefaultUniforms(VertexFormat.Mode.QUADS, modelView, projection, minecraft.getWindow());
                shader.apply();

                renderDispatcher.renderSectionLayer(subLevels, layer, shader, camX, camY, camZ, modelView, projection, partialTicks);

                shader.clear();
                layer.clearRenderState();
            }
        }
    }
}

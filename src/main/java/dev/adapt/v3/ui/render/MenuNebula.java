package dev.adapt.v3.ui.render;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice.MappedView;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import dev.adapt.v3.Adapt;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

// The backdrop is soft-edged, so it is shaded at half resolution and upscaled onto the main target.
public final class MenuNebula {
    public static final long START_NANOS = System.nanoTime();
    public static RenderPipeline pipeline;
    public static RenderPipeline upscalePipeline;
    public static MappableRingBuffer uniformBuffer;
    public static TextureTarget nebulaTarget;
    public static boolean requested;
    public static boolean unavailable;
    public static boolean verifiedCompiled;

    public static void request() {
        requested = true;
    }

    public static void flush() {
        if (requested) {
            requested = false;
            if (!unavailable && Minecraft.getInstance().isGameLoadFinished()) {
                try {
                    RenderTarget rendertarget = Minecraft.getInstance().gameRenderer.mainRenderTarget();
                    ensureResources(rendertarget.width, rendertarget.height);
                    if (!verifiedCompiled) {
                        verifiedCompiled = true;
                        if (RenderSystem.getCompiledPipelineNullable(pipeline) == null || RenderSystem.getCompiledPipelineNullable(upscalePipeline) == null) {
                            throw new IllegalStateException("Failed to compile menu nebula");
                        }
                    }

                    renderPass(rendertarget);
                } catch (Throwable throwable) {
                    unavailable = true;
                    Adapt.LOGGER.warn("AdaptV3 menu backdrop shader unavailable", throwable);
                }
            }
        }
    }

    public static boolean isAvailable() {
        return !unavailable;
    }

    public static void renderPass(RenderTarget rendertarget) {
        try (MappedView mappedview = uniformBuffer.currentBuffer().map(false, true)) {
            float f = (float)(System.nanoTime() - START_NANOS) / 1.0E9F % 3600.0F;
            Std140Builder.intoBuffer(mappedview.data()).putVec4(rendertarget.width, rendertarget.height, f, 1.0F);
        }

        CommandEncoder commandencoder = RenderSystem.getDevice().createCommandEncoder();

        try (RenderPass renderpass = commandencoder.createRenderPass(() -> "AdaptV3 menu nebula", nebulaTarget.getColorTextureView(), Optional.empty())) {
            renderpass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            RenderSystem.bindDefaultUniforms(renderpass);
            renderpass.setUniform("MenuNebula", uniformBuffer.currentBuffer());
            renderpass.draw(3, 1, 0, 0);
        }

        uniformBuffer.rotate();

        try (RenderPass renderpass1 = commandencoder.createRenderPass(() -> "AdaptV3 menu nebula upscale", rendertarget.getColorTextureView(), Optional.empty())) {
            renderpass1.setPipeline(RenderSystem.getCompiledPipeline(upscalePipeline));
            RenderSystem.bindDefaultUniforms(renderpass1);
            renderpass1.setUniform("InSampler", nebulaTarget.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            renderpass1.draw(3, 1, 0, 0);
        }
    }

    public static void ensureResources(int first, int second) {
        if (pipeline == null) {
            pipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("adaptv3", "pipeline/menu_nebula"))
                .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("adaptv3", "post/menu_nebula"))
                .withBindGroupLayout(BindGroupLayout.builder().withUniform("MenuNebula", UniformType.UNIFORM_BUFFER).build())
                .withColorTargetState(ColorTargetState.DEFAULT)
                .build();
            upscalePipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("adaptv3", "pipeline/upscale"))
                .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("adaptv3", "post/upscale"))
                .withBindGroupLayout(BindGroupLayout.builder().withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER).build())
                .withColorTargetState(ColorTargetState.DEFAULT)
                .build();
            uniformBuffer = new MappableRingBuffer(() -> "AdaptV3 menu nebula", 130, 16);
        }

        int i = Math.max(1, first / 2);
        int j = Math.max(1, second / 2);
        if (nebulaTarget == null) {
            nebulaTarget = new TextureTarget("adaptv3_menu_nebula", i, j, GpuFormat.RGBA8_UNORM, null);
        } else if (nebulaTarget.width != i || nebulaTarget.height != j) {
            nebulaTarget.resize(i, j);
        }
    }
}

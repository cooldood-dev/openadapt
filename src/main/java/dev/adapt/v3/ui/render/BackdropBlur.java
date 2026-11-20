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
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.adapt.v3.Adapt;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

// Dual-Kawase blur (down to 1/4 res, gaussian, up to 1/2): same look as the old full-res blur chain at ~1/10 the cost.
public final class BackdropBlur {
    // gaussian sigma in quarter-resolution texels (~7.6 full-resolution pixels)
    public static final float BLUR_SIGMA = 1.9F;
    public static RenderPipeline downPipeline;
    public static RenderPipeline blurPipeline;
    public static RenderPipeline upPipeline;
    public static TextureTarget halfTarget;
    public static TextureTarget quarterTarget;
    public static TextureTarget quarterSwap;
    public static TextureTarget outputTarget;
    public static MappableRingBuffer downHalfUbo;
    public static MappableRingBuffer downQuarterUbo;
    public static MappableRingBuffer blurHUbo;
    public static MappableRingBuffer blurVUbo;
    public static MappableRingBuffer upUbo;
    public static int fullWidth = 1;
    public static int fullHeight = 1;
    public static boolean requested;
    public static boolean unavailable;
    public static boolean verifiedCompiled;

    public static void request() {
        requested = true;
    }

    public static GpuTextureView getTextureView() {
        if (unavailable) {
            return null;
        }

        request();
        RenderTarget rendertarget = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        ensureResources(rendertarget.width, rendertarget.height);
        return outputTarget.getColorTextureView();
    }

    public static GpuSampler getSampler() {
        return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
    }

    // callers build normalized UVs from full-resolution pixel coordinates, so report the main target's size
    public static int getWidth() {
        return fullWidth;
    }

    public static int getHeight() {
        return fullHeight;
    }

    public static void process() {
        if (requested && outputTarget != null && !unavailable) {
            requested = false;

            try {
                RenderTarget rendertarget = Minecraft.getInstance().gameRenderer.mainRenderTarget();
                ensureResources(rendertarget.width, rendertarget.height);
                if (!verifiedCompiled) {
                    verifiedCompiled = true;

                    for (RenderPipeline renderpipeline : new RenderPipeline[]{downPipeline, blurPipeline, upPipeline}) {
                        if (RenderSystem.getCompiledPipelineNullable(renderpipeline) == null) {
                            throw new IllegalStateException("Failed to compile " + renderpipeline.getLocation());
                        }
                    }
                }

                CommandEncoder commandencoder = RenderSystem.getDevice().createCommandEncoder();
                GpuSampler gpusampler = getSampler();
                runPass(commandencoder, downPipeline, downHalfUbo, rendertarget, halfTarget, 0.5F / halfTarget.width, 0.5F / halfTarget.height, 0.0F, gpusampler);
                runPass(commandencoder, downPipeline, downQuarterUbo, halfTarget, quarterTarget, 0.5F / quarterTarget.width, 0.5F / quarterTarget.height, 0.0F, gpusampler);
                runPass(commandencoder, blurPipeline, blurHUbo, quarterTarget, quarterSwap, 1.0F / quarterTarget.width, 0.0F, BLUR_SIGMA, gpusampler);
                runPass(commandencoder, blurPipeline, blurVUbo, quarterSwap, quarterTarget, 0.0F, 1.0F / quarterTarget.height, BLUR_SIGMA, gpusampler);
                runPass(commandencoder, upPipeline, upUbo, quarterTarget, outputTarget, 0.5F / outputTarget.width, 0.5F / outputTarget.height, 0.0F, gpusampler);
            } catch (Throwable throwable) {
                unavailable = true;
                Adapt.LOGGER.warn("AdaptV3 backdrop blur unavailable", throwable);
            }
        }
    }

    public static void runPass(
        CommandEncoder commandEncoder,
        RenderPipeline renderPipeline,
        MappableRingBuffer mappableRingBuffer,
        RenderTarget input,
        RenderTarget output,
        float value,
        float value2,
        float value3,
        GpuSampler gpuSampler
    ) {
        try (MappedView mappedview = mappableRingBuffer.currentBuffer().map(false, true)) {
            Std140Builder.intoBuffer(mappedview.data()).putVec4(value, value2, value3, 0.0F);
        }

        try (RenderPass renderpass = commandEncoder.createRenderPass(() -> "AdaptV3 backdrop", output.getColorTextureView(), Optional.empty())) {
            renderpass.setPipeline(RenderSystem.getCompiledPipeline(renderPipeline));
            RenderSystem.bindDefaultUniforms(renderpass);
            renderpass.setUniform("BackdropConfig", mappableRingBuffer.currentBuffer());
            renderpass.setUniform("InSampler", input.getColorTextureView(), gpuSampler);
            renderpass.draw(3, 1, 0, 0);
        }

        mappableRingBuffer.rotate();
    }

    public static void ensureResources(int first, int second) {
        if (downPipeline == null) {
            downPipeline = buildPipeline("backdrop_down");
            blurPipeline = buildPipeline("backdrop_blur");
            upPipeline = buildPipeline("backdrop_up");
            downHalfUbo = new MappableRingBuffer(() -> "AdaptV3 backdrop down 1/2", 130, 16);
            downQuarterUbo = new MappableRingBuffer(() -> "AdaptV3 backdrop down 1/4", 130, 16);
            blurHUbo = new MappableRingBuffer(() -> "AdaptV3 backdrop blur H", 130, 16);
            blurVUbo = new MappableRingBuffer(() -> "AdaptV3 backdrop blur V", 130, 16);
            upUbo = new MappableRingBuffer(() -> "AdaptV3 backdrop up", 130, 16);
        }

        fullWidth = first;
        fullHeight = second;
        int i = Math.max(1, first / 2);
        int j = Math.max(1, second / 2);
        int k = Math.max(1, first / 4);
        int l = Math.max(1, second / 4);
        if (outputTarget == null) {
            halfTarget = new TextureTarget("adaptv3_backdrop_half", i, j, GpuFormat.RGBA8_UNORM, null);
            quarterTarget = new TextureTarget("adaptv3_backdrop_quarter", k, l, GpuFormat.RGBA8_UNORM, null);
            quarterSwap = new TextureTarget("adaptv3_backdrop_quarter_swap", k, l, GpuFormat.RGBA8_UNORM, null);
            outputTarget = new TextureTarget("adaptv3_backdrop", i, j, GpuFormat.RGBA8_UNORM, null);
        } else if (outputTarget.width != i || outputTarget.height != j) {
            halfTarget.resize(i, j);
            quarterTarget.resize(k, l);
            quarterSwap.resize(k, l);
            outputTarget.resize(i, j);
        }
    }

    public static RenderPipeline buildPipeline(String text) {
        return RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("adaptv3", "pipeline/" + text))
            .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("adaptv3", "post/" + text))
            .withBindGroupLayout(
                BindGroupLayout.builder()
                    .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform("BackdropConfig", UniformType.UNIFORM_BUFFER)
                    .build()
            )
            .withColorTargetState(ColorTargetState.DEFAULT)
            .build();
    }
}

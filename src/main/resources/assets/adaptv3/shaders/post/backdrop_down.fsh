#version 330
#extension GL_ARB_separate_shader_objects : require

// Dual-Kawase downsample (5 bilinear taps). Forces alpha to 1: the world image leaves alpha below 1 in places
// (sky, clouds, leaves, water) and the frosted glass would otherwise blend partly transparent in game.

uniform sampler2D InSampler;

layout(std140) uniform BackdropConfig {
    vec4 Config; // xy = half a texel of the output
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 o = Config.xy;
    vec3 sum = texture(InSampler, texCoord).rgb * 4.0;
    sum += texture(InSampler, texCoord - o).rgb;
    sum += texture(InSampler, texCoord + o).rgb;
    sum += texture(InSampler, texCoord + vec2(o.x, -o.y)).rgb;
    sum += texture(InSampler, texCoord - vec2(o.x, -o.y)).rgb;
    fragColor = vec4(sum / 8.0, 1.0);
}

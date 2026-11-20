#version 330
#extension GL_ARB_separate_shader_objects : require

// Dual-Kawase upsample (8 bilinear taps, tent-shaped).

uniform sampler2D InSampler;

layout(std140) uniform BackdropConfig {
    vec4 Config; // xy = half a texel of the output
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 o = Config.xy;
    vec3 sum = texture(InSampler, texCoord + vec2(-o.x * 2.0, 0.0)).rgb;
    sum += texture(InSampler, texCoord + vec2(o.x * 2.0, 0.0)).rgb;
    sum += texture(InSampler, texCoord + vec2(0.0, -o.y * 2.0)).rgb;
    sum += texture(InSampler, texCoord + vec2(0.0, o.y * 2.0)).rgb;
    sum += texture(InSampler, texCoord + vec2(-o.x, o.y)).rgb * 2.0;
    sum += texture(InSampler, texCoord + vec2(o.x, o.y)).rgb * 2.0;
    sum += texture(InSampler, texCoord + vec2(-o.x, -o.y)).rgb * 2.0;
    sum += texture(InSampler, texCoord + vec2(o.x, -o.y)).rgb * 2.0;
    fragColor = vec4(sum / 12.0, 1.0);
}

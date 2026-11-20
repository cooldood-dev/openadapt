#version 330
#extension GL_ARB_separate_shader_objects : require

// Separable gaussian. Adjacent taps are merged into one bilinear fetch, halving the sample count.

uniform sampler2D InSampler;

layout(std140) uniform BackdropConfig {
    vec4 Config; // xy = one texel along the blur direction, z = sigma (texels)
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 dir = Config.xy;
    float sigma = Config.z;
    float radius = ceil(sigma * 3.0);
    float k = 1.0 / (2.0 * sigma * sigma);
    vec3 sum = texture(InSampler, texCoord).rgb;
    float total = 1.0;
    for (float i = 1.0; i <= radius; i += 2.0) {
        float w1 = exp(-i * i * k);
        float w2 = i + 1.0 <= radius ? exp(-(i + 1.0) * (i + 1.0) * k) : 0.0;
        float w = w1 + w2;
        float off = (i * w1 + (i + 1.0) * w2) / w;
        sum += (texture(InSampler, texCoord + dir * off).rgb + texture(InSampler, texCoord - dir * off).rgb) * w;
        total += 2.0 * w;
    }
    fragColor = vec4(sum / total, 1.0);
}

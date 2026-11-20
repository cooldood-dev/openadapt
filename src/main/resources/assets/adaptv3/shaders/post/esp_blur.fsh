#version 330
#extension GL_ARB_separate_shader_objects : require

// Separable gaussian on the mask (red channel), same weights as the old client's OutlineUtil BLUR shader.
// Adjacent taps are merged into one bilinear fetch, halving the sample count.

uniform sampler2D InSampler;

layout(std140) uniform EspBlur {
    vec4 Config; // xy = one input texel along the blur direction, z = radius (input texels)
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 uv = texCoord;
    float radius = floor(Config.z + 0.5);
    float sigma = max(radius / 2.0, 0.5);
    float k = 1.0 / (2.0 * sigma * sigma);
    float sum = texture(InSampler, uv).r;
    float total = 1.0;
    for (float i = 1.0; i <= radius; i += 2.0) {
        float w1 = exp(-i * i * k);
        float w2 = i + 1.0 <= radius ? exp(-(i + 1.0) * (i + 1.0) * k) : 0.0;
        float w = w1 + w2;
        float off = (i * w1 + (i + 1.0) * w2) / w;
        sum += (texture(InSampler, uv + off * Config.xy).r + texture(InSampler, uv - off * Config.xy).r) * w;
        total += 2.0 * w;
    }
    float v = sum / total;
    fragColor = vec4(v, v, v, 1.0);
}

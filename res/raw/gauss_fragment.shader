precision mediump float;

uniform sampler2D uTexture0;
uniform vec4 uTexCoef0;
uniform vec4 uTexCoef1;
uniform vec4 uTexCoef2;
uniform vec4 uTexCoef3;

varying vec4 TexCoord0;
varying vec4 TexCoord1;
varying vec4 TexCoord2;
varying vec4 TexCoord3;

void main() {
	vec4 c0 = texture2D(uTexture0, TexCoord0.xy);
	vec4 c1 = texture2D(uTexture0, TexCoord1.xy);
	vec4 c2 = texture2D(uTexture0, TexCoord2.xy);
	vec4 c3 = texture2D(uTexture0, TexCoord3.xy);
	gl_FragColor = uTexCoef0 * c0 + uTexCoef1 * c1 + uTexCoef2 * c2 + uTexCoef3 * c3;
}

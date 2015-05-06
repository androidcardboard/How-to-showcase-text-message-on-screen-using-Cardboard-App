precision mediump float;

uniform sampler2D uTexture0;

varying vec4 TexCoord0;

void main() {
	gl_FragColor = texture2D(uTexture0, TexCoord0.xy);
}

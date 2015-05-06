precision mediump float;

attribute vec4 vPosition;
attribute vec4 vTexCoord0;

varying vec4 TexCoord0;

void main() {
	gl_Position = vPosition;
	TexCoord0 = vTexCoord0;
}

precision mediump float;

attribute vec4 vPosition;
attribute vec4 vTexCoord0;

uniform vec4 uTexOffset0;
uniform vec4 uTexOffset1;
uniform vec4 uTexOffset2;
uniform vec4 uTexOffset3;

varying vec4 TexCoord0;
varying vec4 TexCoord1;
varying vec4 TexCoord2;
varying vec4 TexCoord3;

void main() {
	gl_Position = vPosition;
	TexCoord0 = vTexCoord0 + uTexOffset0;
	TexCoord1 = vTexCoord0 + uTexOffset1;
	TexCoord2 = vTexCoord0 + uTexOffset2;
	TexCoord3 = vTexCoord0 + uTexOffset3;
}

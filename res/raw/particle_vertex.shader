precision mediump float;

attribute vec4 vPosition;
attribute float vSizeShift;

uniform float uPointSize;
uniform float uTime;
uniform vec4 uColor;

varying vec4 Color;

void main() {
	float Phase = abs(fract(uTime + vSizeShift) * 2.0 - 1.0);
	vec4 pColor = uColor;
	if (Phase > 0.75) {
		pColor.z = (Phase - 0.75) * 4.0;
	};
	Color = pColor;
	gl_PointSize = uPointSize * Phase;
	gl_Position = vPosition;
}

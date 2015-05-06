precision mediump float;

uniform sampler2D uTexture0;

varying vec4 Color;

void main()
{
	gl_FragColor = texture2D(uTexture0, gl_PointCoord) * Color;
}

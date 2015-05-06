precision mediump float;

uniform mat4 uMVPMatrix;
uniform vec4 uAmbient;
uniform vec4 uDiffuse;

const int MaxLights = 8;

struct LightSourceParameters {
	vec4 color;
	vec3 position;
};
uniform LightSourceParameters uLight[MaxLights];

attribute vec4 vPosition;
attribute vec3 vNormal;

varying vec4 FrontColor;

void main() {
	gl_Position = uMVPMatrix * vPosition;
	vec4 vcolor = uAmbient;

	int i;
	for (i = 0; i < MaxLights; i++) {
		vec3 vert2light = uLight[i].position - vPosition.xyz;
		vec3 ldir = normalize(vert2light);
		float NdotL = dot(vNormal, ldir);

		if (NdotL > 0.0) {
			vcolor += uLight[i].color * uDiffuse * NdotL;
		}
	}

	FrontColor = clamp(vcolor, 0.0, 1.0);
}

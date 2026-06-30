/**
 * WebGL2 volume raycaster for CT data.
 * No external dependencies — uses only the browser's WebGL2 API.
 *
 * Rendering: front-to-back alpha compositing with gradient-based Phong shading.
 * Transfer function: configurable CT window (WL/WW) with bone/soft-tissue layers.
 */

import type { VolumeData } from './volumeReader'

export type VolumeRenderMode = 'brain' | 'composite' | 'skull'

// ─── GLSL shaders ────────────────────────────────────────────────────────────

const VS = `#version 300 es
out vec2 vUv;
void main() {
  int id = gl_VertexID;
  vec2 pos = vec2(float(id & 1), float((id >> 1) & 1)) * 2.0 - 1.0;
  vUv = pos * 0.5 + 0.5;
  gl_Position = vec4(pos, 0.0, 1.0);
}`

const FS = `#version 300 es
precision highp float;
precision highp sampler3D;

uniform sampler3D uVol;
uniform vec2  uRes;
uniform float uAzim, uElev;
uniform float uWL,   uWW;
uniform float uVmin, uVmax;
uniform int   uMode;
uniform float uRoiScale;
// Physical normalized dimensions: (nx*dx, ny*dy, nz*dz) / max(nx*dx, ny*dy, nz*dz)
// This tells the shader the true shape of the volume (not necessarily a cube).
uniform vec3  uPhysNorm;

in  vec2 vUv;
out vec4 fragColor;

mat3 rotY(float a) { float c=cos(a),s=sin(a); return mat3(c,0.,s,  0.,1.,0.,  -s,0.,c); }
mat3 rotX(float a) { float c=cos(a),s=sin(a); return mat3(1.,0.,0.,  0.,c,-s,  0.,s,c); }

float hash12(vec2 p) {
  vec3 p3 = fract(vec3(p.xyx) * 0.1031);
  p3 += dot(p3, p3.yzx + 33.33);
  return fract((p3.x + p3.y) * p3.z);
}

// p is in physical-normalized space [0, uPhysNorm]
// Map to texture space [0,1]^3 before sampling
float voxelHU(vec3 p) {
  vec3 tc = p / uPhysNorm;
  if (any(lessThan(tc, vec3(0.0))) || any(greaterThan(tc, vec3(1.0)))) return uVmin;
  return texture(uVol, tc).r * (uVmax - uVmin) + uVmin;
}

bool insideRoi(vec3 p) {
  vec3 c = uPhysNorm * 0.5;
  vec2 q = (p.xy - c.xy) / max(max(uPhysNorm.x, uPhysNorm.y) * 0.5 * uRoiScale, 0.001);
  return dot(q, q) <= 1.0;
}

vec4 tfSkull(float hu) {
  if (hu < 220.0) return vec4(0.0);
  float f = smoothstep(220.0, 900.0, hu);
  float alpha = mix(0.10, 0.82, f);
  vec3 col = mix(vec3(0.72, 0.66, 0.56), vec3(1.0, 0.96, 0.88), f);
  return vec4(col, alpha);
}

vec4 tfBrain(float hu) {
  if (hu < -20.0 || hu > 140.0) return vec4(0.0);
  float tissue = smoothstep(8.0, 55.0, hu) * (1.0 - smoothstep(92.0, 140.0, hu));
  float dense = smoothstep(62.0, 130.0, hu);
  float alpha = mix(0.035, 0.20, tissue) + dense * 0.10;
  vec3 col = mix(vec3(0.58, 0.64, 0.64), vec3(0.86, 0.88, 0.84), tissue);
  col = mix(col, vec3(1.0, 0.64, 0.54), dense * 0.65);
  return vec4(col, alpha);
}

// Composite mode follows the VTK examples in CTBrain.py / CTAnnotationTool.py.
vec4 tf(float hu) {
  if (uMode == 0) return tfBrain(hu);
  if (uMode == 2) return tfSkull(hu);
  if (hu < -300.0) return vec4(0.0);

  float alpha = 0.0;
  vec3 col = vec3(0.0);

  if (hu < 100.0) {
    float f = smoothstep(-300.0, 100.0, hu);
    alpha = mix(0.0, 0.10, f);
    col = mix(vec3(0.02, 0.02, 0.02), vec3(0.86, 0.86, 0.84), f);
  } else if (hu < 400.0) {
    float f = smoothstep(100.0, 400.0, hu);
    alpha = mix(0.10, 0.30, f);
    col = mix(vec3(1.00, 1.00, 1.00), vec3(1.00, 0.58, 0.50), f);
  } else {
    float f = smoothstep(400.0, 1000.0, hu);
    alpha = mix(0.30, 0.52, f);
    col = mix(vec3(1.00, 0.58, 0.50), vec3(1.00, 0.97, 0.92), f);
  }

  return vec4(col, alpha);
}

// Ray–AABB intersection for box [0, uPhysNorm]
vec2 boxHit(vec3 ro, vec3 rd) {
  vec3 t1 = (vec3(0.0)     - ro) / rd;
  vec3 t2 = (uPhysNorm     - ro) / rd;
  vec3 tN = min(t1, t2), tF = max(t1, t2);
  return vec2(max(max(tN.x, tN.y), tN.z), min(min(tF.x, tF.y), tF.z));
}

const int STEPS = 420;

void main() {
  float asp = uRes.x / uRes.y;
  vec2 uv   = vec2(gl_FragCoord.x / uRes.x, gl_FragCoord.y / uRes.y);

  mat3 R   = rotX(-uElev) * rotY(-uAzim);
  vec3 fwd = R * vec3(0.0, 0.0, -1.0);
  vec3 rgt = R * vec3(1.0, 0.0,  0.0);
  vec3 up  = R * vec3(0.0, 1.0,  0.0);

  // Camera is centred at the volume's physical midpoint.
  // Screen maps to the largest physical dimension for a consistent FOV.
  vec3  center = uPhysNorm * 0.5;
  float scale  = max(uPhysNorm.x, max(uPhysNorm.y, uPhysNorm.z)) * 0.6;

  vec3 ro = center
            + rgt * (uv.x - 0.5) * 2.0 * scale * asp
            + up  * (uv.y - 0.5) * 2.0 * scale
            - fwd * scale * 3.0;
  vec3 rd = fwd;

  vec2 h = boxHit(ro, rd);
  if (h.y <= h.x) { fragColor = vec4(0.02,0.03,0.06,1.0); return; }

  float tStart = max(h.x, 0.0);
  float tEnd   = h.y;
  float dt     = (tEnd - tStart) / float(STEPS);
  float eps    = dt * 0.6;

  vec4 acc = vec4(0.0);

  float jitter = hash12(gl_FragCoord.xy);
  for (int i = 0; i < STEPS; i++) {
    vec3  p  = ro + rd * (tStart + (float(i) + jitter) * dt);
    if (!insideRoi(p)) continue;
    float hu = voxelHU(p);
    vec4  c  = tf(hu);
    if (c.a < 0.001) continue;

    // Gradient in physical space (eps already in physical units)
    vec3 g = vec3(
      voxelHU(p+vec3(eps,0,0)) - voxelHU(p-vec3(eps,0,0)),
      voxelHU(p+vec3(0,eps,0)) - voxelHU(p-vec3(0,eps,0)),
      voxelHU(p+vec3(0,0,eps)) - voxelHU(p-vec3(0,0,eps))
    );
    float gLen = length(g);
    if (gLen > 1.0) {
      vec3 n = g / gLen;
      vec3 L = normalize(R * vec3(0.7, 1.2, -0.5));
      float diff = max(dot(n, L), 0.0);
      float spec = pow(max(dot(reflect(-L,n), vec3(0,0,1)), 0.0), 16.0);
      c.rgb *= 0.22 + 0.68*diff + 0.10*spec;
    }

    acc.rgb += (1.0 - acc.a) * c.a * c.rgb;
    acc.a   += (1.0 - acc.a) * c.a;
    if (acc.a > 0.97) break;
  }

  fragColor = vec4(acc.rgb + vec3(0.02,0.03,0.06)*(1.0-acc.a), 1.0);
}`

// ─── WebGL helpers ────────────────────────────────────────────────────────────

function compileShader(gl: WebGL2RenderingContext, type: number, src: string): WebGLShader {
  const sh = gl.createShader(type)!
  gl.shaderSource(sh, src)
  gl.compileShader(sh)
  if (!gl.getShaderParameter(sh, gl.COMPILE_STATUS)) {
    throw new Error(`Shader compile error:\n${gl.getShaderInfoLog(sh)}`)
  }
  return sh
}

function linkProgram(gl: WebGL2RenderingContext, vs: string, fs: string): WebGLProgram {
  const prog = gl.createProgram()!
  gl.attachShader(prog, compileShader(gl, gl.VERTEX_SHADER, vs))
  gl.attachShader(prog, compileShader(gl, gl.FRAGMENT_SHADER, fs))
  gl.linkProgram(prog)
  if (!gl.getProgramParameter(prog, gl.LINK_STATUS)) {
    throw new Error(`Program link error:\n${gl.getProgramInfoLog(prog)}`)
  }
  return prog
}

/** Downsample volume to fit within maxDim on each axis (avoids GPU memory limits). */
function downsampleVolume(vol: VolumeData, maxDim = 256): {
  data: Float32Array; nx: number; ny: number; nz: number
} {
  const { nx, ny, nz, data } = vol
  const sx = Math.max(1, Math.ceil(nx / maxDim))
  const sy = Math.max(1, Math.ceil(ny / maxDim))
  const sz = Math.max(1, Math.ceil(nz / maxDim))

  if (sx === 1 && sy === 1 && sz === 1) return { data, nx, ny, nz }

  const onx = Math.ceil(nx / sx)
  const ony = Math.ceil(ny / sy)
  const onz = Math.ceil(nz / sz)
  const out = new Float32Array(onx * ony * onz)

  for (let z = 0; z < onz; z++) {
    const sz0 = Math.min(z * sz, nz - 1)
    for (let y = 0; y < ony; y++) {
      const sy0 = Math.min(y * sy, ny - 1)
      for (let x = 0; x < onx; x++) {
        const sx0 = Math.min(x * sx, nx - 1)
        out[z * ony * onx + y * onx + x] = data[sz0 * ny * nx + sy0 * nx + sx0]
      }
    }
  }

  return { data: out, nx: onx, ny: ony, nz: onz }
}

function smoothZFor3D(
  data: Float32Array,
  nx: number,
  ny: number,
  nz: number,
  anisotropy: number
): Float32Array {
  if (nz < 3) return data
  const passes = anisotropy > 1.25 ? 2 : 1
  let src = data
  for (let pass = 0; pass < passes; pass++) {
    const out = new Float32Array(src.length)
    const plane = nx * ny
    out.set(src.subarray(0, plane), 0)
    out.set(src.subarray((nz - 1) * plane), (nz - 1) * plane)
    for (let z = 1; z < nz - 1; z++) {
      const prev = (z - 1) * plane
      const cur = z * plane
      const next = (z + 1) * plane
      for (let i = 0; i < plane; i++) {
        out[cur + i] = src[prev + i] * 0.22 + src[cur + i] * 0.56 + src[next + i] * 0.22
      }
    }
    src = out
  }
  return src
}

// ─── Public renderer class ────────────────────────────────────────────────────

export class VolumeRenderer3D {
  private gl: WebGL2RenderingContext
  private prog: WebGLProgram
  private tex: WebGLTexture
  private vao: WebGLVertexArrayObject
  private vmin: number
  private vmax: number
  private physNorm: [number, number, number]   // physical dims / max_physical_dim

  constructor(canvas: HTMLCanvasElement, vol: VolumeData) {
    const gl = canvas.getContext('webgl2')
    if (!gl) throw new Error('当前浏览器不支持 WebGL2')
    this.gl = gl
    this.vmin = vol.vmin
    this.vmax = vol.vmax

    // Physical dimensions in mm
    const px = vol.nx * vol.dx, py = vol.ny * vol.dy, pz = vol.nz * vol.dz
    const pm = Math.max(px, py, pz)
    this.physNorm = [px / pm, py / pm, pz / pm]

    // Enable float texture linear filtering if possible
    gl.getExtension('OES_texture_float_linear')

    // Compile shaders
    this.prog = linkProgram(gl, VS, FS)

    // Fullscreen VAO (uses gl_VertexID, no buffers needed)
    const vao = gl.createVertexArray()!
    gl.bindVertexArray(vao)
    this.vao = vao

    // Upload 3D texture (R32F, normalized [0,1])
    const { data, nx, ny, nz } = downsampleVolume(vol, 256)
    const anisotropy = vol.dz / Math.max(vol.dx, vol.dy, 0.001)
    const renderData = smoothZFor3D(data, nx, ny, nz, anisotropy)
    const range = (vol.vmax - vol.vmin) || 1
    const norm = new Float32Array(renderData.length)
    for (let i = 0; i < renderData.length; i++) norm[i] = (renderData[i] - vol.vmin) / range

    const tex = gl.createTexture()!
    gl.bindTexture(gl.TEXTURE_3D, tex)
    gl.texImage3D(gl.TEXTURE_3D, 0, gl.R32F, nx, ny, nz, 0, gl.RED, gl.FLOAT, norm)
    gl.texParameteri(gl.TEXTURE_3D, gl.TEXTURE_MIN_FILTER, gl.LINEAR)
    gl.texParameteri(gl.TEXTURE_3D, gl.TEXTURE_MAG_FILTER, gl.LINEAR)
    gl.texParameteri(gl.TEXTURE_3D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE)
    gl.texParameteri(gl.TEXTURE_3D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE)
    gl.texParameteri(gl.TEXTURE_3D, gl.TEXTURE_WRAP_R, gl.CLAMP_TO_EDGE)
    this.tex = tex
  }

  render(
    azim: number,
    elev: number,
    wc: number,
    ww: number,
    mode: VolumeRenderMode = 'brain',
    roiScale = 0.76
  ): void {
    const { gl, prog, tex, vao } = this
    const w = gl.canvas.width, h = gl.canvas.height
    gl.viewport(0, 0, w, h)

    gl.useProgram(prog)
    gl.bindVertexArray(vao)

    // Volume texture → unit 0
    gl.activeTexture(gl.TEXTURE0)
    gl.bindTexture(gl.TEXTURE_3D, tex)

    const u = (name: string) => gl.getUniformLocation(prog, name)
    gl.uniform1i(u('uVol'),  0)
    gl.uniform2f(u('uRes'),  w, h)
    gl.uniform1f(u('uAzim'), azim * Math.PI / 180)
    gl.uniform1f(u('uElev'), elev * Math.PI / 180)
    gl.uniform1f(u('uWL'),   wc)
    gl.uniform1f(u('uWW'),   ww)
    gl.uniform1f(u('uVmin'), this.vmin)
    gl.uniform1f(u('uVmax'), this.vmax)
    gl.uniform1i(u('uMode'), mode === 'brain' ? 0 : mode === 'composite' ? 1 : 2)
    gl.uniform1f(u('uRoiScale'), roiScale)
    gl.uniform3f(u('uPhysNorm'), ...this.physNorm)

    gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4)
  }

  destroy(): void {
    const { gl } = this
    gl.deleteTexture(this.tex)
    gl.deleteProgram(this.prog)
    gl.deleteVertexArray(this.vao)
  }
}

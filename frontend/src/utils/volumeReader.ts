/**
 * Browser-side volumetric medical image reader.
 * Supported: NIfTI (.nii, .nii.gz), NRRD (.nrrd, .nhdr), MetaImage self-contained (.mha)
 */

export interface VolumeData {
  nx: number; ny: number; nz: number       // dimensions: cols, rows, slices
  dx: number; dy: number; dz: number       // voxel spacing in mm
  data: Float32Array                        // linear [z][y][x] order
  vmin: number; vmax: number
  wc: number; ww: number                   // suggested window center / width
}

// ─── Public entry point ──────────────────────────────────────────────────────

export async function readVolume(file: File): Promise<VolumeData> {
  const name = file.name.toLowerCase()
  if (name.endsWith('.nii.gz')) {
    return parseNifti(await decompressGzip(file))
  }
  if (name.endsWith('.nii')) {
    return parseNifti(await file.arrayBuffer())
  }
  if (name.endsWith('.nrrd') || name.endsWith('.nhdr')) {
    return parseNrrd(await file.arrayBuffer())
  }
  if (name.endsWith('.mha')) {
    return parseMha(await file.arrayBuffer())
  }
  throw new Error(`不支持的格式: ${file.name}\n支持: .nii / .nii.gz / .nrrd / .mha`)
}

// ─── Gzip decompression ──────────────────────────────────────────────────────

async function decompressGzip(file: File): Promise<ArrayBuffer> {
  if (typeof DecompressionStream === 'undefined') {
    throw new Error('当前浏览器不支持 DecompressionStream，请使用 Chrome 80+ 或 Firefox 113+')
  }
  const ds = new DecompressionStream('gzip')
  const blob = await new Response(file.stream().pipeThrough(ds)).blob()
  return blob.arrayBuffer()
}

// ─── NIfTI-1 parser ──────────────────────────────────────────────────────────

function parseNifti(buf: ArrayBuffer): VolumeData {
  const v = new DataView(buf)
  const le = v.getInt32(0, true) === 348   // little-endian if sizeof_hdr=348 in LE

  const ndim = v.getInt16(40, le)
  if (ndim < 3) throw new Error(`NIfTI: ndim=${ndim}，需要 ≥ 3 维`)

  const nx = v.getInt16(42, le)
  const ny = v.getInt16(44, le)
  const nz = v.getInt16(46, le)
  const dx = Math.abs(v.getFloat32(80, le)) || 1
  const dy = Math.abs(v.getFloat32(84, le)) || 1
  const dz = Math.abs(v.getFloat32(88, le)) || 1
  const datatype = v.getInt16(70, le)
  const rawSlope = v.getFloat32(112, le)
  const inter = v.getFloat32(116, le)
  const voxOff = Math.max(352, Math.round(v.getFloat32(108, le)))
  const scl = rawSlope === 0 ? 1 : rawSlope

  const n = nx * ny * nz
  const data = new Float32Array(n)
  const dv = new DataView(buf, voxOff)

  switch (datatype) {
    case 2:   { const r = new Uint8Array(buf, voxOff, n);  for (let i = 0; i < n; i++) data[i] = r[i] * scl + inter; break }
    case 256: { const r = new Int8Array(buf, voxOff, n);   for (let i = 0; i < n; i++) data[i] = r[i] * scl + inter; break }
    case 4:   { for (let i = 0; i < n; i++) data[i] = dv.getInt16(i * 2, le) * scl + inter; break }
    case 512: { for (let i = 0; i < n; i++) data[i] = dv.getUint16(i * 2, le) * scl + inter; break }
    case 8:   { for (let i = 0; i < n; i++) data[i] = dv.getInt32(i * 4, le) * scl + inter; break }
    case 16:  { for (let i = 0; i < n; i++) data[i] = dv.getFloat32(i * 4, le) * scl + inter; break }
    case 64:  { for (let i = 0; i < n; i++) data[i] = dv.getFloat64(i * 8, le) * scl + inter; break }
    default:  throw new Error(`NIfTI: 不支持的数据类型 ${datatype}`)
  }

  return buildVolume(data, nx, ny, nz, dx, dy, dz)
}

// ─── NRRD parser ─────────────────────────────────────────────────────────────

async function parseNrrd(buf: ArrayBuffer): Promise<VolumeData> {
  const bytes = new Uint8Array(buf)

  // Find header terminator: \n\n or \r\n\r\n
  let sepIdx = -1
  for (let i = 0; i < bytes.length - 3; i++) {
    if (bytes[i] === 0x0A && bytes[i + 1] === 0x0A) { sepIdx = i + 2; break }
    if (bytes[i] === 0x0D && bytes[i + 1] === 0x0A && bytes[i + 2] === 0x0D && bytes[i + 3] === 0x0A) { sepIdx = i + 4; break }
  }
  if (sepIdx < 0) throw new Error('NRRD: 找不到 header 结束标记')

  const header = new TextDecoder().decode(bytes.slice(0, sepIdx))
  const fields: Record<string, string> = {}
  for (const line of header.split('\n')) {
    if (line.startsWith('#') || !line.trim()) continue
    const idx = line.indexOf(':')
    if (idx < 0) continue
    fields[line.slice(0, idx).trim().toLowerCase()] = line.slice(idx + 1).trim()
  }

  const sizes = (fields['sizes'] ?? '').split(/\s+/).map(Number)
  if (sizes.length < 3) throw new Error('NRRD: sizes 字段异常')
  const [nx, ny, nz] = sizes

  let dx = 1, dy = 1, dz = 1
  if (fields['spacings']) {
    const sp = fields['spacings'].split(/\s+/).map(Number)
    if (!isNaN(sp[0])) dx = sp[0]
    if (!isNaN(sp[1])) dy = sp[1]
    if (!isNaN(sp[2])) dz = sp[2]
  } else if (fields['space directions']) {
    const m = [...fields['space directions'].matchAll(/\(([^)]+)\)/g)]
    const vec = (idx: number) => m[idx]?.[1].split(',').map(Number) ?? []
    dx = Math.abs(vec(0)[0]) || 1
    dy = Math.abs(vec(1)[1]) || 1
    dz = Math.abs(vec(2)[2]) || 1
  }

  const encoding = (fields['encoding'] ?? 'raw').toLowerCase()
  const typeStr = (fields['type'] ?? 'short').toLowerCase()
  const le = (fields['endian'] ?? 'little').toLowerCase() !== 'big'

  let dataBuf: ArrayBuffer = buf.slice(sepIdx)
  if (encoding === 'gzip' || encoding === 'gz') {
    const blob = await new Response(
      new Blob([dataBuf]).stream().pipeThrough(new DecompressionStream('gzip'))
    ).blob()
    dataBuf = await blob.arrayBuffer()
  } else if (encoding !== 'raw') {
    throw new Error(`NRRD: 不支持的编码 "${encoding}"`)
  }

  const n = nx * ny * nz
  const data = new Float32Array(n)
  const dv = new DataView(dataBuf)

  if (typeStr === 'uint8' || typeStr === 'uchar' || typeStr === 'unsigned char') {
    const r = new Uint8Array(dataBuf, 0, n); for (let i = 0; i < n; i++) data[i] = r[i]
  } else if (typeStr === 'int8' || typeStr === 'char' || typeStr === 'signed char') {
    const r = new Int8Array(dataBuf, 0, n); for (let i = 0; i < n; i++) data[i] = r[i]
  } else if (typeStr === 'int16' || typeStr === 'short' || typeStr === 'signed short') {
    for (let i = 0; i < n; i++) data[i] = dv.getInt16(i * 2, le)
  } else if (typeStr === 'uint16' || typeStr === 'ushort' || typeStr === 'unsigned short') {
    for (let i = 0; i < n; i++) data[i] = dv.getUint16(i * 2, le)
  } else if (typeStr === 'int32' || typeStr === 'int' || typeStr === 'signed int') {
    for (let i = 0; i < n; i++) data[i] = dv.getInt32(i * 4, le)
  } else if (typeStr === 'float' || typeStr === 'float32') {
    for (let i = 0; i < n; i++) data[i] = dv.getFloat32(i * 4, le)
  } else if (typeStr === 'double' || typeStr === 'float64') {
    for (let i = 0; i < n; i++) data[i] = dv.getFloat64(i * 8, le)
  } else {
    throw new Error(`NRRD: 不支持的数据类型 "${typeStr}"`)
  }

  return buildVolume(data, nx, ny, nz, dx, dy, dz)
}

// ─── MetaImage (.mha) parser ─────────────────────────────────────────────────

function parseMha(buf: ArrayBuffer): VolumeData {
  const bytes = new Uint8Array(buf)
  const dec = new TextDecoder()

  const fields: Record<string, string> = {}
  let dataStart = -1
  let pos = 0

  while (pos < bytes.length) {
    // Find line end
    let end = pos
    while (end < bytes.length && bytes[end] !== 0x0A) end++
    const line = dec.decode(bytes.slice(pos, end)).trim()
    pos = end + 1

    if (!line || line.startsWith('#')) continue
    const eqIdx = line.indexOf('=')
    if (eqIdx < 0) continue
    const key = line.slice(0, eqIdx).trim().toLowerCase()
    const val = line.slice(eqIdx + 1).trim()
    fields[key] = val.toLowerCase()

    if (key === 'elementdatafile') {
      if (fields[key] !== 'local') throw new Error('MHA: ElementDataFile 不是 LOCAL（.mhd 格式需要单独上传 .raw 文件，暂不支持）')
      dataStart = pos
      break
    }
  }

  if (dataStart < 0) throw new Error('MHA: 未找到 ElementDataFile = LOCAL')

  const dimSizes = (fields['dimsize'] ?? '').split(/\s+/).map(Number)
  if (dimSizes.length < 3) throw new Error('MHA: DimSize 字段异常')
  const [nx, ny, nz] = dimSizes

  const sp = (fields['elementspacing'] ?? '1 1 1').split(/\s+/).map(Number)
  const dx = sp[0] || 1, dy = sp[1] || 1, dz = sp[2] || 1
  const typeStr = fields['elementtype'] ?? 'met_short'
  const le = (fields['elementbyteordermsb'] ?? 'false') !== 'true'

  const n = nx * ny * nz
  const data = new Float32Array(n)
  const dataBuf = buf.slice(dataStart)
  const dv = new DataView(dataBuf)

  switch (typeStr) {
    case 'met_uchar':  { const r = new Uint8Array(dataBuf, 0, n); for (let i = 0; i < n; i++) data[i] = r[i]; break }
    case 'met_char':   { const r = new Int8Array(dataBuf, 0, n);  for (let i = 0; i < n; i++) data[i] = r[i]; break }
    case 'met_short':  { for (let i = 0; i < n; i++) data[i] = dv.getInt16(i * 2, le); break }
    case 'met_ushort': { for (let i = 0; i < n; i++) data[i] = dv.getUint16(i * 2, le); break }
    case 'met_int':    { for (let i = 0; i < n; i++) data[i] = dv.getInt32(i * 4, le); break }
    case 'met_uint':   { for (let i = 0; i < n; i++) data[i] = dv.getUint32(i * 4, le); break }
    case 'met_float':  { for (let i = 0; i < n; i++) data[i] = dv.getFloat32(i * 4, le); break }
    case 'met_double': { for (let i = 0; i < n; i++) data[i] = dv.getFloat64(i * 8, le); break }
    default: throw new Error(`MHA: 不支持的元素类型 "${typeStr}"`)
  }

  return buildVolume(data, nx, ny, nz, dx, dy, dz)
}

// ─── Common helpers ──────────────────────────────────────────────────────────

function buildVolume(
  data: Float32Array,
  nx: number, ny: number, nz: number,
  dx: number, dy: number, dz: number
): VolumeData {
  let vmin = Infinity, vmax = -Infinity
  for (let i = 0; i < data.length; i++) {
    if (data[i] < vmin) vmin = data[i]
    if (data[i] > vmax) vmax = data[i]
  }
  // Smart window suggestion
  // CT data has lots of air voxels (HU ~ -1000) that skew simple percentile.
  // Strategy: sample → if it looks like CT (p1 < -200), compute window on
  // tissue-only voxels (> -100 HU); otherwise fall back to p2–p98.
  const step = Math.max(1, Math.floor(data.length / 8000))
  const sample: number[] = []
  for (let i = 0; i < data.length; i += step) sample.push(data[i])
  sample.sort((a, b) => a - b)

  const p1  = sample[Math.floor(sample.length * 0.01)]
  const p99 = sample[Math.floor(sample.length * 0.99)]
  const isCT = p1 < -200  // significant air → likely CT

  let wc: number, ww: number

  if (isCT) {
    // Filter to tissue-only voxels (> -100 HU), then use p5-p95 range
    const tissue = sample.filter(v => v > -100)
    if (tissue.length > 50) {
      const tp5  = tissue[Math.floor(tissue.length * 0.05)]
      const tp95 = tissue[Math.floor(tissue.length * 0.95)]
      wc = Math.round((tp5 + tp95) / 2)
      ww = Math.max(80, Math.round(tp95 - tp5))
    } else {
      // Mostly air: default soft-tissue window
      wc = 40; ww = 350
    }
  } else {
    const p2  = sample[Math.floor(sample.length * 0.02)]
    const p98 = sample[Math.floor(sample.length * 0.98)]
    wc = Math.round((p2 + p98) / 2)
    ww = Math.max(1, Math.round(p98 - p2))
  }

  return { nx, ny, nz, dx, dy, dz, data, vmin, vmax, wc, ww }
}

function clamp255(v: number): number {
  return Math.round(Math.min(Math.max(v, 0), 1) * 255)
}

// ─── Slice renderers ─────────────────────────────────────────────────────────

export function renderAxial(
  canvas: HTMLCanvasElement, vol: VolumeData,
  z: number, wc: number, ww: number
): void {
  z = Math.max(0, Math.min(z, vol.nz - 1))
  const { nx: w, ny: h } = vol
  canvas.width = w; canvas.height = h
  const ctx = canvas.getContext('2d')!
  const img = ctx.createImageData(w, h)
  const d = img.data, lo = wc - ww / 2, rng = ww
  const base = z * h * w
  for (let r = 0; r < h; r++) {
    const rb = base + r * w
    for (let c = 0; c < w; c++) {
      const g = clamp255((vol.data[rb + c] - lo) / rng)
      const i = (r * w + c) * 4
      d[i] = d[i + 1] = d[i + 2] = g; d[i + 3] = 255
    }
  }
  ctx.putImageData(img, 0, 0)
}

export function renderCoronal(
  canvas: HTMLCanvasElement, vol: VolumeData,
  y: number, wc: number, ww: number
): void {
  y = Math.max(0, Math.min(y, vol.ny - 1))
  const srcW = vol.nx, srcH = vol.nz
  const lo = wc - ww / 2, rng = ww

  // Render at voxel resolution into a temporary ImageData
  const raw = new ImageData(srcW, srcH)
  const d = raw.data
  for (let r = 0; r < srcH; r++) {
    const z = vol.nz - 1 - r
    const base = z * vol.ny * vol.nx + y * vol.nx
    for (let c = 0; c < srcW; c++) {
      const g = clamp255((vol.data[base + c] - lo) / rng)
      const i = (r * srcW + c) * 4
      d[i] = d[i + 1] = d[i + 2] = g; d[i + 3] = 255
    }
  }

  // Physical aspect: height should represent nz*dz physical mm, width = nx*dx
  const physH = Math.max(srcH, Math.round(srcW * (vol.nz * vol.dz) / (vol.nx * vol.dx)))
  canvas.width = srcW; canvas.height = physH
  const ctx = canvas.getContext('2d')!

  if (physH === srcH) {
    ctx.putImageData(raw, 0, 0)
  } else {
    const tmp = document.createElement('canvas')
    tmp.width = srcW; tmp.height = srcH
    tmp.getContext('2d')!.putImageData(raw, 0, 0)
    ctx.imageSmoothingEnabled = true
    ctx.drawImage(tmp, 0, 0, srcW, physH)
  }
}

export function renderSagittal(
  canvas: HTMLCanvasElement, vol: VolumeData,
  x: number, wc: number, ww: number
): void {
  x = Math.max(0, Math.min(x, vol.nx - 1))
  const srcW = vol.ny, srcH = vol.nz
  const lo = wc - ww / 2, rng = ww

  const raw = new ImageData(srcW, srcH)
  const d = raw.data
  for (let r = 0; r < srcH; r++) {
    const z = vol.nz - 1 - r
    const base = z * vol.ny * vol.nx + x
    for (let c = 0; c < srcW; c++) {
      const g = clamp255((vol.data[base + c * vol.nx] - lo) / rng)
      const i = (r * srcW + c) * 4
      d[i] = d[i + 1] = d[i + 2] = g; d[i + 3] = 255
    }
  }

  const physH = Math.max(srcH, Math.round(srcW * (vol.nz * vol.dz) / (vol.ny * vol.dy)))
  canvas.width = srcW; canvas.height = physH
  const ctx = canvas.getContext('2d')!

  if (physH === srcH) {
    ctx.putImageData(raw, 0, 0)
  } else {
    const tmp = document.createElement('canvas')
    tmp.width = srcW; tmp.height = srcH
    tmp.getContext('2d')!.putImageData(raw, 0, 0)
    ctx.imageSmoothingEnabled = true
    ctx.drawImage(tmp, 0, 0, srcW, physH)
  }
}

// ─── DICOM series reader ─────────────────────────────────────────────────────
// Robust parser inspired by pydicom force=True:
//   · Works with and without the 128-byte DICM preamble
//   · Skips undefined-length SQ sequences instead of aborting
//   · Handles FFFE item/sequence-delimiter tags
//   · Falls back to brute-force pixel-data scan if structured parse fails
//   · Supports Explicit VR LE, Implicit VR LE, and 8-bit pixel data
//   · Attempts JPEG extraction for encapsulated (compressed) pixel data

interface DicomSlice {
  instanceNo: number
  zPos: number
  nx: number; ny: number
  dxy: number; dz: number
  bitsAlloc: number
  pixelRep: number           // 0 = unsigned, 1 = signed
  rescaleSlope: number
  rescaleIntercept: number
  pixelBuf: ArrayBuffer
  pixelOffset: number
  pixelLen: number
}

// Long VRs that use 4-byte length (with 2 reserved bytes before)
const LONG_VR = new Set(['OB','OD','OF','OL','OV','OW','SQ','UC','UN','UR','UT'])

function dcmStr(u8: Uint8Array, start: number, len: number): string {
  const end = Math.min(start + len, u8.length)
  return new TextDecoder('utf-8', { ignoreBOM: true })
    .decode(u8.slice(start, end))
    .replace(/\x00/g, '')
    .trim()
}

/**
 * Core DICOM parser. Tries start positions [132 (Part-10), 0 (ACR/NEMA)].
 * Skips over sequences rather than aborting, matching pydicom force=True behaviour.
 */
function parseDicomSlice(buf: ArrayBuffer): DicomSlice | null {
  const u8 = new Uint8Array(buf)
  if (u8.length < 100) return null

  const hasPreamble =
    u8.length > 132 &&
    u8[128] === 0x44 && u8[129] === 0x49 && u8[130] === 0x43 && u8[131] === 0x4D  // DICM

  // Try standard Part-10 start, then from byte 0 (ACR/NEMA without preamble)
  const starts = hasPreamble ? [132, 0] : [0, 132]
  for (const start of starts) {
    const s = parseDicomFrom(buf, u8, start)
    if (s) return s
  }

  // Last resort: brute-force scan for PixelData tag bytes
  return bruteForcePixelData(buf, u8)
}

function parseDicomFrom(buf: ArrayBuffer, u8: Uint8Array, startPos: number): DicomSlice | null {
  const view = new DataView(buf)

  const s: Partial<DicomSlice> = {
    rescaleSlope: 1, rescaleIntercept: 0,
    dxy: 1, dz: 1, instanceNo: 0, zPos: 0,
    pixelRep: 0, bitsAlloc: 16,
  }

  let pos = startPos
  const SAFETY = 500_000  // max iterations

  for (let iter = 0; iter < SAFETY && pos + 4 <= u8.length; iter++) {
    const grp = view.getUint16(pos, true)
    const elm = view.getUint16(pos + 2, true)
    const tag = (grp << 16) | elm
    pos += 4

    // ── FFFE group: item / delimiter tags (no VR, 4-byte length) ──────────
    if (grp === 0xFFFE) {
      if (pos + 4 > u8.length) break
      const ilen = view.getUint32(pos, true)
      pos += 4
      // Skip fixed-length items; undefined-length items contain more tags → fall through
      if (ilen !== 0xFFFFFFFF) pos += ilen
      continue
    }

    // ── Detect VR mode per tag ──────────────────────────────────────────────
    const b0 = u8[pos], b1 = u8[pos + 1]
    const looksExplicit = b0 >= 0x41 && b0 <= 0x5A && b1 >= 0x41 && b1 <= 0x5A  // A-Z A-Z
    let len: number
    let vr = ''

    if (looksExplicit) {
      vr = String.fromCharCode(b0, b1)
      pos += 2
      if (LONG_VR.has(vr)) {
        pos += 2                              // 2 reserved bytes
        if (pos + 4 > u8.length) break
        len = view.getUint32(pos, true); pos += 4
      } else {
        if (pos + 2 > u8.length) break
        len = view.getUint16(pos, true); pos += 2
      }
    } else {
      // Implicit VR Little Endian
      if (pos + 4 > u8.length) break
      len = view.getUint32(pos, true); pos += 4
    }

    // ── Undefined-length value ──────────────────────────────────────────────
    if (len === 0xFFFFFFFF) {
      if (tag === 0x7FE00010) {
        // Encapsulated (compressed) pixel data — try to extract JPEG bytes
        const jpeg = extractEncapsulatedJpeg(u8, view, pos)
        if (jpeg) {
          s.pixelBuf = buf; s.pixelOffset = jpeg.offset; s.pixelLen = jpeg.len
          s.bitsAlloc = 0   // ← 明确标记为压缩格式，caller 会跳过像素读取
          return s as DicomSlice
        }
      }
      // Undefined-length SQ or other: DO NOT break — continue scanning
      // (sequence delimiters FFFE,E0DD handled above as FFFE group)
      continue
    }

    if (pos + len > u8.length) break
    const ds = pos
    pos += len

    // ── Extract tag values ──────────────────────────────────────────────────
    switch (tag) {
      case 0x00200013: s.instanceNo = parseInt(dcmStr(u8, ds, len)) || 0; break
      case 0x00200032: {
        const p = dcmStr(u8, ds, len).split('\\')
        s.zPos = parseFloat(p[2] ?? '0') || 0; break
      }
      case 0x00280010: if (len >= 2) s.ny = view.getUint16(ds, true); break
      case 0x00280011: if (len >= 2) s.nx = view.getUint16(ds, true); break
      case 0x00280030: {
        const p = dcmStr(u8, ds, len).split('\\')
        s.dxy = parseFloat(p[0]) || 1; break
      }
      case 0x00180050: s.dz = parseFloat(dcmStr(u8, ds, len)) || 1; break
      case 0x00280100: if (len >= 2) s.bitsAlloc = view.getUint16(ds, true); break
      case 0x00280103: if (len >= 2) s.pixelRep  = view.getUint16(ds, true); break
      case 0x00281052: s.rescaleIntercept = parseFloat(dcmStr(u8, ds, len)) || 0; break
      case 0x00281053: s.rescaleSlope     = parseFloat(dcmStr(u8, ds, len)) || 1; break
      case 0x7FE00010:
        if (len > 0) {
          s.pixelBuf = buf; s.pixelOffset = ds; s.pixelLen = len
          return s as DicomSlice
        }
        break
    }
  }
  return null
}

/**
 * Brute-force: scan the file for the PixelData tag bytes E0 7F 10 00.
 * Fallback when structured parsing fails (e.g. deeply nested sequences).
 */
function bruteForcePixelData(buf: ArrayBuffer, u8: Uint8Array): DicomSlice | null {
  const view = new DataView(buf)
  // PixelData tag in LE: E0 7F 10 00
  for (let i = 0; i < u8.length - 12; i++) {
    if (u8[i] !== 0xE0 || u8[i+1] !== 0x7F || u8[i+2] !== 0x10 || u8[i+3] !== 0x00) continue

    let dataStart: number, dataLen: number
    const b4 = u8[i+4], b5 = u8[i+5]
    const isOB_OW = (b4 === 0x4F && (b5 === 0x42 || b5 === 0x57))  // OB or OW

    if (isOB_OW) {
      // Explicit VR OB/OW: 2-byte VR + 2 reserved + 4-byte length
      dataLen   = view.getUint32(i + 8, true)
      dataStart = i + 12
    } else {
      // Implicit VR: 4-byte length
      dataLen   = view.getUint32(i + 4, true)
      dataStart = i + 8
    }

    if (dataLen === 0xFFFFFFFF) {
      // Encapsulated — try JPEG extraction
      const jpeg = extractEncapsulatedJpeg(u8, view, dataStart)
      if (jpeg) {
        return {
          instanceNo: 0, zPos: 0, nx: 0, ny: 0, dxy: 1, dz: 1,
          bitsAlloc: 0, pixelRep: 0, rescaleSlope: 1, rescaleIntercept: 0,
          pixelBuf: buf, pixelOffset: jpeg.offset, pixelLen: jpeg.len,
        }
      }
      return null
    }

    if (dataLen > 0 && dataStart + dataLen <= u8.length) {
      return {
        instanceNo: 0, zPos: 0, nx: 0, ny: 0, dxy: 1, dz: 1,
        bitsAlloc: 16, pixelRep: 0, rescaleSlope: 1, rescaleIntercept: 0,
        pixelBuf: buf, pixelOffset: dataStart, pixelLen: dataLen,
      }
    }
  }
  return null
}

/**
 * Try to locate JPEG bytes inside encapsulated pixel data.
 * Encapsulation structure: Item(FFFE,E000) + length + [JPEG bytes starting with FFD8]
 */
function extractEncapsulatedJpeg(
  u8: Uint8Array, view: DataView, afterPixelDataTag: number
): { offset: number; len: number } | null {
  let pos = afterPixelDataTag
  // The first item may be a Basic Offset Table (empty or with offsets); skip it
  // Then subsequent items hold the actual fragment data
  let itemCount = 0
  while (pos + 8 <= u8.length && itemCount < 256) {
    // Item tag: FE FF 00 E0 (LE)
    if (u8[pos] !== 0xFE || u8[pos+1] !== 0xFF || u8[pos+2] !== 0x00 || u8[pos+3] !== 0xE0) break
    const itemLen = view.getUint32(pos + 4, true)
    pos += 8
    if (itemLen === 0xFFFFFFFF) break
    if (itemLen > 0 && pos + itemLen <= u8.length) {
      // Check for JPEG SOI marker FFD8
      if (u8[pos] === 0xFF && u8[pos+1] === 0xD8) {
        return { offset: pos, len: itemLen }
      }
    }
    pos += itemLen
    itemCount++
  }
  return null
}

/**
 * Parse a folder-full of DICOM files into a VolumeData.
 * Accepts the FileList from an <input webkitdirectory> element.
 * Non-DICOM files are silently skipped.
 */
export async function readDicomSeries(files: FileList | File[]): Promise<VolumeData> {
  const SKIP_EXT = new Set(['.xml','.json','.txt','.html','.htm','.css','.js','.ts',
                             '.md','.pdf','.zip','.tar','.gz','.png','.jpg','.bmp','.svg'])
  const candidates = Array.from(files).filter(f => {
    if (f.size < 200) return false
    const dotIdx = f.name.lastIndexOf('.')
    const ext = dotIdx >= 0 ? f.name.slice(dotIdx).toLowerCase() : ''
    return !SKIP_EXT.has(ext)
  })
  if (candidates.length === 0) throw new Error(
    '文件夹中未找到候选文件（DICOM 文件可能没有 .dcm 扩展名，已自动过滤）'
  )

  const slices: DicomSlice[] = []
  let compressedCount = 0

  for (const f of candidates) {
    let buf: ArrayBuffer
    try { buf = await f.arrayBuffer() } catch { continue }
    const s = parseDicomSlice(buf)
    if (!s) continue

    const bpc = s.bitsAlloc === 8 ? 1 : 2          // bytes per component
    const expected = s.nx * s.ny * bpc
    // Skip: compressed (bitsAlloc=0), missing dimensions, or pixel buffer too small
    // (too-small buffer = JPEG compressed data mistaken for raw pixels)
    if (s.bitsAlloc === 0 || s.nx === 0 || s.ny === 0 || s.pixelLen < expected) {
      compressedCount++
      continue
    }
    slices.push(s)
  }

  if (slices.length === 0) {
    if (compressedCount > 0) throw new Error(
      `检测到 ${compressedCount} 个压缩格式 DICOM（JPEG/JPEG2000/RLE）。\n` +
      `浏览器无法直接解压，请先用 Python 工具转换为 NIfTI 格式后再上传：\n` +
      `  python -c "import SimpleITK as sitk; sitk.WriteImage(sitk.ReadImage('CT Plain/'), 'output.nii.gz')"`
    )
    throw new Error(
      `${candidates.length} 个候选文件均无法解析为 DICOM。\n` +
      `请确认文件为未压缩 DICOM（CQ500 等数据集建议先用 cq500_batch_annotate.py 转为 NIfTI）`
    )
  }

  // Sort by z-position, fall back to instance number
  slices.sort((a, b) => a.zPos !== b.zPos ? a.zPos - b.zPos : a.instanceNo - b.instanceNo)

  const { nx, ny, dxy } = slices[0]
  const nz = slices.length
  const dz = nz > 1 ? Math.abs(slices[1].zPos - slices[0].zPos) || slices[0].dz : slices[0].dz

  const data = new Float32Array(nz * ny * nx)
  for (let z = 0; z < nz; z++) {
    const sl = slices[z]
    const slope = sl.rescaleSlope, inter = sl.rescaleIntercept
    const base = z * ny * nx
    try {
      const dv = new DataView(sl.pixelBuf, sl.pixelOffset, sl.pixelLen)
      if (sl.bitsAlloc === 8) {
        const raw8 = sl.pixelRep === 1
          ? new Int8Array(sl.pixelBuf, sl.pixelOffset, sl.pixelLen)
          : new Uint8Array(sl.pixelBuf, sl.pixelOffset, sl.pixelLen)
        for (let i = 0; i < ny * nx; i++) data[base + i] = raw8[i] * slope + inter
      } else {
        for (let i = 0; i < ny * nx; i++) {
          const raw = sl.pixelRep === 1
            ? dv.getInt16(i * 2, true)
            : dv.getUint16(i * 2, true)
          data[base + i] = raw * slope + inter
        }
      }
    } catch { /* slice pixel read failed — leave as 0 */ }
  }

  return buildVolume(data, nx, ny, nz, dxy, dxy, dz)
}

/** Coronal Maximum Intensity Projection (front-to-back MIP) */
export function renderMip(
  canvas: HTMLCanvasElement, vol: VolumeData,
  wc: number, ww: number
): void {
  const w = vol.nx, h = vol.nz
  canvas.width = w; canvas.height = h
  const ctx = canvas.getContext('2d')!
  const img = ctx.createImageData(w, h)
  const d = img.data, lo = wc - ww / 2, rng = ww

  for (let z = 0; z < vol.nz; z++) {
    const row = vol.nz - 1 - z
    for (let x = 0; x < w; x++) {
      let mx = -Infinity
      const zBase = z * vol.ny * vol.nx + x
      for (let y = 0; y < vol.ny; y++) {
        const v = vol.data[zBase + y * vol.nx]
        if (v > mx) mx = v
      }
      const g = clamp255((mx - lo) / rng)
      const i = (row * w + x) * 4
      d[i] = d[i + 1] = d[i + 2] = g; d[i + 3] = 255
    }
  }
  ctx.putImageData(img, 0, 0)
}

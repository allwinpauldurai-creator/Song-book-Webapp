// LOCAL DEVELOPMENT ONLY. Run from the repo root: node scripts/seed-sample-data.mjs
// Seeds the LOCAL dev API with sample categories and books via the admin API.
// Idempotent: existing categories (by name) and books (by title) are skipped.
// Usage: node scripts/seed-sample-data.mjs [apiBase]   (default http://localhost:8080)
import zlib from 'node:zlib';

const API = (process.argv[2] ?? 'http://localhost:8080') + '/api/v1';
const ADMIN = { email: 'admin@songbook.local', password: 'ChangeMe-Admin-123' };

// ------------------------------------------------------------------ sample content

const CATEGORIES = [
  { name: 'Hymns', description: 'Traditional hymns for congregational singing.' },
  { name: 'Choruses', description: 'Short, repeatable choruses and refrains.' },
  { name: 'Youth', description: 'Songs for youth groups and camps.' },
  { name: 'Christmas', description: 'Carols and seasonal songs.' },
];

const today = new Date();
const iso = (d) => d.toISOString().slice(0, 10);
const addDays = (n) => { const d = new Date(today); d.setUTCDate(d.getUTCDate() + n); return iso(d); };

// [title, category, status, effectiveDate, expiryDate, pages, colors, description]
const BOOKS = [
  ['Morning Hymnal', 'Hymns', 'PUBLISHED', addDays(-120), null, 12, ['#3949ab', '#7986cb'],
    'A collection of morning and opening hymns, arranged for four-part harmony.\n\nSample data for local development.'],
  ['Evening Praise', 'Hymns', 'PUBLISHED', addDays(-90), null, 10, ['#283593', '#00897b'],
    'Quiet evening hymns and closing songs. Sample data for local development.'],
  ['Songs of the Harvest', 'Hymns', 'PUBLISHED', addDays(-60), addDays(200), 8, ['#e65100', '#ffb300'],
    'Thanksgiving and harvest-season hymns. Available for a limited period. Sample data.'],
  ['Little Choruses', 'Choruses', 'PUBLISHED', addDays(-45), null, 6, ['#00897b', '#80cbc4'],
    'Short choruses that are easy to learn and teach. Sample data for local development.'],
  ['Chorus Collection Vol. 2', 'Choruses', 'PUBLISHED', addDays(-10), null, 9, ['#00695c', '#4db6ac'],
    'The second volume of well-loved choruses. Sample data for local development.'],
  ['Campfire Songs', 'Youth', 'PUBLISHED', addDays(-30), null, 7, ['#6a1b9a', '#ba68c8'],
    'Upbeat songs for youth camps and retreats, with guitar chords. Sample data.'],
  ['Youth Worship Night', 'Youth', 'PUBLISHED', addDays(-5), null, 8, ['#ad1457', '#f06292'],
    'Contemporary worship set lists for youth evenings. Sample data for local development.'],
  ['Carols by Candlelight', 'Christmas', 'PUBLISHED', addDays(-20), null, 11, ['#b71c1c', '#2e7d32'],
    'Classic carols for Christmas services and carol evenings. Sample data.'],
  ['Untitled Draft Songbook', 'Choruses', 'DRAFT', addDays(-1), null, 4, ['#455a64', '#90a4ae'],
    'DRAFT: should NOT appear in the public catalogue. Sample data.'],
  ['Easter Songbook (coming soon)', 'Hymns', 'PUBLISHED', addDays(60), null, 6, ['#f9a825', '#fff176'],
    'Effective date in the future: should NOT appear in the public catalogue yet. Sample data.'],
];

// ------------------------------------------------------------------ PDF generation

function pdfEscape(s) {
  return s.replace(/[\\()]/g, (c) => '\\' + c).replace(/[^\x20-\x7e]/g, '?');
}

/** Builds a valid multi-page PDF (Helvetica text) with a correct xref table. */
function makePdf(title, pageCount) {
  const objects = []; // index i => object number i+1
  const add = (body) => { objects.push(body); return objects.length; };

  const catalogNo = add(null);
  const pagesNo = add(null);
  const fontNo = add('<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>');
  const boldNo = add('<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>');

  const pageNos = [];
  for (let p = 1; p <= pageCount; p++) {
    const lines = [];
    const text = (font, size, x, y, s) => lines.push(`BT /${font} ${size} Tf ${x} ${y} Td (${pdfEscape(s)}) Tj ET`);
    if (p === 1) {
      text('F2', 30, 72, 640, title);
      text('F1', 14, 72, 600, 'Song Book - sample edition');
      text('F1', 11, 72, 560, 'Generated for local development and testing only.');
      text('F1', 11, 72, 540, `${pageCount} pages`);
    } else {
      text('F2', 18, 72, 720, `${p - 1}. Sample Song ${p - 1}`);
      let y = 680;
      for (let v = 1; v <= 3; v++) {
        text('F2', 12, 72, y, `Verse ${v}`); y -= 20;
        for (let l = 1; l <= 4; l++) { text('F1', 12, 90, y, `Sample lyric line ${l} of verse ${v}, page ${p}.`); y -= 18; }
        y -= 12;
      }
      text('F2', 12, 72, y, 'Chorus'); y -= 20;
      for (let l = 1; l <= 2; l++) { text('F1', 12, 90, y, `Sample chorus line ${l}.`); y -= 18; }
    }
    text('F1', 9, 290, 40, String(p));
    const stream = lines.join('\n');
    const contentNo = add(`<< /Length ${Buffer.byteLength(stream)} >>\nstream\n${stream}\nendstream`);
    pageNos.push(add(`<< /Type /Page /Parent ${pagesNo} 0 R /MediaBox [0 0 612 792] ` +
      `/Resources << /Font << /F1 ${fontNo} 0 R /F2 ${boldNo} 0 R >> >> /Contents ${contentNo} 0 R >>`));
  }
  objects[catalogNo - 1] = `<< /Type /Catalog /Pages ${pagesNo} 0 R >>`;
  objects[pagesNo - 1] = `<< /Type /Pages /Kids [${pageNos.map((n) => `${n} 0 R`).join(' ')}] /Count ${pageNos.length} >>`;

  let out = '%PDF-1.4\n%\xe2\xe3\xcf\xd3\n';
  const offsets = [];
  objects.forEach((body, i) => {
    offsets.push(Buffer.byteLength(out, 'latin1'));
    out += `${i + 1} 0 obj\n${body}\nendobj\n`;
  });
  const xref = Buffer.byteLength(out, 'latin1');
  out += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
  out += offsets.map((o) => `${String(o).padStart(10, '0')} 00000 n \n`).join('');
  out += `trailer\n<< /Size ${objects.length + 1} /Root ${catalogNo} 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
  return Buffer.from(out, 'latin1');
}

// ------------------------------------------------------------------ PNG cover generation

const crcTable = Array.from({ length: 256 }, (_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c >>> 0;
});
function crc32(buf) {
  let c = 0xffffffff;
  for (const b of buf) c = crcTable[(c ^ b) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}
function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td));
  return Buffer.concat([len, td, crc]);
}
const hex = (h) => [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16));

/** 600x800 cover: diagonal gradient, a white "page" panel and decorative staff lines. */
function makeCover([from, to]) {
  const W = 600, H = 800;
  const a = hex(from), b = hex(to);
  const raw = Buffer.alloc((W * 3 + 1) * H);
  for (let y = 0; y < H; y++) {
    const row = y * (W * 3 + 1);
    raw[row] = 0;
    for (let x = 0; x < W; x++) {
      const t = (x / W + y / H) / 2;
      let px = a.map((v, i) => Math.round(v + (b[i] - v) * t));
      const inPanel = x > 70 && x < 530 && y > 470 && y < 690;
      if (inPanel) px = px.map((v) => Math.round(v + (255 - v) * 0.85));
      const staff = x > 100 && x < 500 && [520, 545, 570, 595, 620].some((sy) => Math.abs(y - sy) <= 1);
      if (inPanel && staff) px = a;
      const dot = Math.hypot(x - 300, y - 250) < 90 && Math.hypot(x - 300, y - 250) > 80;
      if (dot) px = [255, 255, 255];
      raw.set(px, row + 1 + x * 3);
    }
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(W, 0); ihdr.writeUInt32BE(H, 4); ihdr[8] = 8; ihdr[9] = 2; // 8-bit RGB
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

// ------------------------------------------------------------------ API calls

let token;
async function call(path, init = {}) {
  const res = await fetch(API + path, {
    ...init,
    headers: { Accept: 'application/json', ...(token && { Authorization: `Bearer ${token}` }), ...init.headers },
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`${init.method ?? 'GET'} ${path} -> ${res.status} ${text}`);
  return text ? JSON.parse(text) : undefined;
}
const json = (body) => ({ method: 'POST', body: JSON.stringify(body), headers: { 'Content-Type': 'application/json' } });
const slug = (s) => s.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');

const login = await call('/auth/login', json(ADMIN));
token = login.accessToken;
console.log(`Logged in as ${login.user.email}`);

const existingCats = await call('/admin/categories');
const catIds = Object.fromEntries(existingCats.map((c) => [c.name, c.id]));
for (const c of CATEGORIES) {
  if (catIds[c.name]) { console.log(`  category exists: ${c.name}`); continue; }
  catIds[c.name] = (await call('/admin/categories', json(c))).id;
  console.log(`+ category ${c.name}`);
}

const existingBooks = await call('/admin/books?size=100');
const titles = new Set(existingBooks.content.map((b) => b.title));
for (const [title, cat, status, effectiveDate, expiryDate, pages, colors, description] of BOOKS) {
  if (titles.has(title)) { console.log(`  book exists: ${title}`); continue; }
  const form = new FormData();
  form.append('metadata', new Blob([JSON.stringify({ title, description, categoryId: catIds[cat], effectiveDate, expiryDate, status })], { type: 'application/json' }));
  form.append('pdf', new Blob([makePdf(title, pages)], { type: 'application/pdf' }), `${slug(title)}.pdf`);
  form.append('cover', new Blob([makeCover(colors)], { type: 'image/png' }), `${slug(title)}.png`);
  const book = await call('/admin/books', { method: 'POST', body: form });
  console.log(`+ book [${status}${expiryDate ? `, expires ${expiryDate}` : ''}, effective ${effectiveDate}] ${title} (${book.currentVersion?.pageCount ?? '?'} pages)`);
}

const pub = await call('/books?size=100');
console.log(`\nPublic catalogue now lists ${pub.totalElements} book(s).`);

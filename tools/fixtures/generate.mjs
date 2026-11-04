// Fake test documents banata hai. Koi dependency nahi: sirf Node. Sab naam, PAN aur data nakli hai.
// Usage: node tools/fixtures/generate.mjs
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';

const OUT = path.join(path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1')), '..', '..', 'fixtures', 'generated');
fs.mkdirSync(OUT, { recursive: true });

// ---------- PDF writer ----------
const latin = (s) => Buffer.from(s, 'latin1');

/** pages: [{ w, h, rotate?, content: string, image?: {w,h,rgb:Buffer} }] */
function buildPdf(pages, { encrypt } = {}) {
  const objects = []; // index i => object number i+1
  const add = (o) => { objects.push(o); return objects.length; };
  const catalog = add(null);
  const pagesRoot = add(null);
  const font = add({ dict: '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>' });
  const kids = [];
  for (const p of pages) {
    let resources = `/Font << /F1 ${font} 0 R >>`;
    if (p.image) {
      const img = add({
        dict: `<< /Type /XObject /Subtype /Image /Width ${p.image.w} /Height ${p.image.h} /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /FlateDecode`,
        stream: zlib.deflateSync(p.image.rgb),
      });
      resources += ` /XObject << /Im0 ${img} 0 R >>`;
    }
    const content = add({ dict: '<<', stream: latin(p.content) });
    const rotate = p.rotate ? ` /Rotate ${p.rotate}` : '';
    kids.push(add({
      dict: `<< /Type /Page /Parent ${pagesRoot} 0 R /MediaBox [0 0 ${p.w} ${p.h}]${rotate} /Resources << ${resources} >> /Contents ${content} 0 R >>`,
    }));
  }
  objects[catalog - 1] = { dict: `<< /Type /Catalog /Pages ${pagesRoot} 0 R >>` };
  objects[pagesRoot - 1] = { dict: `<< /Type /Pages /Count ${kids.length} /Kids [${kids.map((k) => `${k} 0 R`).join(' ')}] >>` };

  const id = crypto.createHash('md5').update(String(pages.length)).digest();
  const enc = encrypt ? makeEncryption(encrypt, id) : null;
  let encObj = 0;
  if (enc) encObj = add({ dict: enc.dict, plain: true });

  const chunks = [latin('%PDF-1.6\n%\xE2\xE3\xCF\xD3\n')];
  let offset = chunks[0].length;
  const offsets = [];
  objects.forEach((o, i) => {
    const num = i + 1;
    offsets.push(offset);
    let body;
    if (o.stream) {
      const data = enc && !o.plain ? enc.encryptStream(num, o.stream) : o.stream;
      const dict = o.dict.replace(/\s*>?>?$/, '');
      body = Buffer.concat([latin(`${num} 0 obj\n${dict} /Length ${data.length} >>\nstream\n`), data, latin('\nendstream\nendobj\n')]);
    } else {
      body = latin(`${num} 0 obj\n${o.dict}\nendobj\n`);
    }
    chunks.push(body);
    offset += body.length;
  });
  const xref = offset;
  let table = `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
  for (const off of offsets) table += `${String(off).padStart(10, '0')} 00000 n \n`;
  const hex = id.toString('hex');
  const encRef = enc ? ` /Encrypt ${encObj} 0 R` : '';
  table += `trailer\n<< /Size ${objects.length + 1} /Root ${catalog} 0 R /ID [<${hex}> <${hex}>]${encRef} >>\nstartxref\n${xref}\n%%EOF\n`;
  chunks.push(latin(table));
  return Buffer.concat(chunks);
}

// ---------- PDF standard security handler ----------
const PAD = Buffer.from('28BF4E5E4E758A4164004E56FFFA01082E2E00B6D0683E802F0CA9FE6453697A', 'hex');
const md5 = (...b) => crypto.createHash('md5').update(Buffer.concat(b)).digest();
const padPass = (p) => Buffer.concat([latin(p), PAD]).subarray(0, 32);
function rc4(key, data) {
  const s = Array.from({ length: 256 }, (_, i) => i);
  for (let i = 0, j = 0; i < 256; i++) { j = (j + s[i] + key[i % key.length]) & 255; [s[i], s[j]] = [s[j], s[i]]; }
  const out = Buffer.alloc(data.length);
  for (let n = 0, i = 0, j = 0; n < data.length; n++) {
    i = (i + 1) & 255; j = (j + s[i]) & 255; [s[i], s[j]] = [s[j], s[i]];
    out[n] = data[n] ^ s[(s[i] + s[j]) & 255];
  }
  return out;
}

/** AES-128 (V4/R4), wahi handler jo aaj ke bahut se bank aur sarkari PDF use karte hain. */
function makeEncryption(password, id) {
  const perms = -44;
  const permBuf = Buffer.alloc(4); permBuf.writeInt32LE(perms);
  const keyLen = 16;
  // Owner entry (algorithm 3), owner password = user password
  let ok = md5(padPass(password));
  for (let i = 0; i < 50; i++) ok = md5(ok);
  ok = ok.subarray(0, keyLen);
  let O = padPass(password);
  for (let i = 0; i < 20; i++) O = rc4(Buffer.from(ok.map((b) => b ^ i)), O);
  // File key (algorithm 2)
  let key = md5(padPass(password), O, permBuf, id);
  for (let i = 0; i < 50; i++) key = md5(key.subarray(0, keyLen));
  key = key.subarray(0, keyLen);
  // User entry (algorithm 5)
  let U = md5(PAD, id);
  for (let i = 0; i < 20; i++) U = rc4(Buffer.from(key.map((b) => b ^ i)), U);
  U = Buffer.concat([U, Buffer.alloc(16)]);
  return {
    dict: `<< /Filter /Standard /V 4 /R 4 /Length 128 /P ${perms} /O <${O.toString('hex')}> /U <${U.toString('hex')}> /CF << /StdCF << /CFM /AESV2 /AuthEvent /DocOpen /Length 16 >> >> /StmF /StdCF /StrF /StdCF >>`,
    encryptStream(num, data) {
      const objKey = md5(key, Buffer.from([num & 255, (num >> 8) & 255, (num >> 16) & 255, 0, 0]), latin('sAlT'));
      const iv = crypto.randomBytes(16);
      const c = crypto.createCipheriv('aes-128-cbc', objKey.subarray(0, 16), iv);
      return Buffer.concat([iv, c.update(data), c.final()]);
    },
  };
}

// ---------- Page content helpers ----------
const A4 = { w: 595, h: 842 };
const esc = (s) => s.replace(/([\\()])/g, '\\$1');
function textPage(size, title, lines = 30) {
  let c = `BT /F1 20 Tf 40 ${size.h - 60} Td (${esc(title)}) Tj ET\n`;
  for (let i = 0; i < lines; i++) {
    c += `BT /F1 11 Tf 40 ${size.h - 95 - i * 18} Td (Line ${i + 1}: the quick brown fox reads a very long statement line.) Tj ET\n`;
  }
  // Kinaron par marker: fit-to-width aur clipping aankh se jaanchne ke liye.
  c += `1 0 0 RG 2 w 4 4 ${size.w - 8} ${size.h - 8} re S\n`;
  return { ...size, content: c };
}
function scanPage() {
  const w = 620; const h = 877;
  const rgb = Buffer.alloc(w * h * 3);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const ink = y % 24 < 3 && x > 50 && x < 570 && (x * 7 + y) % 13 > 3;
    const v = ink ? 40 : 235 + ((x * 31 + y * 17) % 15);
    rgb.fill(v, (y * w + x) * 3, (y * w + x) * 3 + 3);
  }
  return { ...A4, image: { w, h, rgb }, content: `q ${A4.w} 0 0 ${A4.h} 0 0 cm /Im0 Do Q\n` };
}

const write = (name, buf) => { fs.writeFileSync(path.join(OUT, name), buf); console.log(name.padEnd(30), buf.length); };

write('simple.pdf', buildPdf([textPage(A4, 'Simple PDF, page 1'), textPage(A4, 'Simple PDF, page 2'), textPage(A4, 'Simple PDF, page 3')]));
write('long-500-pages.pdf', buildPdf(Array.from({ length: 500 }, (_, i) => textPage(A4, `Long PDF, page ${i + 1}`))));
write('mixed-sizes.pdf', buildPdf([
  textPage(A4, 'A4 portrait'),
  textPage({ w: 842, h: 595 }, 'A4 landscape', 20),
  textPage({ w: 300, h: 420 }, 'Small page', 12),
  textPage({ w: 1190, h: 1684 }, 'A2 big page', 60),
  textPage({ w: 612, h: 200 }, 'Short strip page', 3),
]));
write('rotated-pages.pdf', buildPdf([
  textPage(A4, 'Rotate 0'),
  { ...textPage(A4, 'Rotate 90'), rotate: 90 },
  { ...textPage(A4, 'Rotate 180'), rotate: 180 },
  { ...textPage(A4, 'Rotate 270'), rotate: 270 },
]));
write('scan-like.pdf', buildPdf([scanPage(), scanPage()]));

// Nakli identity: naam "Asha Verma", janm 1990-03-15, PAN "ABCDE1234F".
const secret = (t) => [textPage(A4, t), textPage(A4, `${t}, page 2`)];
write('Aadhaar_fake_ASHA1990.pdf', buildPdf(secret('Fake Aadhaar'), { encrypt: 'ASHA1990' }));
write('Form16_fake_ABCDE15031990.pdf', buildPdf(secret('Fake Form 16'), { encrypt: 'ABCDE15031990' }));
write('AIS_fake_abcde1234f15031990.pdf', buildPdf(secret('Fake AIS'), { encrypt: 'abcde1234f15031990' }));
write('corrupt.pdf', latin('%PDF-1.6\nthis is not a real pdf body\n'));
write('empty.pdf', Buffer.alloc(0));

// ---------- CSV ----------
const fd = fs.openSync(path.join(OUT, 'big-500k-rows.csv'), 'w');
fs.writeSync(fd, 'id,name,city,amount,pin\n');
let batch = '';
for (let i = 1; i <= 500000; i++) {
  batch += `${i},"Person ${i}",City ${i % 97},${(i * 37) % 100000}.50,0${110000 + (i % 80000)}\n`;
  if (i % 10000 === 0) { fs.writeSync(fd, batch); batch = ''; }
}
fs.closeSync(fd);
console.log('big-500k-rows.csv'.padEnd(30), fs.statSync(path.join(OUT, 'big-500k-rows.csv')).size);
write('small.csv', latin('name,amount\r\n"Asha, Verma",100\r\nRavi,200\r\n'));
write('plain.txt', Buffer.from('Yeh ek saada text file hai.\nयह हिंदी की लाइन है।\n', 'utf8'));

// ---------- ZIP writer (DOCX/XLSX ke liye) ----------
const crcTable = Array.from({ length: 256 }, (_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc32 = (b) => { let c = 0xffffffff; for (const x of b) c = crcTable[(c ^ x) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
function zip(files) {
  const local = []; const central = []; let offset = 0;
  for (const [name, text] of Object.entries(files)) {
    const raw = Buffer.from(text, 'utf8'); const data = zlib.deflateRawSync(raw); const n = latin(name); const crc = crc32(raw);
    const head = Buffer.alloc(30);
    head.writeUInt32LE(0x04034b50, 0); head.writeUInt16LE(20, 4); head.writeUInt16LE(8, 8); head.writeUInt32LE(crc, 14);
    head.writeUInt32LE(data.length, 18); head.writeUInt32LE(raw.length, 22); head.writeUInt16LE(n.length, 26);
    local.push(head, n, data);
    const cen = Buffer.alloc(46);
    cen.writeUInt32LE(0x02014b50, 0); cen.writeUInt16LE(20, 4); cen.writeUInt16LE(20, 6); cen.writeUInt16LE(8, 10); cen.writeUInt32LE(crc, 16);
    cen.writeUInt32LE(data.length, 20); cen.writeUInt32LE(raw.length, 24); cen.writeUInt16LE(n.length, 28); cen.writeUInt32LE(offset, 42);
    central.push(cen, n);
    offset += head.length + n.length + data.length;
  }
  const cd = Buffer.concat(central); const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0); end.writeUInt16LE(Object.keys(files).length, 8); end.writeUInt16LE(Object.keys(files).length, 10);
  end.writeUInt32LE(cd.length, 12); end.writeUInt32LE(offset, 16);
  return Buffer.concat([...local, cd, end]);
}
const X = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>';
write('sample.docx', zip({
  '[Content_Types].xml': `${X}<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>`,
  '_rels/.rels': `${X}<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>`,
  'word/document.xml': `${X}<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Sample heading</w:t></w:r></w:p><w:p><w:r><w:rPr><w:b/></w:rPr><w:t>Bold</w:t></w:r><w:r><w:t xml:space="preserve"> and normal text.</w:t></w:r></w:p></w:body></w:document>`,
}));
write('sample.xlsx', zip({
  '[Content_Types].xml': `${X}<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>`,
  '_rels/.rels': `${X}<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>`,
  'xl/workbook.xml': `${X}<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Sheet1" sheetId="1" r:id="rId1"/></sheets></workbook>`,
  'xl/_rels/workbook.xml.rels': `${X}<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>`,
  'xl/worksheets/sheet1.xml': `${X}<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>Name</t></is></c><c r="B1" t="inlineStr"><is><t>Amount</t></is></c></row><row r="2"><c r="A2" t="inlineStr"><is><t>Asha</t></is></c><c r="B2"><v>100</v></c></row></sheetData></worksheet>`,
}));

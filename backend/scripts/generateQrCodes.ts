import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { mkdir } from 'node:fs/promises';
import QRCode from 'qrcode';
import sharp from 'sharp';
import { query } from '../src/db/pg.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const outDir = path.join(__dirname, '..', 'public', 'qrcodes');
const QR_SIZE = 400;
const CAPTION_HEIGHT = 90;

// Filenames are the store code + product name (not the cryptic qr_code, e.g. "QR-ST01-MILK")
// so the right file is easy to spot by eye in a Photos gallery/file picker during the demo -
// the qr_code itself still is the encoded QR payload and is unchanged, so scanning/lookup
// behavior is unaffected.
function fileNameFor(storeCode: string, productName: string): string {
  return `${storeCode} - ${productName}`.replace(/[/\\:*?"<>|]/g, '-').trim();
}

function escapeXml(value: string): string {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

// One-time generation: shelf QR codes never change after seeding, so this only needs to be
// re-run if db/seed_mock_sap.sql's shelves change. Output is served statically from /qrcodes/.
async function main() {
  await mkdir(outDir, { recursive: true });
  const shelves = await query<{
    qr_code: string;
    product_name: string;
    store_name: string;
    store_code: string;
    aisle: string | null;
  }>(
    `SELECT s.qr_code, p.name AS product_name, st.name AS store_name, st.code AS store_code, s.aisle
     FROM shelves s
     JOIN products p ON p.id = s.product_id
     JOIN stores st ON st.id = s.store_id
     ORDER BY st.name, p.name`
  );

  for (const shelf of shelves) {
    const name = fileNameFor(shelf.store_code, shelf.product_name);
    const file = path.join(outDir, `${name}.png`);

    // The QR image still encodes shelf.qr_code (what the app looks up) - only the filename
    // and the caption drawn below it are human-readable.
    const qrBuffer = await QRCode.toBuffer(shelf.qr_code, { width: QR_SIZE, margin: 2 });
    const caption = `
      <svg width="${QR_SIZE}" height="${CAPTION_HEIGHT}" xmlns="http://www.w3.org/2000/svg">
        <rect width="100%" height="100%" fill="white" />
        <text x="50%" y="34" font-size="22" font-family="sans-serif" font-weight="bold"
              text-anchor="middle" fill="black">${escapeXml(shelf.product_name)}</text>
        <text x="50%" y="62" font-size="16" font-family="sans-serif"
              text-anchor="middle" fill="#555">${escapeXml(shelf.store_name)} \u00b7 aisle ${escapeXml(shelf.aisle ?? '-')}</text>
      </svg>`;

    await sharp(qrBuffer)
      .extend({ bottom: CAPTION_HEIGHT, background: 'white' })
      .composite([{ input: Buffer.from(caption), top: QR_SIZE, left: 0 }])
      .png()
      .toFile(file);

    console.log(`generated ${name}.png (${shelf.store_name} \u00b7 ${shelf.product_name} \u00b7 aisle ${shelf.aisle ?? '-'})`);
  }

  console.log(`Done: ${shelves.length} QR codes in ${outDir}`);
  process.exit(0);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});

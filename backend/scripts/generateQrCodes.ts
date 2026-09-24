import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { mkdir } from 'node:fs/promises';
import QRCode from 'qrcode';
import { query } from '../src/db/pg.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const outDir = path.join(__dirname, '..', 'public', 'qrcodes');

// One-time generation: shelf QR codes never change after seeding, so this only needs to be
// re-run if db/seed_mock_sap.sql's shelves change. Output is served statically from /qrcodes/.
async function main() {
  await mkdir(outDir, { recursive: true });
  const shelves = await query<{
    qr_code: string;
    product_name: string;
    store_name: string;
    aisle: string | null;
  }>(
    `SELECT s.qr_code, p.name AS product_name, st.name AS store_name, s.aisle
     FROM shelves s
     JOIN products p ON p.id = s.product_id
     JOIN stores st ON st.id = s.store_id
     ORDER BY st.name, p.name`
  );

  for (const shelf of shelves) {
    const file = path.join(outDir, `${shelf.qr_code}.png`);
    await QRCode.toFile(file, shelf.qr_code, { width: 400, margin: 2 });
    console.log(`generated ${shelf.qr_code}.png (${shelf.store_name} \u00b7 ${shelf.product_name} \u00b7 aisle ${shelf.aisle ?? '-'})`);
  }

  console.log(`Done: ${shelves.length} QR codes in ${outDir}`);
  process.exit(0);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});

import { query } from '../src/db/pg.js';
import { embedText } from '../src/ai/embeddings.js';

// One-time backfill: populates products.embedding via the local Ollama embedding model.
// Re-running is a no-op cost-wise (idempotent — just re-embeds the same text deterministically).
async function main() {
  const products = await query<{ id: string; name: string; description: string | null }>(
    'SELECT id, name, description FROM products'
  );

  for (const p of products) {
    const embedding = await embedText([p.name, p.description ?? ''].join(' ').trim());
    await query('UPDATE products SET embedding = $1 WHERE id = $2', [
      JSON.stringify(embedding),
      p.id,
    ]);
    console.log(`embedded ${p.name}`);
  }

  console.log('Product embedding backfill complete.');
  process.exit(0);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});

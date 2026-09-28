import type { FastifyInstance } from 'fastify';
import { randomUUID } from 'node:crypto';
import { writeFile, readFile, unlink } from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import { embedText } from '../ai/embeddings.js';
import { generateAnswer } from '../ai/llm.js';
import { transcribeAudio } from '../ai/stt.js';
import { synthesizeSpeech } from '../ai/tts.js';
import { query } from '../db/pg.js';
import { runCypher } from '../db/neo4j.js';

// Catalog names are short and near-duplicates (e.g. "Whole Milk 1L" vs "Oat Milk 1L"), which
// the embedding of a *full question sentence* doesn't reliably tell apart - that mismatch was
// causing e.g. "alternative to whole milk?" to resolve to Oat Milk 1L (which has no substitute
// of its own), so the answer was always "no known alternative". Prefer a direct keyword match
// on product names first, and only fall back to embeddings for vaguer questions that don't
// name a product explicitly.
function keywordMatch(question: string, products: { id: string; sku: string; name: string }[]) {
  const q = question.toLowerCase();
  let best: { product: (typeof products)[number]; ratio: number } | null = null;
  for (const product of products) {
    const words = product.name.toLowerCase().split(/[^a-z0-9]+/).filter(Boolean);
    if (words.length === 0) continue;
    const matched = words.filter((word) => new RegExp(`\\b${word}\\b`).test(q)).length;
    const ratio = matched / words.length;
    if (matched > 0 && (!best || ratio > best.ratio)) best = { product, ratio };
  }
  // Require a majority of the product's own words to appear, so a single shared word
  // (e.g. "milk") isn't enough on its own to pick a specific product.
  return best && best.ratio >= 0.5 ? best.product : null;
}

// Shared by both the text and voice entry points. The curated `substitutes` table (the same
// one the shelf-action rule engine uses) is the source of truth for what counts as an
// accepted alternative; the graph tells us which *other stores* currently have the product.
async function answerQuestion(question: string, storeId: string) {
  const products = await query<{ id: string; sku: string; name: string }>(
    'SELECT id, sku, name FROM products'
  );
  let likelyProduct: { id: string; sku: string; name: string; similarity?: number } | null =
    keywordMatch(question, products);

  if (!likelyProduct) {
    const embedding = await embedText(question);
    [likelyProduct] = await query(
      `SELECT id, sku, name, 1 - (embedding <=> $1) AS similarity
       FROM products ORDER BY embedding <=> $1 LIMIT 1`,
      [JSON.stringify(embedding)]
    );
  }

  const substitutes = likelyProduct
    ? await query(
        `SELECT sp.name, sp.sku, s.score
         FROM substitutes s JOIN products sp ON sp.id = s.substitute_product_id
         WHERE s.product_id = $1 ORDER BY s.score DESC`,
        [likelyProduct.id]
      )
    : [];

  // Per-store stock status for the identified product.
  const storeAvailability = likelyProduct
    ? (
        await runCypher(
          `MATCH (p:Product {id: $productId})-[:STOCKED_AT]->(s:Shelf)-[:LOCATED_IN]->(st:Store)
           RETURN st.id AS storeId, st.name AS store, s.status AS shelfStatus, s.qty AS qty`,
          { productId: likelyProduct.id }
        )
      ).map((r) => r.toObject() as { storeId: string; store: string; shelfStatus: string; qty: number })
    : [];

  // Pre-filter rather than handing the LLM raw rows plus a storeId to compare - the local
  // model reliably fumbled that comparison (answered "not available elsewhere" even when the
  // context clearly listed another store with stock), so do the "elsewhere" logic here instead.
  const otherStoresInStock = storeAvailability.filter(
    (r) => r.storeId !== storeId && r.shelfStatus !== 'empty'
  );

  const answer = await generateAnswer(question, {
    likelyProduct,
    substitutes,
    otherStoresInStock,
  });
  return { answer, likelyProduct, substitutes, storeAvailability };
}

export async function assistantRoutes(app: FastifyInstance) {
  // Text-first for now; a /assistant/voice endpoint (Phase 6) will wrap this with STT/TTS.
  app.post('/assistant/query', async (req) => {
    const { question, storeId } = req.body as { question: string; storeId: string };
    return answerQuestion(question, storeId);
  });

  // Speech-only endpoint: no STT/RAG, just "say this sentence" - used by the proactive
  // agent to speak its Yes/No prompts (e.g. "shall I guide you to the shelf?") instead of
  // only showing them as text.
  app.post('/assistant/speak', async (req) => {
    const { text } = req.body as { text: string };
    const tmpOut = path.join(os.tmpdir(), `bopis-speak-${randomUUID()}.wav`);
    try {
      await synthesizeSpeech(text, tmpOut);
      const audioBase64 = (await readFile(tmpOut)).toString('base64');
      return { audioBase64 };
    } finally {
      await unlink(tmpOut).catch(() => {});
    }
  });

  // Voice-first flow for the Android app: associate speaks a question, gets a spoken
  // answer back. Entirely local (whisper.cpp -> Ollama RAG -> Piper), no cloud calls.
  app.post('/assistant/voice', async (req) => {
    const data = await req.file();
    if (!data) {
      return { error: 'multipart field "audio" (wav file) is required' };
    }
    const storeId = (data.fields.storeId as { value?: string } | undefined)?.value ?? '';

    const tmpIn = path.join(os.tmpdir(), `bopis-voice-in-${randomUUID()}.wav`);
    const tmpOut = path.join(os.tmpdir(), `bopis-voice-out-${randomUUID()}.wav`);
    await writeFile(tmpIn, await data.toBuffer());

    try {
      const question = await transcribeAudio(tmpIn);
      const result = await answerQuestion(question, storeId);
      await synthesizeSpeech(result.answer, tmpOut);
      const audioBase64 = (await readFile(tmpOut)).toString('base64');
      return { question, ...result, audioBase64 };
    } finally {
      await unlink(tmpIn).catch(() => {});
      await unlink(tmpOut).catch(() => {});
    }
  });
}

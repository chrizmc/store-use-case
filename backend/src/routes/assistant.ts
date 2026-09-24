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

// Shared by both the text and voice entry points. Vector search (pgvector) only identifies
// *which* catalog product the question is likely about - it must NOT be presented as "the
// answer" itself, since a question sentence's embedding vs. short product-name embeddings is
// a fuzzy match. The curated `substitutes` table (the same one the shelf-action rule engine
// uses) is the source of truth for what counts as an accepted alternative.
async function answerQuestion(question: string, storeId: string) {
  const embedding = await embedText(question);
  const [likelyProduct] = await query(
    `SELECT id, sku, name, 1 - (embedding <=> $1) AS similarity
     FROM products ORDER BY embedding <=> $1 LIMIT 1`,
    [JSON.stringify(embedding)]
  );
  const substitutes = likelyProduct
    ? await query(
        `SELECT sp.name, sp.sku, s.score
         FROM substitutes s JOIN products sp ON sp.id = s.substitute_product_id
         WHERE s.product_id = $1 ORDER BY s.score DESC`,
        [likelyProduct.id]
      )
    : [];
  const graphFacts = await runCypher(
    `MATCH (p:Product)-[:STOCKED_AT]->(s:Shelf)-[:LOCATED_IN]->(st:Store)
     WHERE st.id = $storeId
     RETURN p.name AS product, st.name AS store LIMIT 5`,
    { storeId }
  );
  const graphFactsObj = graphFacts.map((r) => r.toObject());
  const answer = await generateAnswer(question, { likelyProduct, substitutes, graphFacts: graphFactsObj });
  return { answer, likelyProduct, substitutes, graphFacts: graphFactsObj };
}

export async function assistantRoutes(app: FastifyInstance) {
  // Text-first for now; a /assistant/voice endpoint (Phase 6) will wrap this with STT/TTS.
  app.post('/assistant/query', async (req) => {
    const { question, storeId } = req.body as { question: string; storeId: string };
    return answerQuestion(question, storeId);
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

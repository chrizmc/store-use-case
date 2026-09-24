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

// Shared by both the text and voice entry points: embeds the question, pulls similar
// products via pgvector and store facts via Neo4j, then asks the local LLM to answer.
async function answerQuestion(question: string, storeId: string) {
  const embedding = await embedText(question);
  const similarProducts = await query(
    `SELECT sku, name, 1 - (embedding <=> $1) AS similarity
     FROM products ORDER BY embedding <=> $1 LIMIT 3`,
    [JSON.stringify(embedding)]
  );
  const graphFacts = await runCypher(
    `MATCH (p:Product)-[:STOCKED_AT]->(s:Shelf)-[:LOCATED_IN]->(st:Store)
     WHERE st.id = $storeId
     RETURN p.name AS product, st.name AS store LIMIT 5`,
    { storeId }
  );
  const graphFactsObj = graphFacts.map((r) => r.toObject());
  const answer = await generateAnswer(question, { similarProducts, graphFacts: graphFactsObj });
  return { answer, similarProducts, graphFacts: graphFactsObj };
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

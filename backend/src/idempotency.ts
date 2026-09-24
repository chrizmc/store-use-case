import type { FastifyRequest, FastifyReply } from 'fastify';
import { query } from './db/pg.js';

// Ensures repeated writes with the same client-supplied key are applied at most once.
export async function withIdempotency<T>(
  req: FastifyRequest,
  reply: FastifyReply,
  handler: () => Promise<T>
): Promise<T | { error: string }> {
  const key = req.headers['idempotency-key'];
  if (!key || typeof key !== 'string') {
    reply.code(400);
    return { error: 'Idempotency-Key header is required' };
  }

  const existing = await query<{ response: T }>(
    'SELECT response FROM processed_requests WHERE idempotency_key = $1',
    [key]
  );
  if (existing.length > 0) {
    return existing[0].response;
  }

  const response = await handler();
  await query(
    'INSERT INTO processed_requests (idempotency_key, response) VALUES ($1, $2) ON CONFLICT (idempotency_key) DO NOTHING',
    [key, JSON.stringify(response)]
  );
  return response;
}

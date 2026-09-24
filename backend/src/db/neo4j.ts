import neo4j from 'neo4j-driver';
import 'dotenv/config';

export const neo4jDriver = neo4j.driver(
  process.env.NEO4J_URI ?? 'bolt://localhost:7687',
  neo4j.auth.basic(process.env.NEO4J_USER ?? 'neo4j', process.env.NEO4J_PASSWORD ?? 'password')
);

export async function runCypher(cypher: string, params: Record<string, unknown> = {}) {
  const session = neo4jDriver.session();
  try {
    const result = await session.run(cypher, params);
    return result.records;
  } finally {
    await session.close();
  }
}

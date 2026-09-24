import { Pool } from 'pg';
import 'dotenv/config';

export const pool = new Pool({
  host: process.env.PGHOST ?? 'localhost',
  port: Number(process.env.PGPORT ?? 5432),
  user: process.env.PGUSER ?? 'postgres',
  password: process.env.PGPASSWORD ?? '',
  database: process.env.PGDATABASE ?? 'bopis',
});

export async function query<T = any>(text: string, params: unknown[] = []): Promise<T[]> {
  const { rows } = await pool.query(text, params);
  return rows;
}

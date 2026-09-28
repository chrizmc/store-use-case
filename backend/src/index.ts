import path from 'node:path';
import { fileURLToPath } from 'node:url';
import Fastify from 'fastify';
import multipart from '@fastify/multipart';
import fastifyStatic from '@fastify/static';
import 'dotenv/config';
import { ordersRoutes } from './routes/orders.js';
import { shelvesRoutes } from './routes/shelves.js';
import { syncRoutes } from './routes/sync.js';
import { rulesRoutes } from './routes/rules.js';
import { assistantRoutes } from './routes/assistant.js';
import { customerOrdersRoutes } from './routes/customerOrders.js';
import { catalogRoutes } from './routes/catalog.js';
import { eventsRoutes } from './routes/events.js';
import { notificationsRoutes } from './routes/notifications.js';
import { startRuleListener } from './rules/listener.js';

const app = Fastify({ logger: true });
const __dirname = path.dirname(fileURLToPath(import.meta.url));

app.register(multipart);
// Serves the small Rules Admin / Customer Order Simulator HTML pages (no build step).
app.register(fastifyStatic, { root: path.join(__dirname, '..', 'public') });
app.register(ordersRoutes);
app.register(shelvesRoutes);
app.register(syncRoutes);
app.register(rulesRoutes);
app.register(assistantRoutes);
app.register(customerOrdersRoutes);
app.register(catalogRoutes);
app.register(eventsRoutes);
app.register(notificationsRoutes);

app.get('/health', async () => ({ status: 'ok' }));

const port = Number(process.env.PORT ?? 3000);

app
  .listen({ port, host: '0.0.0.0' })
  .then(() => startRuleListener())
  .catch((err) => {
    app.log.error(err);
    process.exit(1);
  });

import express from 'express';
import cors from 'cors';
import http from 'http';
import { Server as SocketServer } from 'socket.io';
import { getDatabase, closeDatabase } from './database';
import { seedDatabase } from './seed';
import { setupSocket } from './socket/handler';
import authRoutes from './routes/auth';
import tableRoutes from './routes/tables';
import menuRoutes from './routes/menu';
import orderRoutes from './routes/orders';
import paymentRoutes from './routes/payments';
import reportRoutes from './routes/reports';
import adminRoutes from './routes/admin';

const app = express();
const server = http.createServer(app);
const io = new SocketServer(server, {
  cors: { origin: '*', methods: ['GET', 'POST'] }
});

app.use(cors());
app.use(express.json());

app.use('/api/auth', authRoutes);
app.use('/api/tables', tableRoutes);
app.use('/api/menu', menuRoutes);
app.use('/api/orders', orderRoutes);
app.use('/api/payments', paymentRoutes);
app.use('/api/reports', reportRoutes);
app.use('/api/admin', adminRoutes);

app.get('/api/health', (req, res) => {
  res.json({ status: 'ok', message: 'AB\'s Cafe API is running' });
});

setupSocket(io);

const PORT = process.env.PORT || 3000;
const HOST = process.env.HOST || '0.0.0.0';

getDatabase().then(async () => {
  await seedDatabase();
  server.listen(Number(PORT), HOST, () => {
    console.log(`AB's Cafe backend running on http://${HOST}:${PORT}`);
    console.log(`WebSocket server ready`);
  });
}).catch((err) => {
  console.error('Failed to initialize database:', err);
  process.exit(1);
});

process.on('SIGINT', async () => {
  await closeDatabase();
  process.exit(0);
});

process.on('SIGTERM', async () => {
  await closeDatabase();
  process.exit(0);
});

export default app;

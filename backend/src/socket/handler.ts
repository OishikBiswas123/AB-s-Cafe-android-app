import { Server as SocketServer, Socket } from 'socket.io';
import jwt from 'jsonwebtoken';

const JWT_SECRET = process.env.JWT_SECRET || 'abs-cafe-secret-key-2026';

interface AuthUser {
  id: number;
  name: string;
  email: string;
  role: string;
}

export function setupSocket(io: SocketServer) {
  io.use((socket, next) => {
    const token = socket.handshake.auth?.token;
    if (!token) return next(new Error('Authentication required'));
    try {
      const user = jwt.verify(token, JWT_SECRET) as AuthUser;
      (socket as any).user = user;
      next();
    } catch {
      next(new Error('Invalid token'));
    }
  });

  io.on('connection', (socket: Socket) => {
    const user = (socket as any).user as AuthUser;
    console.log(`${user.name} (${user.role}) connected`);

    socket.join(`role:${user.role}`);

    if (user.role === 'waiter' || user.role === 'owner') {
      socket.join('waiters');
    }

    socket.on('order:place', (orderData) => {
      io.to('role:chef').emit('order:new', orderData);
      io.to('waiters').emit('order:placed', orderData);
    });

    socket.on('order:add-items', (data) => {
      io.to('role:chef').emit('order:modified', data);
      io.to('waiters').emit('order:items-added', data);
    });

    socket.on('order:status', (data) => {
      io.to('waiters').emit('order:update', data);
      io.to('role:cashier').emit('order:status-change', data);
    });

    socket.on('order:paid', (data) => {
      io.emit('table:released', { table_id: data.table_id });
      io.to('role:owner').emit('payment:completed', data);
    });

    socket.on('disconnect', () => {
      console.log(`${user.name} disconnected`);
    });
  });
}

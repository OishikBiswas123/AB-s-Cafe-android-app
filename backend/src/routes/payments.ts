import { Router } from 'express';
import { getDatabase, saveDatabase } from '../database';
import { authenticate, authorize, AuthRequest } from '../middleware/auth';

const router = Router();

router.post('/:orderId/pay', authenticate, authorize('cashier', 'owner'), async (req: AuthRequest, res) => {
  const { orderId } = req.params;
  const { method } = req.body;

  if (!['cash', 'qr'].includes(method)) {
    return res.status(400).json({ error: 'Payment method must be cash or qr' });
  }

  const db = await getDatabase();
  const orderResult = db.exec(
    'SELECT id, total, status FROM orders WHERE id = ?', [orderId]
  );
  if (!orderResult.length) {
    return res.status(404).json({ error: 'Order not found' });
  }

  const [, total, status] = orderResult[0].values[0] as any[];
  if (status === 'paid') {
    return res.status(400).json({ error: 'Order already paid' });
  }

  db.run('INSERT INTO payments (order_id, amount, method, cashier_id) VALUES (?, ?, ?, ?)',
    [orderId, total, method, req.user!.id]);

  db.run('UPDATE orders SET status = ?, updated_at = datetime("now","localtime") WHERE id = ?',
    ['paid', orderId]);

  const tableResult = db.exec('SELECT table_id FROM orders WHERE id = ?', [orderId]);
  if (tableResult.length) {
    const tableId = tableResult[0].values[0][0];
    db.run('UPDATE tables SET status = ? WHERE id = ?', ['available', tableId]);
  }

  saveDatabase();

  res.json({ success: true, message: 'Payment successful, table released' });
});

export default router;

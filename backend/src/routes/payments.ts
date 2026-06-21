import { Router } from 'express';
import { getDatabase } from '../database';
import { authenticate, authorize, AuthRequest } from '../middleware/auth';

const router = Router();

router.post('/:orderId/pay', authenticate, authorize('cashier', 'owner'), async (req: AuthRequest, res) => {
  const { orderId } = req.params;
  const { method } = req.body;

  if (!['cash', 'qr'].includes(method)) {
    return res.status(400).json({ error: 'Payment method must be cash or qr' });
  }

  const db = await getDatabase();
  const orderResult = await db.query(
    'SELECT id, total, status FROM orders WHERE id = $1', [orderId]
  );
  if (!orderResult.rows.length) {
    return res.status(404).json({ error: 'Order not found' });
  }

  const order = orderResult.rows[0];
  if (order.status === 'paid') {
    return res.status(400).json({ error: 'Order already paid' });
  }

  await db.query('INSERT INTO payments (order_id, amount, method, cashier_id) VALUES ($1, $2, $3, $4)',
    [orderId, order.total, method, req.user!.id]);

  await db.query('UPDATE orders SET status = $1, updated_at = NOW() WHERE id = $2',
    ['paid', orderId]);

  const tableResult = await db.query('SELECT table_id FROM orders WHERE id = $1', [orderId]);
  if (tableResult.rows.length) {
    const tableId = tableResult.rows[0].table_id;
    await db.query('UPDATE tables SET status = $1 WHERE id = $2', ['available', tableId]);
  }

  res.json({ success: true, message: 'Payment successful, table released' });
});

export default router;

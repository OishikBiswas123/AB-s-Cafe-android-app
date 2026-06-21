import { Router } from 'express';
import { getDatabase } from '../database';
import { authenticate, authorize, AuthRequest } from '../middleware/auth';

const router = Router();

router.get('/', authenticate, async (req, res) => {
  const db = await getDatabase();
  const result = await db.query('SELECT id, number, status FROM tables ORDER BY number');
  res.json(result.rows);
});

router.patch('/:id/status', authenticate, authorize('owner', 'waiter', 'cashier'), async (req: AuthRequest, res) => {
  const { id } = req.params;
  const { status } = req.body;
  if (!['available', 'occupied'].includes(status)) {
    return res.status(400).json({ error: 'Invalid status' });
  }
  const db = await getDatabase();
  await db.query('UPDATE tables SET status = $1 WHERE id = $2', [status, id]);
  res.json({ success: true });
});

router.post('/', authenticate, authorize('owner'), async (req, res) => {
  const { number } = req.body;
  if (number === undefined || number === null || number === '') return res.status(400).json({ error: 'Table number required' });
  const db = await getDatabase();
  try {
    const result = await db.query(
      'INSERT INTO tables (number, status) VALUES ($1, $2) RETURNING id',
      [number, 'available']
    );
    res.json({ success: true, id: result.rows[0].id });
  } catch {
    res.status(409).json({ error: 'Table number already exists' });
  }
});

router.delete('/:id', authenticate, authorize('owner'), async (req, res) => {
  const { id } = req.params;
  const db = await getDatabase();
  await db.query('DELETE FROM tables WHERE id = $1', [id]);
  res.json({ success: true });
});

export default router;

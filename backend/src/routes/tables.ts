import { Router } from 'express';
import { getDatabase } from '../database';
import { authenticate, authorize, AuthRequest } from '../middleware/auth';

const router = Router();

router.get('/', authenticate, async (req, res) => {
  const db = await getDatabase();
  const result = db.exec('SELECT id, number, status FROM tables ORDER BY number');
  const tables = result[0]?.values.map((row: any[]) => ({
    id: row[0], number: row[1], status: row[2]
  })) || [];
  res.json(tables);
});

router.patch('/:id/status', authenticate, authorize('owner', 'waiter', 'cashier'), async (req: AuthRequest, res) => {
  const { id } = req.params;
  const { status } = req.body;
  if (!['available', 'occupied'].includes(status)) {
    return res.status(400).json({ error: 'Invalid status' });
  }
  const db = await getDatabase();
  db.run('UPDATE tables SET status = ? WHERE id = ?', [status, id]);
  res.json({ success: true });
});

router.post('/', authenticate, authorize('owner'), async (req, res) => {
  const { number } = req.body;
  if (!number) return res.status(400).json({ error: 'Table number required' });
  const db = await getDatabase();
  try {
    db.run('INSERT INTO tables (number, status) VALUES (?, ?)', [number, 'available']);
    res.json({ success: true, id: db.exec("SELECT last_insert_rowid()")[0].values[0][0] });
  } catch {
    res.status(409).json({ error: 'Table number already exists' });
  }
});

router.delete('/:id', authenticate, authorize('owner'), async (req, res) => {
  const { id } = req.params;
  const db = await getDatabase();
  db.run('DELETE FROM tables WHERE id = ?', [id]);
  res.json({ success: true });
});

export default router;

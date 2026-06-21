import { Router } from 'express';
import bcrypt from 'bcryptjs';
import { getDatabase } from '../database';
import { authenticate, authorize, AuthRequest } from '../middleware/auth';

const router = Router();

router.get('/users', authenticate, authorize('owner'), async (req, res) => {
  const db = await getDatabase();
  const result = await db.query('SELECT id, name, email, role, created_at FROM users ORDER BY id');
  res.json(result.rows);
});

router.post('/users', authenticate, authorize('owner'), async (req, res) => {
  const { name, email, password, role } = req.body;
  if (!name || !email || !password || !role) {
    return res.status(400).json({ error: 'Name, email, password, and role required' });
  }
  if (!['owner', 'waiter', 'chef', 'cashier'].includes(role)) {
    return res.status(400).json({ error: 'Invalid role' });
  }

  const db = await getDatabase();
  const hash = await bcrypt.hash(password, 10);
  try {
    const result = await db.query(
      'INSERT INTO users (name, email, password_hash, role) VALUES ($1, $2, $3, $4) RETURNING id',
      [name, email, hash, role]
    );
    res.json({ success: true, id: result.rows[0].id });
  } catch {
    res.status(409).json({ error: 'Email already exists' });
  }
});

router.delete('/users/:id', authenticate, authorize('owner'), async (req, res) => {
  const { id } = req.params;
  const db = await getDatabase();
  const userResult = await db.query('SELECT email FROM users WHERE id = $1', [id]);
  if (userResult.rows.length === 0) {
    return res.status(404).json({ error: 'User not found' });
  }
  const protectedEmails = ['owner@abscafe.com', 'waiter@abscafe.com', 'chef@abscafe.com', 'cashier@abscafe.com'];
  if (protectedEmails.includes(userResult.rows[0].email)) {
    return res.status(403).json({ error: 'Cannot delete default users' });
  }
  await db.query('DELETE FROM users WHERE id = $1', [id]);
  res.json({ success: true });
});

router.patch('/users/:id/reset-password', authenticate, authorize('owner'), async (req, res) => {
  const { id } = req.params;
  const { password } = req.body;
  if (!password) return res.status(400).json({ error: 'Password required' });
  const db = await getDatabase();
  const hash = await bcrypt.hash(password, 10);
  await db.query('UPDATE users SET password_hash = $1 WHERE id = $2', [hash, id]);
  res.json({ success: true });
});

router.delete('/clear-data', authenticate, authorize('owner'), async (req, res) => {
  const db = await getDatabase();
  await db.query('DELETE FROM payments');
  await db.query('DELETE FROM order_addons');
  await db.query('DELETE FROM order_items');
  await db.query('DELETE FROM orders');
  await db.query('UPDATE tables SET status = $1', ['available']);
  res.json({ success: true, message: 'All data cleared successfully' });
});

export default router;

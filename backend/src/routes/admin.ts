import { Router } from 'express';
import bcrypt from 'bcryptjs';
import { getDatabase, saveDatabase } from '../database';
import { authenticate, authorize, AuthRequest } from '../middleware/auth';

const router = Router();

router.get('/users', authenticate, authorize('owner'), async (req, res) => {
  const db = await getDatabase();
  const result = db.exec('SELECT id, name, email, role, created_at FROM users ORDER BY id');
  const users = result[0]?.values.map((row: any[]) => ({
    id: row[0], name: row[1], email: row[2], role: row[3], created_at: row[4]
  })) || [];
  res.json(users);
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
    db.run('INSERT INTO users (name, email, password_hash, role) VALUES (?, ?, ?, ?)',
      [name, email, hash, role]);
    saveDatabase();
    res.json({ success: true, id: db.exec("SELECT last_insert_rowid()")[0].values[0][0] });
  } catch {
    res.status(409).json({ error: 'Email already exists' });
  }
});

router.delete('/users/:id', authenticate, authorize('owner'), async (req, res) => {
  const { id } = req.params;
  const db = await getDatabase();
  db.run('DELETE FROM users WHERE id = ? AND role != ?', [id, 'owner']);
  saveDatabase();
  res.json({ success: true });
});

router.patch('/users/:id/reset-password', authenticate, authorize('owner'), async (req, res) => {
  const { id } = req.params;
  const { password } = req.body;
  if (!password) return res.status(400).json({ error: 'Password required' });
  const db = await getDatabase();
  const hash = await bcrypt.hash(password, 10);
  db.run('UPDATE users SET password_hash = ? WHERE id = ?', [hash, id]);
  saveDatabase();
  res.json({ success: true });
});

export default router;

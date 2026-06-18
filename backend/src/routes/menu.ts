import { Router } from 'express';
import { getDatabase, saveDatabase } from '../database';
import { authenticate, authorize, AuthRequest } from '../middleware/auth';

const router = Router();

router.get('/categories', authenticate, async (req, res) => {
  const db = await getDatabase();
  const result = db.exec('SELECT id, name, sort_order FROM categories ORDER BY sort_order');
  const categories = result[0]?.values.map((row: any[]) => ({
    id: row[0], name: row[1], sort_order: row[2]
  })) || [];
  res.json(categories);
});

router.get('/items', authenticate, async (req, res) => {
  const db = await getDatabase();
  const { category_id } = req.query;
  let sql = 'SELECT id, name, description, price, category_id, available FROM menu_items';
  const params: any[] = [];
  if (category_id) {
    sql += ' WHERE category_id = ?';
    params.push(Number(category_id));
  }
  sql += ' ORDER BY id';
  const result = db.exec(sql, params);
  const items = result[0]?.values.map((row: any[]) => ({
    id: row[0], name: row[1], description: row[2], price: row[3],
    category_id: row[4], available: !!row[5]
  })) || [];
  res.json(items);
});

router.post('/items', authenticate, authorize('owner'), async (req, res) => {
  const { name, price, category_id, description } = req.body;
  if (!name || !price || !category_id) {
    return res.status(400).json({ error: 'Name, price, and category_id required' });
  }
  const db = await getDatabase();
  db.run(
    'INSERT INTO menu_items (name, description, price, category_id) VALUES (?, ?, ?, ?)',
    [name, description || '', price, category_id]
  );
  saveDatabase();
  res.json({ success: true, id: db.exec("SELECT last_insert_rowid()")[0].values[0][0] });
});

router.put('/items/:id', authenticate, authorize('owner'), async (req, res) => {
  const { id } = req.params;
  const { name, price, category_id, description, available } = req.body;
  const db = await getDatabase();
  db.run(
    `UPDATE menu_items SET name=?, price=?, category_id=?, description=?, available=? WHERE id=?`,
    [name, price, category_id, description || '', available ? 1 : 0, id]
  );
  saveDatabase();
  res.json({ success: true });
});

router.delete('/items/:id', authenticate, authorize('owner'), async (req, res) => {
  const { id } = req.params;
  const db = await getDatabase();
  db.run('DELETE FROM menu_items WHERE id = ?', [id]);
  saveDatabase();
  res.json({ success: true });
});

export default router;

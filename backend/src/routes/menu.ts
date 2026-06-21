import { Router } from 'express';
import { getDatabase } from '../database';
import { authenticate, authorize } from '../middleware/auth';

const router = Router();

router.get('/categories', authenticate, async (req, res) => {
  const db = await getDatabase();
  const result = await db.query('SELECT id, name, sort_order FROM categories ORDER BY sort_order');
  res.json(result.rows);
});

router.get('/items', authenticate, async (req, res) => {
  const db = await getDatabase();
  const { category_id } = req.query;
  let sql = 'SELECT id, name, description, price, category_id, available FROM menu_items';
  const params: any[] = [];
  if (category_id) {
    sql += ' WHERE category_id = $1';
    params.push(Number(category_id));
  }
  sql += ' ORDER BY id';
  const result = await db.query(sql, params);
  res.json(result.rows.map((row: any) => ({
    ...row,
    available: !!row.available
  })));
});

router.post('/items', authenticate, authorize('owner'), async (req, res) => {
  const { name, price, category_id, description } = req.body;
  if (!name || !price || !category_id) {
    return res.status(400).json({ error: 'Name, price, and category_id required' });
  }
  const db = await getDatabase();
  const result = await db.query(
    'INSERT INTO menu_items (name, description, price, category_id) VALUES ($1, $2, $3, $4) RETURNING id',
    [name, description || '', price, category_id]
  );
  res.json({ success: true, id: result.rows[0].id });
});

router.put('/items/:id', authenticate, authorize('owner', 'chef'), async (req, res) => {
  const { id } = req.params;
  const { name, price, category_id, description, available } = req.body;
  const db = await getDatabase();
  await db.query(
    `UPDATE menu_items SET name=$1, price=$2, category_id=$3, description=$4, available=$5 WHERE id=$6`,
    [name, price, category_id, description || '', available, id]
  );
  res.json({ success: true });
});

router.delete('/items/:id', authenticate, authorize('owner'), async (req, res) => {
  const { id } = req.params;
  const db = await getDatabase();
  await db.query('DELETE FROM menu_items WHERE id = $1', [id]);
  res.json({ success: true });
});

export default router;

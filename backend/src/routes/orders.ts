import { Router } from 'express';
import { getDatabase } from '../database';
import { authenticate, authorize, AuthRequest } from '../middleware/auth';

const router = Router();

interface OrderItemInput {
  menu_item_id: number;
  quantity: number;
  price: number;
  notes?: string;
  addons?: { name: string; price: number }[];
}

router.post('/', authenticate, authorize('waiter', 'owner'), async (req: AuthRequest, res) => {
  const { table_id, items, is_takeaway } = req.body;
  if (!table_id || !items?.length) {
    return res.status(400).json({ error: 'Table ID and items required' });
  }

  const db = await getDatabase();
  const total = items.reduce((sum: number, item: OrderItemInput) => {
    const addonTotal = (item.addons || []).reduce((a: number, ad: any) => a + ad.price, 0);
    return sum + (item.price + addonTotal) * item.quantity;
  }, 0);

  const orderResult = await db.query(
    'INSERT INTO orders (table_id, waiter_id, status, total, is_takeaway) VALUES ($1, $2, $3, $4, $5) RETURNING id',
    [table_id, req.user!.id, 'pending', total, is_takeaway ? true : false]
  );
  const orderId = orderResult.rows[0].id;

  for (const item of items) {
    const itemResult = await db.query(
      'INSERT INTO order_items (order_id, menu_item_id, quantity, price, notes) VALUES ($1, $2, $3, $4, $5) RETURNING id',
      [orderId, item.menu_item_id, item.quantity, item.price, item.notes || '']
    );
    const orderItemId = itemResult.rows[0].id;

    if (item.addons?.length) {
      for (const addon of item.addons) {
        await db.query('INSERT INTO order_addons (order_item_id, name, price) VALUES ($1, $2, $3)',
          [orderItemId, addon.name, addon.price]);
      }
    }
  }

  await db.query('UPDATE tables SET status = $1 WHERE id = $2', ['occupied', table_id]);

  const order = await getOrderById(orderId);
  res.status(201).json(order);
});

router.get('/', authenticate, async (req: AuthRequest, res) => {
  const db = await getDatabase();
  const { status, table_id } = req.query;

  let sql = `SELECT o.id, o.table_id, t.number as table_number, o.waiter_id, u.name as waiter_name,
    o.status, o.total, o.is_takeaway, o.created_at, o.updated_at
    FROM orders o
    JOIN tables t ON t.id = o.table_id
    JOIN users u ON u.id = o.waiter_id
    WHERE 1=1`;
  const params: any[] = [];
  let paramIndex = 1;

  if (req.user?.role === 'chef') {
    sql += " AND o.status IN ('pending','preparing','ready')";
  }

  if (status) {
    sql += ` AND o.status = $${paramIndex++}`;
    params.push(status);
  }
  if (table_id) {
    sql += ` AND o.table_id = $${paramIndex++}`;
    params.push(Number(table_id));
  }
  sql += ' ORDER BY o.created_at DESC';

  const result = await db.query(sql, params);
  const orders = result.rows;

  for (const order of orders) {
    const itemsResult = await db.query(
      `SELECT oi.id, oi.menu_item_id, mi.name, oi.quantity, oi.price, oi.notes, oi.status
       FROM order_items oi
       JOIN menu_items mi ON mi.id = oi.menu_item_id
       WHERE oi.order_id = $1
       ORDER BY oi.id`,
      [order.id]
    );
    order.items = itemsResult.rows;

    for (const item of order.items) {
      const addonResult = await db.query(
        'SELECT id, name, price FROM order_addons WHERE order_item_id = $1',
        [item.id]
      );
      item.addons = addonResult.rows;
    }
  }

  res.json(orders);
});

router.get('/:id', authenticate, async (req, res) => {
  const order = await getOrderById(Number(req.params.id));
  if (!order) return res.status(404).json({ error: 'Order not found' });
  res.json(order);
});

router.post('/:id/items', authenticate, authorize('waiter', 'owner'), async (req: AuthRequest, res) => {
  const { id } = req.params;
  const { items } = req.body;
  if (!items?.length) return res.status(400).json({ error: 'Items required' });

  const db = await getDatabase();
  const orderResult = await db.query('SELECT status FROM orders WHERE id = $1', [id]);
  if (!orderResult.rows.length) return res.status(404).json({ error: 'Order not found' });

  let addonTotal = 0;
  for (const item of items) {
    const itemAddons = (item.addons || []).reduce((a: number, ad: any) => a + ad.price, 0);
    addonTotal += itemAddons * item.quantity;
    const itemResult = await db.query(
      'INSERT INTO order_items (order_id, menu_item_id, quantity, price, notes) VALUES ($1, $2, $3, $4, $5) RETURNING id',
      [id, item.menu_item_id, item.quantity, item.price, item.notes || '']
    );
    const orderItemId = itemResult.rows[0].id;
    if (item.addons?.length) {
      for (const addon of item.addons) {
        await db.query('INSERT INTO order_addons (order_item_id, name, price) VALUES ($1, $2, $3)',
          [orderItemId, addon.name, addon.price]);
      }
    }
  }

  const itemTotal = items.reduce((s: number, i: OrderItemInput) => s + i.price * i.quantity, 0);
  await db.query('UPDATE orders SET total = total + $1, updated_at = NOW() WHERE id = $2',
    [itemTotal + addonTotal, id]);

  const order = await getOrderById(Number(id));
  res.json(order);
});

router.patch('/:id/status', authenticate, authorize('waiter', 'chef', 'owner'), async (req: AuthRequest, res) => {
  const { id } = req.params;
  const { status } = req.body;
  const validStatuses = ['pending', 'preparing', 'ready', 'served'];
  if (!validStatuses.includes(status)) {
    return res.status(400).json({ error: 'Invalid status' });
  }

  const db = await getDatabase();
  await db.query('UPDATE orders SET status = $1, updated_at = NOW() WHERE id = $2', [status, id]);

  const order = await getOrderById(Number(id));
  res.json(order);
});

router.patch('/items/:itemId/status', authenticate, authorize('chef', 'owner'), async (req, res) => {
  const { itemId } = req.params;
  const { status } = req.body;
  const validStatuses = ['pending', 'preparing', 'ready', 'served'];
  if (!validStatuses.includes(status)) {
    return res.status(400).json({ error: 'Invalid status' });
  }

  const db = await getDatabase();
  await db.query('UPDATE order_items SET status = $1 WHERE id = $2', [status, itemId]);
  res.json({ success: true });
});

async function getOrderById(orderId: number) {
  const db = await getDatabase();
  const result = await db.query(
    `SELECT o.id, o.table_id, t.number as table_number, o.waiter_id, u.name as waiter_name,
     o.status, o.total, o.is_takeaway, o.created_at, o.updated_at
     FROM orders o
     JOIN tables t ON t.id = o.table_id
     JOIN users u ON u.id = o.waiter_id
     WHERE o.id = $1`,
    [orderId]
  );
  if (!result.rows.length) return null;

  const order = result.rows[0];

  const itemsResult = await db.query(
    `SELECT oi.id, oi.menu_item_id, mi.name, oi.quantity, oi.price, oi.notes, oi.status
     FROM order_items oi
     JOIN menu_items mi ON mi.id = oi.menu_item_id
     WHERE oi.order_id = $1
     ORDER BY oi.id`,
    [orderId]
  );
  order.items = itemsResult.rows;

  for (const item of order.items) {
    const addonResult = await db.query(
      'SELECT id, name, price FROM order_addons WHERE order_item_id = $1', [item.id]
    );
    item.addons = addonResult.rows;
  }

  return order;
}

export default router;

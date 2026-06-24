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

  if (req.user?.role === 'chef' || req.user?.role === 'drinks_chef') {
    const itemType = req.user.role === 'drinks_chef' ? 'beverage' : 'food';
    sql += ` AND o.id IN (SELECT DISTINCT oi.order_id FROM order_items oi JOIN menu_items mi ON mi.id = oi.menu_item_id WHERE mi.type = '${itemType}')`;
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
    let itemTypeFilter = '';
    if (req.user?.role === 'chef') itemTypeFilter = " AND mi.type = 'food'";
    else if (req.user?.role === 'drinks_chef') itemTypeFilter = " AND mi.type = 'beverage'";

    const itemsResult = await db.query(
      `SELECT oi.id, oi.menu_item_id, mi.name, oi.quantity, oi.price, oi.notes, oi.status
       FROM order_items oi
       JOIN menu_items mi ON mi.id = oi.menu_item_id
       WHERE oi.order_id = $1${itemTypeFilter}
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

router.get('/:id', authenticate, async (req: AuthRequest, res) => {
  const order = await getOrderById(Number(req.params.id), req.user?.role);
  if (!order) return res.status(404).json({ error: 'Order not found' });
  res.json(order);
});

router.post('/:id/items', authenticate, authorize('waiter', 'cashier', 'owner'), async (req: AuthRequest, res) => {
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

router.patch('/:id/status', authenticate, authorize('waiter', 'chef', 'drinks_chef', 'owner'), async (req: AuthRequest, res) => {
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

router.patch('/items/:itemId/status', authenticate, authorize('chef', 'drinks_chef', 'owner', 'waiter'), async (req, res) => {
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

router.delete('/items/:itemId', authenticate, authorize('cashier', 'waiter', 'owner'), async (req: AuthRequest, res) => {
  const { itemId } = req.params;
  const db = await getDatabase();

  const itemResult = await db.query('SELECT order_id FROM order_items WHERE id = $1', [itemId]);
  if (!itemResult.rows.length) {
    return res.status(404).json({ error: 'Item not found' });
  }
  const orderId = itemResult.rows[0].order_id;

  await db.query('DELETE FROM order_addons WHERE order_item_id = $1', [itemId]);
  await db.query('DELETE FROM order_items WHERE id = $1', [itemId]);

  const totalResult = await db.query(`
    SELECT COALESCE(SUM(
      oi.price * oi.quantity + COALESCE(
        (SELECT SUM(oa.price) * oi.quantity FROM order_addons oa WHERE oa.order_item_id = oi.id), 0
      )
    ), 0) as total FROM order_items oi WHERE oi.order_id = $1
  `, [orderId]);

  const countResult = await db.query('SELECT COUNT(*) as count FROM order_items WHERE order_id = $1', [orderId]);

  if (countResult.rows[0].count > 0) {
    await db.query('UPDATE orders SET total = $1, updated_at = NOW() WHERE id = $2', [totalResult.rows[0].total, orderId]);
  } else {
    const tableResult = await db.query('SELECT table_id FROM orders WHERE id = $1', [orderId]);
    if (tableResult.rows.length) {
      await db.query('UPDATE tables SET status = $1 WHERE id = $2', ['available', tableResult.rows[0].table_id]);
    }
    await db.query('UPDATE orders SET status = $1, total = 0, updated_at = NOW() WHERE id = $2', ['void', orderId]);
  }

  const updatedOrder = await getOrderById(orderId);
  res.json({ success: true, order: updatedOrder });
});

router.post('/:id/split', authenticate, authorize('cashier', 'owner'), async (req: AuthRequest, res) => {
  const { id } = req.params;
  const { groups } = req.body;

  if (!groups || !Array.isArray(groups) || groups.length === 0) {
    return res.status(400).json({ error: 'Groups array required' });
  }

  const db = await getDatabase();
  const orderResult = await db.query('SELECT * FROM orders WHERE id = $1', [id]);
  if (!orderResult.rows.length) {
    return res.status(404).json({ error: 'Order not found' });
  }
  const originalOrder = orderResult.rows[0];
  const createdOrders: any[] = [];

  for (const group of groups) {
    if (!Array.isArray(group) || group.length === 0) continue;

    const itemsResult = await db.query(`
      SELECT oi.*, COALESCE((SELECT SUM(oa.price) FROM order_addons oa WHERE oa.order_item_id = oi.id), 0) as addon_total
      FROM order_items oi WHERE oi.id = ANY($1::int[])
    `, [group]);

    if (itemsResult.rows.length === 0) continue;

    const total = itemsResult.rows.reduce((sum: number, item: any) => {
      return sum + (item.price * item.quantity) + (item.addon_total * item.quantity);
    }, 0);

    const newOrderResult = await db.query(
      'INSERT INTO orders (table_id, waiter_id, status, total, is_takeaway) VALUES ($1, $2, $3, $4, $5) RETURNING id',
      [originalOrder.table_id, req.user!.id, 'pending', total, originalOrder.is_takeaway]
    );
    const newOrderId = newOrderResult.rows[0].id;

    await db.query('UPDATE order_items SET order_id = $1 WHERE id = ANY($2::int[])', [newOrderId, group]);
    await db.query('UPDATE tables SET status = $1 WHERE id = $2 AND status != $3', ['occupied', originalOrder.table_id, 'occupied']);

    createdOrders.push(await getOrderById(newOrderId));
  }

  const totalResult = await db.query(`
    SELECT COALESCE(SUM(
      oi.price * oi.quantity + COALESCE(
        (SELECT SUM(oa.price) * oi.quantity FROM order_addons oa WHERE oa.order_item_id = oi.id), 0
      )
    ), 0) as total FROM order_items oi WHERE oi.order_id = $1
  `, [id]);

  const countResult = await db.query('SELECT COUNT(*) as count FROM order_items WHERE order_id = $1', [id]);

  if (countResult.rows[0].count > 0) {
    await db.query('UPDATE orders SET total = $1, updated_at = NOW() WHERE id = $2', [totalResult.rows[0].total, id]);
    createdOrders.push(await getOrderById(Number(id)));
  } else {
    await db.query('UPDATE orders SET status = $1, total = 0, updated_at = NOW() WHERE id = $2', ['void', id]);
    createdOrders.push(await getOrderById(Number(id)));
  }

  res.json({ success: true, orders: createdOrders });
});

async function getOrderById(orderId: number, role?: string) {
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

  let itemTypeFilter = '';
  if (role === 'chef') itemTypeFilter = " AND mi.type = 'food'";
  else if (role === 'drinks_chef') itemTypeFilter = " AND mi.type = 'beverage'";

  const itemsResult = await db.query(
    `SELECT oi.id, oi.menu_item_id, mi.name, oi.quantity, oi.price, oi.notes, oi.status
     FROM order_items oi
     JOIN menu_items mi ON mi.id = oi.menu_item_id
     WHERE oi.order_id = $1${itemTypeFilter}
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

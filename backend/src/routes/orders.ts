import { Router } from 'express';
import { getDatabase, saveDatabase } from '../database';
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

  db.run(
    'INSERT INTO orders (table_id, waiter_id, status, total, is_takeaway) VALUES (?, ?, ?, ?, ?)',
    [table_id, req.user!.id, 'pending', total, is_takeaway ? 1 : 0]
  );
  const orderId = db.exec("SELECT last_insert_rowid()")[0].values[0][0] as number;

  for (const item of items) {
    db.run(
      'INSERT INTO order_items (order_id, menu_item_id, quantity, price, notes) VALUES (?, ?, ?, ?, ?)',
      [orderId, item.menu_item_id, item.quantity, item.price, item.notes || '']
    );
    const orderItemId = db.exec("SELECT last_insert_rowid()")[0].values[0][0] as number;

    if (item.addons?.length) {
      for (const addon of item.addons) {
        db.run('INSERT INTO order_addons (order_item_id, name, price) VALUES (?, ?, ?)',
          [orderItemId, addon.name, addon.price]);
      }
    }
  }

  db.run('UPDATE tables SET status = ? WHERE id = ?', ['occupied', table_id]);
  saveDatabase();

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

  if (req.user?.role === 'chef') {
    sql += " AND o.status IN ('pending','preparing','ready')";
  }

  if (status) {
    sql += ' AND o.status = ?';
    params.push(status);
  }
  if (table_id) {
    sql += ' AND o.table_id = ?';
    params.push(Number(table_id));
  }
  sql += ' ORDER BY o.created_at DESC';

  const result = db.exec(sql, params);
  const orders = result[0]?.values.map((row: any[]) => {
    const order: any = {
      id: row[0], table_id: row[1], table_number: row[2], waiter_id: row[3],
      waiter_name: row[4], status: row[5], total: row[6], is_takeaway: !!row[7],
      created_at: row[8], updated_at: row[9], items: []
    };
    return order;
  }) || [];

  for (const order of orders) {
    const itemsResult = db.exec(
      `SELECT oi.id, oi.menu_item_id, mi.name, oi.quantity, oi.price, oi.notes, oi.status
       FROM order_items oi
       JOIN menu_items mi ON mi.id = oi.menu_item_id
       WHERE oi.order_id = ?
       ORDER BY oi.id`,
      [order.id]
    );
    order.items = itemsResult[0]?.values.map((row: any[]) => {
      const item: any = {
        id: row[0], menu_item_id: row[1], name: row[2],
        quantity: row[3], price: row[4], notes: row[5], status: row[6], addons: []
      };
      return item;
    }) || [];

    for (const item of order.items) {
      const addonResult = db.exec(
        'SELECT id, name, price FROM order_addons WHERE order_item_id = ?',
        [item.id]
      );
      item.addons = addonResult[0]?.values.map((row: any[]) => ({
        id: row[0], name: row[1], price: row[2]
      })) || [];
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
  const orderResult = db.exec('SELECT status FROM orders WHERE id = ?', [id]);
  if (!orderResult.length) return res.status(404).json({ error: 'Order not found' });

  let addonTotal = 0;
  for (const item of items) {
    const itemAddons = (item.addons || []).reduce((a: number, ad: any) => a + ad.price, 0);
    addonTotal += itemAddons * item.quantity;
    db.run(
      'INSERT INTO order_items (order_id, menu_item_id, quantity, price, notes) VALUES (?, ?, ?, ?, ?)',
      [id, item.menu_item_id, item.quantity, item.price, item.notes || '']
    );
    const orderItemId = db.exec("SELECT last_insert_rowid()")[0].values[0][0] as number;
    if (item.addons?.length) {
      for (const addon of item.addons) {
        db.run('INSERT INTO order_addons (order_item_id, name, price) VALUES (?, ?, ?)',
          [orderItemId, addon.name, addon.price]);
      }
    }
  }

  const itemTotal = items.reduce((s: number, i: OrderItemInput) => s + i.price * i.quantity, 0);
  db.run('UPDATE orders SET total = total + ?, updated_at = datetime("now","localtime") WHERE id = ?',
    [itemTotal + addonTotal, id]);
  saveDatabase();

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
  db.run('UPDATE orders SET status = ?, updated_at = datetime("now","localtime") WHERE id = ?', [status, id]);
  saveDatabase();

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
  db.run('UPDATE order_items SET status = ? WHERE id = ?', [status, itemId]);
  saveDatabase();
  res.json({ success: true });
});

async function getOrderById(orderId: number) {
  const db = await getDatabase();
  const result = db.exec(
    `SELECT o.id, o.table_id, t.number as table_number, o.waiter_id, u.name as waiter_name,
     o.status, o.total, o.is_takeaway, o.created_at, o.updated_at
     FROM orders o
     JOIN tables t ON t.id = o.table_id
     JOIN users u ON u.id = o.waiter_id
     WHERE o.id = ?`,
    [orderId]
  );
  if (!result.length) return null;

  const row = result[0].values[0] as any[];
  const order: any = {
    id: row[0], table_id: row[1], table_number: row[2], waiter_id: row[3],
    waiter_name: row[4], status: row[5], total: row[6], is_takeaway: !!row[7],
    created_at: row[8], updated_at: row[9], items: []
  };

  const itemsResult = db.exec(
    `SELECT oi.id, oi.menu_item_id, mi.name, oi.quantity, oi.price, oi.notes, oi.status
     FROM order_items oi
     JOIN menu_items mi ON mi.id = oi.menu_item_id
     WHERE oi.order_id = ?
     ORDER BY oi.id`,
    [orderId]
  );
  order.items = itemsResult[0]?.values.map((row: any[]) => {
    const item: any = { id: row[0], menu_item_id: row[1], name: row[2],
      quantity: row[3], price: row[4], notes: row[5], status: row[6], addons: [] };
    return item;
  }) || [];

  for (const item of order.items) {
    const addonResult = db.exec(
      'SELECT id, name, price FROM order_addons WHERE order_item_id = ?', [item.id]
    );
    item.addons = addonResult[0]?.values.map((row: any[]) => ({
      id: row[0], name: row[1], price: row[2]
    })) || [];
  }

  return order;
}

export default router;

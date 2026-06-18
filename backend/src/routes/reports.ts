import { Router } from 'express';
import { getDatabase } from '../database';
import { authenticate, authorize } from '../middleware/auth';

const router = Router();

router.get('/:period', authenticate, authorize('owner'), async (req, res) => {
  const { period } = req.params;
  if (!['daily', 'weekly', 'monthly'].includes(period)) {
    return res.status(400).json({ error: 'Period must be daily, weekly, or monthly' });
  }

  const db = await getDatabase();
  let dateFilter: string;

  switch (period) {
    case 'daily':
      dateFilter = "datetime('now','localtime','start of day')";
      break;
    case 'weekly':
      dateFilter = "datetime('now','localtime','start of day','-6 days')";
      break;
    case 'monthly':
      dateFilter = "datetime('now','localtime','start of month')";
      break;
  }

  const salesResult = db.exec(`
    SELECT COALESCE(SUM(p.amount), 0) as total_sales,
           COUNT(DISTINCT p.order_id) as total_orders,
           COALESCE(AVG(p.amount), 0) as avg_order_value
    FROM payments p
    WHERE p.paid_at >= ${dateFilter}
  `);

  const byMethodResult = db.exec(`
    SELECT p.method, COALESCE(SUM(p.amount), 0) as total
    FROM payments p
    WHERE p.paid_at >= ${dateFilter}
    GROUP BY p.method
  `);

  const topItemsResult = db.exec(`
    SELECT mi.name, SUM(oi.quantity) as qty, SUM(oi.quantity * oi.price) as revenue
    FROM order_items oi
    JOIN menu_items mi ON mi.id = oi.menu_item_id
    JOIN orders o ON o.id = oi.order_id
    WHERE o.status = 'paid' AND o.updated_at >= ${dateFilter}
    GROUP BY mi.id, mi.name
    ORDER BY qty DESC
    LIMIT 10
  `);

  const byTableResult = db.exec(`
    SELECT t.number, COUNT(DISTINCT o.id) as orders, SUM(p.amount) as revenue
    FROM payments p
    JOIN orders o ON o.id = p.order_id
    JOIN tables t ON t.id = o.table_id
    WHERE p.paid_at >= ${dateFilter}
    GROUP BY t.id, t.number
    ORDER BY revenue DESC
  `);

  res.json({
    period,
    sales: salesResult[0]?.values[0] ? {
      total_sales: salesResult[0].values[0][0],
      total_orders: salesResult[0].values[0][1],
      avg_order_value: Math.round(Number(salesResult[0].values[0][2]) * 100) / 100
    } : { total_sales: 0, total_orders: 0, avg_order_value: 0 },
    by_payment_method: byMethodResult[0]?.values.map((r: any[]) => ({
      method: r[0], total: r[1]
    })) || [],
    top_items: topItemsResult[0]?.values.map((r: any[]) => ({
      name: r[0], quantity: r[1], revenue: r[2]
    })) || [],
    by_table: byTableResult[0]?.values.map((r: any[]) => ({
      table_number: r[0], orders: r[1], revenue: r[2]
    })) || []
  });
});

export default router;

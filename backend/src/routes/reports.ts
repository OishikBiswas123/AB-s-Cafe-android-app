import { Router } from 'express';
import { getDatabase } from '../database';
import { authenticate, authorize } from '../middleware/auth';

const router = Router();

router.get('/:period', authenticate, authorize('owner'), async (req, res) => {
  const period = req.params.period as string;
  if (!['daily', 'weekly', 'monthly'].includes(period)) {
    return res.status(400).json({ error: 'Period must be daily, weekly, or monthly' });
  }

  const db = await getDatabase();
  let dateFilter: string;

  switch (period) {
    case 'daily':
      dateFilter = "DATE_TRUNC('day', NOW())";
      break;
    case 'weekly':
      dateFilter = "DATE_TRUNC('day', NOW()) - INTERVAL '6 days'";
      break;
    case 'monthly':
      dateFilter = "DATE_TRUNC('month', NOW())";
      break;
    default:
      return res.status(400).json({ error: 'Invalid period' });
  }

  const salesResult = await db.query(`
    SELECT COALESCE(SUM(p.amount), 0) as total_sales,
           COUNT(DISTINCT p.order_id) as total_orders,
           COALESCE(AVG(p.amount), 0) as avg_order_value
    FROM payments p
    WHERE p.paid_at >= ${dateFilter}
  `);

  const byMethodResult = await db.query(`
    SELECT p.method, COALESCE(SUM(p.amount), 0) as total
    FROM payments p
    WHERE p.paid_at >= ${dateFilter}
    GROUP BY p.method
  `);

  const topItemsResult = await db.query(`
    SELECT mi.name, SUM(oi.quantity)::int as qty, SUM(oi.quantity * oi.price) as revenue
    FROM order_items oi
    JOIN menu_items mi ON mi.id = oi.menu_item_id
    JOIN orders o ON o.id = oi.order_id
    WHERE o.status = 'paid' AND o.updated_at >= ${dateFilter}
    GROUP BY mi.id, mi.name
    ORDER BY qty DESC
    LIMIT 10
  `);

  const byTableResult = await db.query(`
    SELECT t.number, COUNT(DISTINCT o.id)::int as orders, SUM(p.amount) as revenue
    FROM payments p
    JOIN orders o ON o.id = p.order_id
    JOIN tables t ON t.id = o.table_id
    WHERE p.paid_at >= ${dateFilter}
    GROUP BY t.id, t.number
    ORDER BY revenue DESC
  `);

  const sales = salesResult.rows[0] || { total_sales: 0, total_orders: 0, avg_order_value: 0 };

  res.json({
    period,
    sales: {
      total_sales: Number(sales.total_sales),
      total_orders: Number(sales.total_orders),
      avg_order_value: Math.round(Number(sales.avg_order_value) * 100) / 100
    },
    by_payment_method: byMethodResult.rows.map((r: any) => ({
      method: r.method, total: Number(r.total)
    })),
    top_items: topItemsResult.rows.map((r: any) => ({
      name: r.name, quantity: r.qty, revenue: Number(r.revenue)
    })),
    by_table: byTableResult.rows.map((r: any) => ({
      table_number: r.number, orders: r.orders, revenue: Number(r.revenue)
    }))
  });
});

export default router;

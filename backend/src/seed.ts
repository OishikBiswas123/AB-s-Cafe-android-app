import bcrypt from 'bcryptjs';
import { getDatabase } from './database';

export async function seedDatabase() {
  const db = await getDatabase();

  const existingUsers = await db.query("SELECT COUNT(*) as count FROM users");
  if (parseInt(existingUsers.rows[0].count) > 0) {
    console.log('Database already seeded. Skipping.');
    return;
  }

  const ownerHash = await bcrypt.hash('admin123', 10);
  const staffHash = await bcrypt.hash('staff123', 10);

  await db.query(`INSERT INTO users (name, email, password_hash, role) VALUES
    ('Owner', 'owner@abscafe.com', $1, 'owner'),
    ('Waiter', 'waiter@abscafe.com', $2, 'waiter'),
    ('Chef', 'chef@abscafe.com', $3, 'chef'),
    ('Cashier', 'cashier@abscafe.com', $4, 'cashier'),
    ('Drinks Chef', 'drinks@abscafe.com', $5, 'drinks_chef')
  `, [ownerHash, staffHash, staffHash, staffHash, staffHash]);

  for (let i = 1; i <= 18; i++) {
    await db.query('INSERT INTO tables (number, status) VALUES ($1, $2)', [i, 'available']);
  }

  await db.query(`INSERT INTO categories (name, sort_order) VALUES
    ('Tea & Coffee', 1),
    ('Snacks', 2),
    ('Maggi & Noodles', 3),
    ('Sandwiches', 4),
    ('Burgers', 5),
    ('Special Momos', 6),
    ('Cold Drinks', 7)
  `);

  await db.query(`INSERT INTO menu_items (name, price, category_id, type) VALUES
    ('Black Tea', 10, 1, 'beverage'),
    ('Lemon Tea', 15, 1, 'beverage'),
    ('Milk Tea (Small)', 10, 1, 'beverage'),
    ('Milk Tea (Large)', 20, 1, 'beverage'),
    ('Black Coffee', 20, 1, 'beverage'),
    ('Milk Coffee', 30, 1, 'beverage'),

    ('French Fries (Plain)', 60, 2, 'food'),
    ('French Fries (Peri Peri)', 70, 2, 'food'),
    ('Honey Chilli Potato', 99, 2, 'food'),
    ('Veg Pakora', 70, 2, 'food'),
    ('Paneer Pakora (6 Pcs)', 80, 2, 'food'),
    ('Chicken Pakora', 80, 2, 'food'),
    ('Chicken Popcorn', 70, 2, 'food'),
    ('Chicken Nuggets (5 Pcs)', 80, 2, 'food'),
    ('Crispy Chicken Fry', 99, 2, 'food'),
    ('Dragon Chicken', 99, 2, 'food'),
    ('Chicken Lollipop (4 Pcs)', 99, 2, 'food'),
    ('Dry Chilli Chicken', 99, 2, 'food'),
    ('Fish Fry (2 Pcs)', 130, 2, 'food'),
    ('Fish Finger (4 Pcs)', 130, 2, 'food'),

    ('Veg Maggi', 35, 3, 'food'),
    ('Egg Maggi', 45, 3, 'food'),
    ('Chicken N Cheese Maggi', 60, 3, 'food'),
    ('Veg Hakka Noodles', 60, 3, 'food'),
    ('Egg Hakka Noodles', 70, 3, 'food'),
    ('Chicken Hakka Noodles', 80, 3, 'food'),
    ('Egg Chicken Hakka Noodles', 90, 3, 'food'),

    ('Veg Sandwich', 70, 4, 'food'),
    ('Cheese Corn Sandwich', 80, 4, 'food'),
    ('Chicken Cheese Sandwich', 99, 4, 'food'),

    ('Veg Cheese Burger', 80, 5, 'food'),
    ('Crispy Chicken Cheese Burger', 99, 5, 'food'),

    ('Chicken Steam (Darjeeling Style)', 70, 6, 'food'),
    ('Chicken Fried', 80, 6, 'food'),
    ('Chicken Cheese Steam', 90, 6, 'food'),
    ('Chicken Pan Fried', 110, 6, 'food'),

    ('Cold Coffee', 70, 7, 'beverage'),
    ('Virgin Mojito', 70, 7, 'beverage'),
    ('Orange Punch', 70, 7, 'beverage'),
    ('Masala Cold Drink', 50, 7, 'beverage'),
    ('Blue Lagoon', 70, 7, 'beverage'),
    ('Pineapple Mojito', 70, 7, 'beverage')
  `);

  console.log('Database seeded successfully!');
  console.log('Login credentials:');
  console.log('  Owner:       owner@abscafe.com / admin123');
  console.log('  Waiter:      waiter@abscafe.com / staff123');
  console.log('  Chef:        chef@abscafe.com / staff123');
  console.log('  Cashier:     cashier@abscafe.com / staff123');
  console.log('  Drinks Chef: drinks@abscafe.com / staff123');
}

// Self-execute if run directly (npx ts-node src/seed.ts)
if (require.main === module) {
  seedDatabase().catch(console.error);
}

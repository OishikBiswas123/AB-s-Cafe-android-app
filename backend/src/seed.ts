import bcrypt from 'bcryptjs';
import { getDatabase } from './database';

async function seed() {
  const db = await getDatabase();

  const existingUsers = await db.query("SELECT COUNT(*) as count FROM users");
  if (parseInt(existingUsers.rows[0].count) > 0) {
    console.log('Database already seeded. Skipping.');
    return;
  }

  const ownerHash = await bcrypt.hash('admin123', 10);
  const staffHash = await bcrypt.hash('staff123', 10);

  await db.query(`INSERT INTO users (name, email, password_hash, role) VALUES
    ($1, 'owner@abscafe.com', $2, 'owner'),
    ($3, 'waiter@abscafe.com', $4, 'waiter'),
    ($5, 'chef@abscafe.com', $6, 'chef'),
    ($7, 'cashier@abscafe.com', $8, 'cashier')
  `, [ownerHash, ownerHash, staffHash, staffHash, staffHash, staffHash, staffHash, staffHash]);

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

  await db.query(`INSERT INTO menu_items (name, price, category_id) VALUES
    ('Black Tea', 10, 1),
    ('Lemon Tea', 15, 1),
    ('Milk Tea (Small)', 10, 1),
    ('Milk Tea (Large)', 20, 1),
    ('Black Coffee', 20, 1),
    ('Milk Coffee', 30, 1),

    ('French Fries (Plain)', 60, 2),
    ('French Fries (Peri Peri)', 70, 2),
    ('Honey Chilli Potato', 99, 2),
    ('Veg Pakora', 70, 2),
    ('Paneer Pakora (6 Pcs)', 80, 2),
    ('Chicken Pakora', 80, 2),
    ('Chicken Popcorn', 70, 2),
    ('Chicken Nuggets (5 Pcs)', 80, 2),
    ('Crispy Chicken Fry', 99, 2),
    ('Dragon Chicken', 99, 2),
    ('Chicken Lollipop (4 Pcs)', 99, 2),
    ('Dry Chilli Chicken', 99, 2),
    ('Fish Fry (2 Pcs)', 130, 2),
    ('Fish Finger (4 Pcs)', 130, 2),

    ('Veg Maggi', 35, 3),
    ('Egg Maggi', 45, 3),
    ('Chicken N Cheese Maggi', 60, 3),
    ('Veg Hakka Noodles', 60, 3),
    ('Egg Hakka Noodles', 70, 3),
    ('Chicken Hakka Noodles', 80, 3),
    ('Egg Chicken Hakka Noodles', 90, 3),

    ('Veg Sandwich', 70, 4),
    ('Cheese Corn Sandwich', 80, 4),
    ('Chicken Cheese Sandwich', 99, 4),

    ('Veg Cheese Burger', 80, 5),
    ('Crispy Chicken Cheese Burger', 99, 5),

    ('Chicken Steam (Darjeeling Style)', 70, 6),
    ('Chicken Fried', 80, 6),
    ('Chicken Cheese Steam', 90, 6),
    ('Chicken Pan Fried', 110, 6),

    ('Cold Coffee', 70, 7),
    ('Virgin Mojito', 70, 7),
    ('Orange Punch', 70, 7),
    ('Masala Cold Drink', 50, 7),
    ('Blue Lagoon', 70, 7),
    ('Pineapple Mojito', 70, 7)
  `);

  console.log('Database seeded successfully!');
  console.log('Login credentials:');
  console.log('  Owner:   owner@abscafe.com / admin123');
  console.log('  Waiter:  waiter@abscafe.com / staff123');
  console.log('  Chef:    chef@abscafe.com / staff123');
  console.log('  Cashier: cashier@abscafe.com / staff123');
}

seed().catch(console.error);

import { Pool } from 'pg';
import bcrypt from 'bcryptjs';

async function recoverUsers() {
  const pool = new Pool({ connectionString: process.env.DATABASE_URL });

  const existing = await pool.query("SELECT COUNT(*) as count FROM users WHERE email IN ('owner@abscafe.com','waiter@abscafe.com','chef@abscafe.com','cashier@abscafe.com')");
  if (parseInt(existing.rows[0].count) === 4) {
    console.log('All 4 default users already exist. Skipping.');
    await pool.end();
    return;
  }

  const ownerHash = await bcrypt.hash('admin123', 10);
  const staffHash = await bcrypt.hash('staff123', 10);

  await pool.query(`INSERT INTO users (name, email, password_hash, role) VALUES
    ('Owner', 'owner@abscafe.com', $1, 'owner'),
    ('Waiter', 'waiter@abscafe.com', $2, 'waiter'),
    ('Chef', 'chef@abscafe.com', $3, 'chef'),
    ('Cashier', 'cashier@abscafe.com', $4, 'cashier')
  ON CONFLICT (email) DO NOTHING`, [ownerHash, staffHash, staffHash, staffHash]);

  console.log('Default users restored successfully!');
  console.log('  Owner:   owner@abscafe.com / admin123');
  console.log('  Waiter:  waiter@abscafe.com / staff123');
  console.log('  Chef:    chef@abscafe.com / staff123');
  console.log('  Cashier: cashier@abscafe.com / staff123');

  await pool.end();
}

recoverUsers().catch(console.error);

import { Pool, QueryResult } from 'pg';

const pool = new Pool({
  connectionString: process.env.DATABASE_URL || 'postgresql://postgres:postgres@localhost:5432/abs_cafe',
});

let schemaInitialized = false;

export async function getDatabase(): Promise<Pool> {
  if (!schemaInitialized) {
    await initializeSchema();
    schemaInitialized = true;
  }
  return pool;
}

async function initializeSchema() {
  await pool.query(`
    CREATE TABLE IF NOT EXISTS users (
      id SERIAL PRIMARY KEY,
      name TEXT NOT NULL,
      email TEXT UNIQUE NOT NULL,
      password_hash TEXT NOT NULL,
      role TEXT NOT NULL CHECK(role IN ('owner','waiter','chef','cashier')),
      created_at TIMESTAMPTZ DEFAULT NOW()
    )
  `);

  await pool.query(`
    CREATE TABLE IF NOT EXISTS tables (
      id SERIAL PRIMARY KEY,
      number INTEGER UNIQUE NOT NULL,
      status TEXT DEFAULT 'available' CHECK(status IN ('available','occupied'))
    )
  `);

  await pool.query(`
    CREATE TABLE IF NOT EXISTS categories (
      id SERIAL PRIMARY KEY,
      name TEXT NOT NULL,
      sort_order INTEGER DEFAULT 0
    )
  `);

  await pool.query(`
    CREATE TABLE IF NOT EXISTS menu_items (
      id SERIAL PRIMARY KEY,
      name TEXT NOT NULL,
      description TEXT DEFAULT '',
      price DOUBLE PRECISION NOT NULL,
      category_id INTEGER NOT NULL REFERENCES categories(id),
      available BOOLEAN DEFAULT TRUE
    )
  `);

  await pool.query(`
    CREATE TABLE IF NOT EXISTS orders (
      id SERIAL PRIMARY KEY,
      table_id INTEGER NOT NULL REFERENCES tables(id),
      waiter_id INTEGER NOT NULL REFERENCES users(id),
      status TEXT DEFAULT 'pending' CHECK(status IN ('pending','preparing','ready','served','paid')),
      total DOUBLE PRECISION DEFAULT 0,
      is_takeaway BOOLEAN DEFAULT FALSE,
      created_at TIMESTAMPTZ DEFAULT NOW(),
      updated_at TIMESTAMPTZ DEFAULT NOW()
    )
  `);

  await pool.query(`
    CREATE TABLE IF NOT EXISTS order_items (
      id SERIAL PRIMARY KEY,
      order_id INTEGER NOT NULL REFERENCES orders(id),
      menu_item_id INTEGER NOT NULL REFERENCES menu_items(id),
      quantity INTEGER NOT NULL DEFAULT 1,
      price DOUBLE PRECISION NOT NULL,
      notes TEXT DEFAULT '',
      status TEXT DEFAULT 'pending' CHECK(status IN ('pending','preparing','ready','served'))
    )
  `);

  await pool.query(`
    CREATE TABLE IF NOT EXISTS order_addons (
      id SERIAL PRIMARY KEY,
      order_item_id INTEGER NOT NULL REFERENCES order_items(id),
      name TEXT NOT NULL,
      price DOUBLE PRECISION NOT NULL DEFAULT 0
    )
  `);

  await pool.query(`
    CREATE TABLE IF NOT EXISTS payments (
      id SERIAL PRIMARY KEY,
      order_id INTEGER UNIQUE NOT NULL REFERENCES orders(id),
      amount DOUBLE PRECISION NOT NULL,
      method TEXT CHECK(method IN ('cash','qr')),
      paid_at TIMESTAMPTZ DEFAULT NOW(),
      cashier_id INTEGER NOT NULL REFERENCES users(id)
    )
  `);
}

export function saveDatabase() {}

export async function closeDatabase() {
  await pool.end();
}

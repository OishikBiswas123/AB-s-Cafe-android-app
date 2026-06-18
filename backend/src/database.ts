import initSqlJs, { Database as SqlJsDatabase } from 'sql.js';
import fs from 'fs';
import path from 'path';

const DB_PATH = path.join(__dirname, '..', 'data', 'abs-cafe.db');

let db: SqlJsDatabase;

export async function getDatabase(): Promise<SqlJsDatabase> {
  if (db) return db;

  const SQL = await initSqlJs();
  const dir = path.dirname(DB_PATH);
  if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });

  if (fs.existsSync(DB_PATH)) {
    const buffer = fs.readFileSync(DB_PATH);
    db = new SQL.Database(buffer);
  } else {
    db = new SQL.Database();
  }

  db.run('PRAGMA journal_mode=WAL');
  db.run('PRAGMA foreign_keys=ON');

  initializeSchema();
  saveDatabase();
  return db;
}

function initializeSchema() {
  db.run(`
    CREATE TABLE IF NOT EXISTS users (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      name TEXT NOT NULL,
      email TEXT UNIQUE NOT NULL,
      password_hash TEXT NOT NULL,
      role TEXT NOT NULL CHECK(role IN ('owner','waiter','chef','cashier')),
      created_at TEXT DEFAULT (datetime('now','localtime'))
    )
  `);

  db.run(`
    CREATE TABLE IF NOT EXISTS tables (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      number INTEGER UNIQUE NOT NULL,
      status TEXT DEFAULT 'available' CHECK(status IN ('available','occupied'))
    )
  `);

  db.run(`
    CREATE TABLE IF NOT EXISTS categories (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      name TEXT NOT NULL,
      sort_order INTEGER DEFAULT 0
    )
  `);

  db.run(`
    CREATE TABLE IF NOT EXISTS menu_items (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      name TEXT NOT NULL,
      description TEXT DEFAULT '',
      price REAL NOT NULL,
      category_id INTEGER NOT NULL,
      available INTEGER DEFAULT 1,
      FOREIGN KEY (category_id) REFERENCES categories(id)
    )
  `);

  db.run(`
    CREATE TABLE IF NOT EXISTS orders (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      table_id INTEGER NOT NULL,
      waiter_id INTEGER NOT NULL,
      status TEXT DEFAULT 'pending' CHECK(status IN ('pending','preparing','ready','served','paid')),
      total REAL DEFAULT 0,
      is_takeaway INTEGER DEFAULT 0,
      created_at TEXT DEFAULT (datetime('now','localtime')),
      updated_at TEXT DEFAULT (datetime('now','localtime')),
      FOREIGN KEY (table_id) REFERENCES tables(id),
      FOREIGN KEY (waiter_id) REFERENCES users(id)
    )
  `);

  db.run(`
    CREATE TABLE IF NOT EXISTS order_items (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      order_id INTEGER NOT NULL,
      menu_item_id INTEGER NOT NULL,
      quantity INTEGER NOT NULL DEFAULT 1,
      price REAL NOT NULL,
      notes TEXT DEFAULT '',
      status TEXT DEFAULT 'pending' CHECK(status IN ('pending','preparing','ready','served')),
      FOREIGN KEY (order_id) REFERENCES orders(id),
      FOREIGN KEY (menu_item_id) REFERENCES menu_items(id)
    )
  `);

  db.run(`
    CREATE TABLE IF NOT EXISTS order_addons (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      order_item_id INTEGER NOT NULL,
      name TEXT NOT NULL,
      price REAL NOT NULL DEFAULT 0,
      FOREIGN KEY (order_item_id) REFERENCES order_items(id)
    )
  `);

  db.run(`
    CREATE TABLE IF NOT EXISTS payments (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      order_id INTEGER UNIQUE NOT NULL,
      amount REAL NOT NULL,
      method TEXT CHECK(method IN ('cash','qr')),
      paid_at TEXT DEFAULT (datetime('now','localtime')),
      cashier_id INTEGER NOT NULL,
      FOREIGN KEY (order_id) REFERENCES orders(id),
      FOREIGN KEY (cashier_id) REFERENCES users(id)
    )
  `);
}

export function saveDatabase() {
  const data = db.export();
  const buffer = Buffer.from(data);
  fs.writeFileSync(DB_PATH, buffer);
}

export function closeDatabase() {
  if (db) db.close();
}

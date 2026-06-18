import { Router } from 'express';
import bcrypt from 'bcryptjs';
import { getDatabase } from '../database';
import { generateToken } from '../middleware/auth';

const router = Router();

router.post('/login', async (req, res) => {
  try {
    const { email, password } = req.body;
    if (!email || !password) {
      return res.status(400).json({ error: 'Email and password required' });
    }

    const db = await getDatabase();
    const result = db.exec(
      'SELECT id, name, email, password_hash, role FROM users WHERE email = ?',
      [email]
    );

    if (!result.length || !result[0].values.length) {
      return res.status(401).json({ error: 'Invalid credentials' });
    }

    const [id, name, userEmail, passwordHash, role] = result[0].values[0];
    const valid = await bcrypt.compare(password, passwordHash as string);
    if (!valid) {
      return res.status(401).json({ error: 'Invalid credentials' });
    }

    const user = { id: id as number, name: name as string, email: userEmail as string, role: role as any };
    const token = generateToken(user);

    res.json({ token, user });
  } catch (error) {
    res.status(500).json({ error: 'Login failed' });
  }
});

export default router;

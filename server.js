require('dotenv').config();
const path = require('path');
const express = require('express');
const cors = require('cors');

const { pool } = require('./js/config/db');
const authRoutes = require('./js/routes/authRoutes');
const adminRoutes = require('./js/routes/adminRoutes');
const soporteRoutes = require('./js/routes/soporteRoutes');
const suscripcionesRoutes = require('./js/routes/suscripciones');
const favoritosRoutes = require('./js/routes/favoritosRoutes');
const catalogosRoutes = require('./js/routes/catalogosRoutes');
const usuariosRoutes = require('./js/routes/usuariosRoutes');
const contactoRoutes = require('./js/routes/contactoRoutes');
const ubicacionRoutes = require('./js/routes/ubicacionRoutes');
const { noEncontrado, manejadorErrores } = require('./js/middlewares/errores');
const { iniciarJobs } = require('./js/jobs');

const app = express(); 
app.disable('x-powered-by');

// CORS: la app movil no lo necesita; el panel web si cuando vive en otro dominio (Vercel)
const origenes = (process.env.CORS_ORIGIN || '').split(',').map((o) => o.trim()).filter(Boolean);
app.use(cors({ origin: origenes.length ? origenes : true }));
app.use(express.json({ limit: '1mb' }));

// ---------------------------------------------------------------- API
app.get('/api/salud', async (req, res) => {
  try {
    await pool.query('SELECT 1');
    res.json({ estado: 'ok' });
  } catch (_) {
    res.status(503).json({ estado: 'sin base de datos' });
  }
});
app.use('/api/auth', authRoutes);
app.use('/api/admin', adminRoutes);
app.use('/api/soporte', soporteRoutes);
app.use('/api/suscripciones', suscripcionesRoutes);
app.use('/api/favoritos', favoritosRoutes);
app.use('/api/catalogos', catalogosRoutes);
app.use('/api/usuarios', usuariosRoutes);
app.use('/api/contacto', contactoRoutes);
app.use('/api/ubicacion', ubicacionRoutes);
app.use('/api', noEncontrado);

app.use('/css', express.static(path.join(__dirname, 'css')));
app.use('/html', express.static(path.join(__dirname, 'html')));
app.get('/js/script.js', (req, res) => res.sendFile(path.join(__dirname, 'js', 'script.js')));
app.get('/', (req, res) => res.sendFile(path.join(__dirname, 'index.html')));
app.get('/terminos_condiciones.html', (req, res) => res.sendFile(path.join(__dirname, 'terminos_condiciones.html')));

app.use(manejadorErrores);

const PORT = Number(process.env.PORT || 3000);
app.listen(PORT, () => {
  console.log(`Tamalon escuchando en el puerto ${PORT}`);
  iniciarJobs();
});

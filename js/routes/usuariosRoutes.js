const router = require('express').Router();
const usuario = require('../Controladores/usuarioController');
const { verificarSesion } = require('../middlewares/autenticacion');

// Todas estas rutas requieren sesión activa
router.use(verificarSesion);

router.get('/perfil', usuario.obtenerPerfil);            // Datos del usuario logueado
router.put('/perfil', usuario.actualizarPerfil);         // Nombre, teléfono
router.put('/foto', usuario.actualizarFotoPerfil);       // Subir o cambiar foto de perfil

module.exports = router;
const router = require('express').Router();
const auth = require('../Controladores/authController');
const { verificarSesion } = require('../middlewares/autenticacion');

router.post('/registro/vendedor', auth.registrarVendedor);   // RF1.1
router.post('/registro/cliente', auth.registrarCliente);     // RF1.2
router.post('/login', auth.login);                           // RF2.1
router.post('/logout', verificarSesion, auth.logout);
router.post('/recuperar', auth.solicitarRecuperacion);       // RF3.1
router.post('/restablecer', auth.restablecerContrasena);     // RF3.2

module.exports = router;

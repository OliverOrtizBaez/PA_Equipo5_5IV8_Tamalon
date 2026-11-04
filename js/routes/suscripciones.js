const router = require('express').Router();
const suscripcion = require('../Controladores/suscripcionController');
const { verificarSesion } = require('../middlewares/autenticacion');

router.use(verificarSesion);

router.get('/mi-plan', suscripcion.obtenerSuscripcion);         // Consultar estado de suscripción
router.post('/procesar-pago', suscripcion.procesarPagoPremium);  // Confirmar pago y subir a Premium

module.exports = router;
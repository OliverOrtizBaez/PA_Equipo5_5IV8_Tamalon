const router = require('express').Router();
const suscripcion = require('../Controladores/suscripcionController');
const { verificarSesion } = require('../middlewares/autenticacion');

router.use(verificarSesion);

router.get('/mia', suscripcion.obtenerSuscripcion);
router.post('/pagar', suscripcion.procesarPagoPremium);

module.exports = router;
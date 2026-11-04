const router = require('express').Router();
const soporte = require('../Controladores/soporteController');
const { verificarSesion } = require('../middlewares/autenticacion');

router.use(verificarSesion);

router.post('/reportes', soporte.crearReporte);        // Reportar un vendedor/usuario
router.get('/reportes', soporte.obtenerMisReportes);
router.post('/tickets', soporte.crearSolicitudSoporte); // Ticket de ayuda
router.get('/tickets', soporte.obtenerMisTickets);

module.exports = router;
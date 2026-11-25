const router = require('express').Router();
const estadisticas = require('../Controladores/estadisticasController');
const { verificarSesion, verificarTipo } = require('../middlewares/autenticacion');
const { TIPOS } = require('../config/constantes');

router.get('/vendedor', verificarSesion, verificarTipo(TIPOS.VENDEDOR), estadisticas.obtenerEstadisticasVendedor);

module.exports = router;

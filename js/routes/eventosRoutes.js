const router = require('express').Router();
const eventos = require('../Controladores/eventosController');
const { verificarSesionOpcional } = require('../middlewares/autenticacion');

router.post('/', verificarSesionOpcional, eventos.registrarEvento);

module.exports = router;

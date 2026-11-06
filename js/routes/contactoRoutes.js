const router = require('express').Router();
const contacto = require('../Controladores/contactoController');

router.post('/', contacto.crearMensaje);

module.exports = router;

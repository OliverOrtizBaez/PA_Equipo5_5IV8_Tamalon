const router = require('express').Router();
const tipos = require('../Controladores/tiposVendedorController');

router.get('/', tipos.listarTipos);
router.get('/:id/categorias', tipos.listarCategorias);

module.exports = router;

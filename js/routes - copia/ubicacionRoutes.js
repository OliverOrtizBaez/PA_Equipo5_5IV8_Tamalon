const router = require('express').Router();
const ubicacion = require('../Controladores/ubicacionController');
const { verificarSesion, verificarTipo } = require('../middlewares/autenticacion');
const { TIPOS } = require('../config/constantes');

const soloVendedores = verificarTipo(TIPOS.VENDEDOR);

router.use(verificarSesion);

router.put('/mia', soloVendedores, ubicacion.actualizarUbicacion);
router.put('/mia/fuera-de-ruta', soloVendedores, ubicacion.fueraDeRuta);
router.get('/cercanos', ubicacion.obtenerCercanos);
router.get('/vendedores/:id', ubicacion.obtenerUbicacionVendedor);

module.exports = router;

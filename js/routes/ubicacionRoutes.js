const router = require('express').Router();
const ubicacion = require('../Controladores/ubicacionController');
const { verificarSesion, verificarRol } = require('../middlewares/autenticacion');
const { ROLES } = require('../config/constantes');

const soloVendedores = verificarRol(ROLES.VENDEDOR_GRATUITO, ROLES.VENDEDOR_PREMIUM);

router.use(verificarSesion);

router.put('/mia', soloVendedores, ubicacion.actualizarUbicacion);
router.put('/mia/fuera-de-ruta', soloVendedores, ubicacion.fueraDeRuta);
router.get('/cercanos', ubicacion.obtenerCercanos);
router.get('/vendedores/:id', ubicacion.obtenerUbicacionVendedor);

module.exports = router;

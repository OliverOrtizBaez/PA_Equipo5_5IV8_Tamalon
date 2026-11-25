const router = require('express').Router();
const ubicacion = require('../Controladores/ubicacionController');
const { verificarSesion, verificarTipo } = require('../middlewares/autenticacion');
const { TIPOS } = require('../config/constantes');

const soloVendedores = verificarTipo(TIPOS.VENDEDOR);

router.post('/jornada/iniciar', verificarSesion, soloVendedores, ubicacion.iniciarJornada);
router.post('/jornada/finalizar', verificarSesion, soloVendedores, ubicacion.finalizarJornada);
router.put('/mia', verificarSesion, soloVendedores, ubicacion.actualizarUbicacion);
router.get('/cercanos', ubicacion.obtenerCercanos);
router.get('/vendedores/:id', ubicacion.obtenerUbicacionVendedor);

module.exports = router;

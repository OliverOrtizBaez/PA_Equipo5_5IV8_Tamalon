const router = require('express').Router();
const admin = require('../Controladores/adminController');
const { verificarSesion, verificarRol } = require('../middlewares/autenticacion');
const { ROLES } = require('../config/constantes');

// Todo /api/admin exige sesion valida y rol Administrador
router.use(verificarSesion, verificarRol(ROLES.ADMIN));

router.post('/nuevo', admin.crearAdministrador);                          // RF1.3
router.get('/suscripciones', admin.consultarSuscripciones);               // RF16.1
router.get('/trafico', admin.consultarTrafico);                           // RF20b
router.get('/ingresos', admin.consultarIngresos);                         // RF18
router.get('/ingresos/exportar', admin.exportarIngresos);                 // RF19a / RF19b
router.get('/usuarios', admin.buscarUsuarios);                            // RF21
router.put('/usuarios/:id/rol', admin.cambiarRol);                        // RF17
router.put('/usuarios/:id/suspender', admin.suspenderIndefinidamente);    // RF22.1
router.put('/usuarios/:id/suspender-temporal', admin.suspenderTemporalmente); // RF22.2a
router.put('/usuarios/:id/reactivar', admin.reactivarCuenta);             // RF22.3
router.get('/reportes', admin.consultarReportes);                         // RF24

module.exports = router;

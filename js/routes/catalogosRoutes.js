const router = require('express').Router();
const catalogo = require('../Controladores/catalogoController');
const producto = require('../Controladores/productoController');
const { verificarSesion } = require('../middlewares/autenticacion');

// Rutas de Catálogos
router.get('/vendedor/:vendedorId', catalogo.obtenerPorVendedor); // Catálogos públicos de un vendedor
router.post('/', verificarSesion, catalogo.crearCatalogo);         // Validar límite (Gratuito: 1, Premium: 3)
router.put('/:id', verificarSesion, catalogo.actualizarCatalogo);
router.delete('/:id', verificarSesion, catalogo.eliminarCatalogo);

// Rutas de Productos
router.get('/:catalogoId/productos', producto.obtenerPorCatalogo);
router.post('/:catalogoId/productos', verificarSesion, producto.crearProducto); // Validar límite (Gratuito: 10, Premium: 50)
router.put('/productos/:id', verificarSesion, producto.actualizarProducto);
router.delete('/productos/:id', verificarSesion, producto.eliminarProducto);

module.exports = router;
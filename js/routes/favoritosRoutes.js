const router = require('express').Router();
const favorito = require('../Controladores/favoritoController');
const { verificarSesion } = require('../middlewares/autenticacion');

router.use(verificarSesion);

router.get('/', favorito.obtenerFavoritosCliente);               // Mis vendedores favoritos
router.get('/check/:vendedorId', favorito.verificarEsFavorito);   // Saber si un vendedor ya es favorito
router.post('/:vendedorId', favorito.agregarFavorito);            // Agregar vendedor
router.delete('/:vendedorId', favorito.eliminarFavorito);         // Eliminar de favoritos

module.exports = router;
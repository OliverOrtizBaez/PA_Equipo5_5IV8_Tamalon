const nodemailer = require('nodemailer');

let transporte = null;
function obtenerTransporte() {
  if (transporte) return transporte;
  if (!process.env.GMAIL_USER || !process.env.GMAIL_APP_PASSWORD) return null;
  transporte = nodemailer.createTransport({
    service: 'gmail',
    auth: { user: process.env.GMAIL_USER, pass: process.env.GMAIL_APP_PASSWORD },
  });
  return transporte;
}

/**
 * Envia el enlace de recuperacion (RF3.1). Sin credenciales de Gmail y fuera de produccion,
 * imprime el enlace en consola para poder probar el flujo sin correo real.
 */
async function enviarRecuperacion(destino, enlace) {
  const t = obtenerTransporte();
  if (!t) {
    if (process.env.NODE_ENV === 'production') throw new Error('Gmail no configurado');
    console.log(`[correo:simulado] Enlace de recuperación para ${destino}: ${enlace}`);
    return;
  }
  await t.sendMail({
    from: `"Tamalon" <${process.env.GMAIL_USER}>`,
    to: destino,
    subject: 'Restablece tu contraseña de Tamalon',
    text:
      `Recibimos una solicitud para restablecer tu contraseña.\n\n` +
      `Abre este enlace (válido 30 minutos y de un solo uso):\n${enlace}\n\n` +
      `Si no fuiste tú, ignora este mensaje.`,
    html:
      `<p>Recibimos una solicitud para restablecer tu contraseña.</p>` +
      `<p><a href="${enlace}">Restablecer contraseña</a></p>` +
      `<p>El enlace es válido 30 minutos y de un solo uso. Si no fuiste tú, ignora este mensaje.</p>`,
  });
}

module.exports = { enviarRecuperacion };

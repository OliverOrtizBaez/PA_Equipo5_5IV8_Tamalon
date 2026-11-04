// Envio del correo de recuperacion (RF3.1) con EmailJS, llamando a su API REST desde el servidor.
// No necesita ninguna libreria extra: usa el fetch que ya trae Node.
const ENDPOINT = 'https://api.emailjs.com/api/v1.0/email/send';

/**
 * Envia el enlace de recuperacion. Las variables que recibe la plantilla de EmailJS son:
 *   {{to_email}}, {{enlace}} y {{minutos_vigencia}}
 * Sin credenciales y fuera de produccion, imprime el enlace en consola para poder probar el flujo.
 */
async function enviarRecuperacion(destino, enlace) {
  const {
    EMAILJS_SERVICE_ID: serviceId,
    EMAILJS_TEMPLATE_ID: templateId,
    EMAILJS_PUBLIC_KEY: publicKey,
    EMAILJS_PRIVATE_KEY: privateKey,
  } = process.env;

  if (!serviceId || !templateId || !publicKey || !privateKey) {
    if (process.env.NODE_ENV === 'production') throw new Error('EmailJS no está configurado');
    console.log(`[correo:simulado] Enlace de recuperación para ${destino}: ${enlace}`);
    return;
  }

  const resp = await fetch(ENDPOINT, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    signal: AbortSignal.timeout(15000),
    body: JSON.stringify({
      service_id: serviceId,
      template_id: templateId,
      user_id: publicKey,        // Public Key
      accessToken: privateKey,   // Private Key (solo servidor)
      template_params: { to_email: destino, enlace, minutos_vigencia: 30 },
    }),
  });

  if (!resp.ok) {
    // EmailJS explica el motivo en el cuerpo (p. ej. "API calls are disabled for non-browser applications")
    throw new Error(`EmailJS respondió ${resp.status}: ${await resp.text()}`);
  }
}

module.exports = { enviarRecuperacion };
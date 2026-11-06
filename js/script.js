// Pill filter
function setPill(el) {
    document.querySelectorAll('.pill').forEach(p => p.classList.remove('active'));
    el.classList.add('active');
}

// Contact form
async function submitForm(e) {
    e.preventDefault();
    const form = e.currentTarget;
    const button = form.querySelector('button[type="submit"]');
    const error = document.getElementById('contact-error');
    const data = Object.fromEntries(new FormData(form).entries());

    error.hidden = true;
    error.textContent = '';
    button.disabled = true;

    try {
        const response = await fetch('/api/contacto', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data),
        });
        const result = await response.json().catch(() => ({}));

        if (!response.ok) {
            const detail = Array.isArray(result.errores) ? result.errores.join('. ') : result.mensaje;
            throw new Error(detail || 'No se pudo enviar el mensaje. Intenta de nuevo.');
        }

        form.reset();
        form.style.display = 'none';
        document.getElementById('success-msg').style.display = 'flex';
    } catch (requestError) {
        error.textContent = requestError.message || 'No se pudo conectar con el servidor. Intenta de nuevo.';
        error.hidden = false;
    } finally {
        button.disabled = false;
    }
}

// Navbar shadow on scroll
window.addEventListener('scroll', function() {
    var nav = document.getElementById('navbar');
    if (window.scrollY > 10) {
        nav.style.boxShadow = '0 2px 16px rgba(61,43,31,.08)';
    } else {
        nav.style.boxShadow = 'none';
    }
});
// Pill filter
function setPill(el) {
    document.querySelectorAll('.pill').forEach(p => p.classList.remove('active'));
    el.classList.add('active');
}

// Contact form jdbjdfh
function submitForm(e) {
    e.preventDefault();
    document.getElementById('contact-form').style.display = 'none';
    var msg = document.getElementById('success-msg');
    msg.style.display = 'flex';
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
// ── Datos precargados de los vendedores ──
const VENDORS = [
    { name: 'Don Memo', spec: 'Tamales verdes y rojos', dist: 150, time: '6:00 – 10:00 am', rating: 4.9, count: 48, active: true, x: 38, y: 42 },
    { name: 'La Señora Lupita', spec: 'Tamales oaxaqueños', dist: 320, time: '7:00 – 11:00 am', rating: 4.8, count: 33, active: true, x: 62, y: 28 },
    { name: 'El Tamalero', spec: 'Tamales de rajas y elote', dist: 500, time: '6:30 – 9:30 am', rating: 4.7, count: 27, active: false, x: 55, y: 60 },
    { name: 'Doña Carmen', spec: 'Tamales dulces y mole', dist: 680, time: '7:30 – 10:30 am', rating: 4.6, count: 21, active: false, x: 24, y: 64 },
];

const FILTERS = {
    todos: v => true,
    activos: v => v.active,
    cerca: v => v.dist <= 400,
    mejor: v => v.rating >= 4.8,
};

let currentFilter = 'todos';

// Quita acentos y mayúsculas para buscar cómodo ("senora" encuentra "Señora")
const normalize = s => s.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').trim();

// ── Templates HTML ──
const PIN_PATH = 'M13 0C6.4 0 1 5.4 1 12c0 7.2 10.2 17.1 11.4 18.2.3.3.9.3 1.2 0C14.8 29.1 25 19.2 25 12 25 5.4 19.6 0 13 0z';

function cardHTML(v) {
    return `
    <div class="vendor-card">
        <div class="vendor-top">
            <div>
                <div class="vendor-name">${v.name} ${v.active ? '<span class="status-badge">Activo</span>' : ''}</div>
                <div class="vendor-spec">${v.spec}</div>
            </div>
            <div class="vendor-dist">${v.dist} m</div>
        </div>
        <div class="vendor-bottom">
            <div class="vendor-time">
                <svg width="12" height="12"><use href="#i-clock"/></svg> ${v.time}
            </div>
            <div class="vendor-rating">
                <svg width="12" height="12"><use href="#i-star"/></svg>
                <span class="rating-n">${v.rating}</span>
                <span class="rating-c">(${v.count})</span>
            </div>
        </div>
    </div>`;
}

function pinHTML(v) {
    const size = v.active ? [26, 30] : [20, 24];
    return `
    <div class="pin" style="left:${v.x}%;top:${v.y}%">
        <div class="pin-label ${v.active ? 'active' : 'inactive'}" ${v.active ? 'style="position:relative"' : ''}>
            ${v.active ? '<span class="pin-ring"></span>' : ''}${v.name}
        </div>
        <svg width="${size[0]}" height="${size[1]}" viewBox="0 0 26 30">
            <path d="${PIN_PATH}" fill="${v.active ? '#3D2B1F' : '#C7B299'}"/>
            <circle cx="13" cy="12" r="5" fill="#FBF7F0"/>
        </svg>
    </div>`;
}

// ── Renderizado sincronizado (Tarjetas + Pines) ──
function render() {
    const searchInput = document.getElementById('search-input');
    const query = normalize(searchInput ? searchInput.value : '');

    let list = VENDORS
        .filter(FILTERS[currentFilter])
        .filter(v => !query || normalize(v.name).includes(query) || normalize(v.spec).includes(query));

    // Ordenamiento opcional según el filtro activo
    if (currentFilter === 'cerca') list.sort((a, b) => a.dist - b.dist);
    if (currentFilter === 'mejor') list.sort((a, b) => b.rating - a.rating);

    // 1. Actualizar Tarjetas en el Panel derecho
    const box = document.getElementById('vendors');
    if (box) {
        box.innerHTML = list.length ?
            list.map(cardHTML).join('') :
            `<div style="text-align:center;padding:2rem 1.5rem;color:var(--mid);font-size:.875rem">
                   No se encontró ningún tamalero${query ? ` para “${searchInput.value.trim()}”` : ''}.
               </div>`;
    }

    // 2. Actualizar Pines en el Mapa (respetando los controles y la leyenda)
    const wrap = document.getElementById('map-wrap');
    if (wrap) {
        // Borrar únicamente los pines existentes, sin tocar la leyenda ni los botones de zoom
        wrap.querySelectorAll('.pin').forEach(p => p.remove());
        
        const controls = wrap.querySelector('.map-controls');
        if (controls) {
            controls.insertAdjacentHTML('beforebegin', list.map(pinHTML).join(''));
        }
    }
}

// ── Controladores de Eventos ──
function setPill(el) {
    document.querySelectorAll('.pill').forEach(p => p.classList.remove('active'));
    el.classList.add('active');
    currentFilter = el.dataset.filter || 'todos';
    render();
}

// Escuchar cambios en la barra de búsqueda en tiempo real
const searchElement = document.getElementById('search-input');
if (searchElement) {
    searchElement.addEventListener('input', render);
}

// Ejecutar el render inicial al cargar la página
document.addEventListener('DOMContentLoaded', () => {
    render();
});
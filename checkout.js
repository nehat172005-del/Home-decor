const cart = JSON.parse(localStorage.getItem('casaCart') || '[]');
const items = document.querySelector('#items');
const total = cart.reduce((sum, item) => sum + Number(item.price), 0);
const money = value => '$' + value.toLocaleString('en-US');

if (!cart.length) {
  items.innerHTML = '<p>Your bag is empty. Return to the store to choose something lovely.</p>';
  document.querySelector('#checkoutForm').hidden = true;
} else {
  items.innerHTML = cart.map(item => `<div class="item"><img src="${item.image}" alt="${item.name}"><div><h3>${item.name}</h3><p>${item.category}</p></div><strong>${money(item.price)}</strong></div>`).join('');
}
document.querySelector('#totalDisplay').textContent = money(total);
document.querySelector('#total').value = total.toFixed(2);

function showError(text) {
  const message = document.querySelector('#message');
  message.textContent = text;
  message.hidden = false;
}

document.querySelector('#checkoutForm').addEventListener('submit', async event => {
  event.preventDefault();
  const form = event.currentTarget;
  const button = form.querySelector('button');
  button.disabled = true;
  button.textContent = 'Placing order...';
  try {
    const response = await fetch('/api/orders', { method: 'POST', headers: { 'Accept': 'application/json' }, body: new URLSearchParams(new FormData(form)) });
    const result = await response.json();
    if (!response.ok) throw new Error(result.message || 'Unable to place order.');
    document.querySelector('#orderNumber').textContent = '#' + result.orderId;
    form.hidden = true;
    document.querySelector('#confirmation').hidden = false;
    localStorage.removeItem('casaCart');
  } catch (error) {
    showError(error.message);
    button.disabled = false;
    button.innerHTML = 'Place order <span>→</span>';
  }
});

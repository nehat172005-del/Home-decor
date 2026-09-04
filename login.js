const loginForm = document.querySelector('#loginForm');
const registerForm = document.querySelector('#registerForm');
const switchButton = document.querySelector('#switchButton');
const message = document.querySelector('#message');
const formTitle = document.querySelector('#formTitle');
const formSubtitle = document.querySelector('#formSubtitle');

function showMessage(text) {
  message.textContent = text;
  message.hidden = false;
}

async function submitForm(form, endpoint) {
  const response = await fetch(endpoint, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'Accept': 'application/json' },
    body: new URLSearchParams(new FormData(form))
  });
  const text = await response.text();
  let result;
  try {
    result = JSON.parse(text);
  } catch {
    throw new Error('The server returned an invalid response. Open this page using http://localhost:8080/login.');
  }
  if (!response.ok) throw new Error(result.message || 'Something went wrong.');
  return result;
}

loginForm.addEventListener('submit', async (event) => {
  event.preventDefault();
  message.hidden = true;
  try {
    await submitForm(loginForm, '/login');
    window.location.assign('/');
  } catch (error) {
    showMessage(error.message);
  }
});

registerForm.addEventListener('submit', async (event) => {
  event.preventDefault();
  message.hidden = true;
  try {
    await submitForm(registerForm, '/register');
    showMessage('Account created. You can now sign in.');
    registerForm.hidden = true;
    loginForm.hidden = false;
    formTitle.textContent = 'Welcome back.';
    formSubtitle.textContent = 'Sign in to keep your considered pieces close.';
    switchButton.textContent = 'New here? Create an account';
  } catch (error) {
    showMessage(error.message);
  }
});

switchButton.addEventListener('click', () => {
  const showingLogin = !loginForm.hidden;
  loginForm.hidden = showingLogin;
  registerForm.hidden = !showingLogin;
  message.hidden = true;
  formTitle.textContent = showingLogin ? 'Create your account.' : 'Welcome back.';
  formSubtitle.textContent = showingLogin ? 'A home for the things you love.' : 'Sign in to keep your considered pieces close.';
  switchButton.textContent = showingLogin ? 'Already have an account? Sign in' : 'New here? Create an account';
});

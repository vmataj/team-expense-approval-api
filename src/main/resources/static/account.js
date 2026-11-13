async function loadAccount() {
    const response = await fetch('/account', { cache: 'no-store' });
    if (response.status === 404) return null;
    if (!response.ok) throw new Error('Could not check your session. Reload to try again.');
    return response.json();
}

function updateAccountHeader(account) {
    const panel = document.querySelector('#account-panel');
    panel.hidden = false;
    if (!account.username) return;
    document.querySelector('#account-status').textContent = account.username;
    document.querySelector('#login').hidden = true;
    document.querySelector('#signup-link').hidden = true;
    const logout = document.querySelector('#logout');
    logout.elements._csrf.value = account.csrfToken;
    logout.hidden = false;
}

const accountForm = document.querySelector('#account-form');
if (accountForm) {
    const feedback = document.querySelector('#account-feedback');
    const button = accountForm.querySelector('button');
    const query = new URLSearchParams(location.search);
    if (query.has('error')) feedback.textContent = 'Username or password is incorrect.';
    if (query.has('created')) feedback.textContent = 'Account created. Sign in to get started.';
    loadAccount().then(account => {
        if (!account) {
            feedback.textContent = 'Account access is disabled in local demo mode.';
            return;
        }
        if (account.username) { location.replace('/'); return; }
        accountForm.elements._csrf.value = account.csrfToken;
        button.disabled = false;
        if (accountForm.dataset.mode !== 'signup') return;
        accountForm.addEventListener('submit', async event => {
            event.preventDefault();
            button.disabled = true;
            try {
                const { username, password } = accountForm.elements;
                const response = await fetch('/account/signup', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json', [account.csrfHeader]: account.csrfToken },
                    body: JSON.stringify({ username: username.value, password: password.value })
                });
                if (response.ok) location.assign('/login.html?created');
                else if (response.status === 409) feedback.textContent = 'That username is taken. Choose another.';
                else if (response.status === 403) feedback.textContent = 'This form expired. Reload and try again.';
                else feedback.textContent = 'Check the username and password requirements, then try again.';
            } catch {
                feedback.textContent = 'Could not reach the server. Try again.';
            } finally {
                button.disabled = false;
            }
        });
    }).catch(error => { feedback.textContent = error.message; });
}

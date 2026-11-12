async function startAccount() {
    const response = await fetch('/account', { cache: 'no-store' });
    if (response.status === 404) return null;
    if (!response.ok) throw new Error('Could not check your session. Reload to try again.');
    const account = await response.json();
    document.querySelector('#account-panel').hidden = false;
    const status = document.querySelector('#account-status');
    if (account.username) {
        status.textContent = 'Signed in as ' + account.username + '.';
        document.querySelector('#login').hidden = true;
        const logout = document.querySelector('#logout');
        logout.elements._csrf.value = account.csrfToken;
        logout.hidden = false;
        return account;
    }
    const signup = document.querySelector('#signup');
    signup.hidden = false;
    signup.addEventListener('submit', async event => {
        event.preventDefault();
        const data = Object.fromEntries(new FormData(signup));
        const button = signup.querySelector('button');
        button.disabled = true;
        try {
            const result = await fetch('/account/signup', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', [account.csrfHeader]: account.csrfToken },
                body: JSON.stringify(data)
            });
            if (result.ok) location.assign('/login');
            else status.textContent = result.status === 409 ? 'Username already in use.' : 'Check your username and password.';
        } catch {
            status.textContent = 'Could not reach the server. Try again.';
        } finally {
            button.disabled = false;
        }
    });
    return account;
}

function showAccountError(error) {
    document.querySelector('#account-panel').hidden = false;
    document.querySelector('#account-status').textContent = error.message;
}

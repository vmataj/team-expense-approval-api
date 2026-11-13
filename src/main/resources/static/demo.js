const form = document.querySelector('#claim');
const actor = document.querySelector('#actor');
const filter = document.querySelector('#filter');
const claims = document.querySelector('#claims');
const statusLine = document.querySelector('#status');
let view = 0;
let session = null;
let preview = false;
const examples = [
    { description: 'Rail ticket to client site', amount: 32.40, status: 'PENDING', history: [{ action: 'SUBMITTED', actor: 'Sample employee' }] },
    { description: 'Team workshop supplies', amount: 86.50, status: 'APPROVED', history: [{ action: 'SUBMITTED', actor: 'Sample employee' }, { action: 'APPROVED', actor: 'Sample reviewer' }] },
    { description: 'Duplicate taxi receipt', amount: 18.20, status: 'REJECTED', history: [{ action: 'REJECTED', actor: 'Sample reviewer' }] }
];
const euros = new Intl.NumberFormat('en', { style: 'currency', currency: 'EUR' });

function currentAccount() {
    return session?.username ?? actor.value;
}

async function request(path, account, method = 'GET', payload) {
    const headers = session ? { [session.csrfHeader]: session.csrfToken }
        : { Authorization: 'Basic ' + btoa(account + ':demo-pass') };
    if (payload) headers['Content-Type'] = 'application/json';
    const response = await fetch('/api/claims' + path, {
        method, headers, body: payload && JSON.stringify(payload)
    });
    if (response.status === 409) throw new Error('This claim is already closed.');
    if (response.status === 400) {
        const problem = await response.json().catch(() => null);
        throw new Error(problem?.detail ?? 'Enter a description and a valid amount with up to two decimal places.');
    }
    if (response.redirected || response.status === 401 || response.status === 403) {
        throw new Error('Sign in again to continue.');
    }
    if (!response.ok) throw new Error('Could not load or update claims. Try again.');
    return response.json();
}

async function refresh(message) {
    const active = ++view;
    const account = currentAccount();
    form.hidden = preview || (!session && account === 'manager');
    claims.replaceChildren();
    statusLine.textContent = 'Loading claims…';
    try {
        const data = preview ? examples : await request('', account);
        if (active !== view) return;
        const visible = data.filter(claim => !filter.value || claim.status === filter.value);
        statusLine.textContent = message ?? (visible.length ? '' : 'No claims match this view.');
        for (const claim of visible) {
            const card = document.createElement('article');
            card.className = 'ledger-row';
            const summary = document.createElement('div');
            summary.className = 'claim-summary';
            const heading = document.createElement('h3');
            heading.textContent = claim.description;
            const amount = document.createElement('strong');
            amount.textContent = euros.format(claim.amount);
            const badge = document.createElement('span');
            badge.className = 'badge';
            badge.dataset.status = claim.status;
            badge.textContent = claim.status.toLowerCase();
            summary.append(heading, amount, badge);
            card.append(summary);
            for (const event of claim.history) {
                const line = document.createElement('p');
                line.textContent = event.action.toLowerCase() + ' by ' + event.actor + (event.time ? ' · ' + new Date(event.time).toLocaleString() : '');
                card.append(line);
            }
            if (!preview && claim.status === 'PENDING') {
                const canReview = session ? claim.reviewer === account : account === 'manager';
                const actions = canReview
                    ? [['Approve', '/decision', { outcome: 'APPROVED' }], ['Reject', '/decision', { outcome: 'REJECTED' }]]
                    : [['Withdraw', '/withdraw']];
                for (const [label, path, payload] of actions) {
                    const button = document.createElement('button');
                    button.textContent = label;
                    button.addEventListener('click', () => change('/' + claim.id + path, payload, 'Claim updated.'));
                    card.append(button);
                }
            }
            claims.append(card);
        }
    } catch (error) {
        if (active === view) statusLine.textContent = error.message;
    }
}

async function change(path, payload, message) {
    const controls = [actor, filter, ...form.elements, ...claims.querySelectorAll('button')];
    controls.forEach(control => { control.disabled = true; });
    try {
        await request(path, currentAccount(), 'POST', payload);
        filter.value = '';
        await refresh(message);
    } catch (error) {
        await refresh(error.message);
    } finally {
        controls.forEach(control => { control.disabled = false; });
    }
}

form.addEventListener('submit', event => {
    event.preventDefault();
    if (preview) return;
    const payload = Object.fromEntries(new FormData(form));
    payload.description = payload.description.trim();
    if (session) payload.reviewer = payload.reviewer.trim().toLowerCase();
    change('', payload, 'Claim submitted.');
});
actor.addEventListener('change', () => refresh());
filter.addEventListener('change', () => refresh());
loadAccount().then(account => {
    session = account;
    if (session) {
        updateAccountHeader(session);
        preview = !session.username;
        document.querySelector('#preview-label').hidden = !preview;
        document.querySelector('#preview-note').hidden = !preview;
        document.querySelector('#demo-account').hidden = true;
        document.querySelector('#reviewer-field').hidden = false;
        form.elements.reviewer.disabled = false;
        form.elements.reviewer.required = true;
        form.querySelector('button').textContent = 'Submit claim';
    }
    document.querySelector('#claims-app').hidden = false;
    return refresh();
}).catch(error => { document.querySelector('#startup-status').textContent = error.message; });

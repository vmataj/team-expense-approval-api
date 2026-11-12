const form = document.querySelector('#claim');
const actor = document.querySelector('#actor');
const filter = document.querySelector('#filter');
const claims = document.querySelector('#claims');
const statusLine = document.querySelector('#status');
let view = 0;
let session = null;

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
    if (!response.ok) throw new Error('API returned HTTP ' + response.status + '.');
    return response.json();
}

async function refresh(message) {
    const active = ++view;
    const account = currentAccount();
    form.hidden = !session && account === 'manager';
    claims.replaceChildren();
    statusLine.textContent = 'Loading claims…';
    try {
        const data = await request('', account);
        if (active !== view) return;
        const visible = data.filter(claim => !filter.value || claim.status === filter.value);
        statusLine.textContent = message ?? (visible.length ? '' : 'No claims match this view.');
        for (const claim of visible) {
            const card = document.createElement('article');
            const heading = document.createElement('h2');
            heading.textContent = claim.description + ' · €' + Number(claim.amount).toFixed(2);
            card.append(heading);
            for (const event of claim.history) {
                const line = document.createElement('p');
                line.textContent = event.action + ' · ' + event.actor + ' · ' + new Date(event.time).toLocaleString();
                card.append(line);
            }
            if (claim.status === 'PENDING') {
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
    change('', Object.fromEntries(new FormData(form)), 'Claim submitted.');
});
actor.addEventListener('change', () => refresh());
filter.addEventListener('change', () => refresh());
startAccount().then(account => {
    session = account;
    if (session) {
        if (!session.username) return;
        document.querySelector('#demo-account').hidden = true;
        document.querySelector('#reviewer-field').hidden = false;
        form.elements.reviewer.disabled = false;
        form.elements.reviewer.required = true;
        form.querySelector('button').textContent = 'Submit claim';
    }
    document.querySelector('#claims-app').hidden = false;
    return refresh();
}).catch(showAccountError);

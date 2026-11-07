const form = document.querySelector('#claim');
const claims = document.querySelector('#claims');
const statusLine = document.querySelector('#status');

async function request(path, actor, payload) {
    const headers = { Authorization: 'Basic ' + btoa(actor + ':demo-pass') };
    const options = { headers };
    if (payload) {
        options.method = 'POST';
        headers['Content-Type'] = 'application/json';
        options.body = JSON.stringify(payload);
    }
    const response = await fetch('/api/claims' + path, options);
    if (!response.ok) throw new Error('API returned HTTP ' + response.status);
    return response.json();
}

async function refresh() {
    const data = await request('', 'manager');
    claims.replaceChildren();
    for (const claim of data) {
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
            for (const outcome of ['APPROVED', 'REJECTED']) {
                const button = document.createElement('button');
                button.textContent = outcome === 'APPROVED' ? 'Approve' : 'Reject';
                button.addEventListener('click', async () => {
                    try {
                        await request('/' + claim.id + '/decision', 'manager', { outcome });
                        statusLine.textContent = 'Decision recorded as manager.';
                        await refresh();
                    } catch (error) {
                        statusLine.textContent = error.message;
                    }
                });
                card.append(button);
            }
        }
        claims.append(card);
    }
}

form.addEventListener('submit', async event => {
    event.preventDefault();
    try {
        await request('', 'employee', Object.fromEntries(new FormData(form)));
        statusLine.textContent = 'Claim submitted as employee.';
        await refresh();
    } catch (error) {
        statusLine.textContent = error.message;
    }
});
refresh().catch(error => { statusLine.textContent = error.message; });

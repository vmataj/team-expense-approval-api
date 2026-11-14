import { scanReceipt } from './receipts.js';

const $ = id => document.getElementById(id);
const categories = {
    FOOD: ['Food & dining', '#63a383'], TRANSPORT: ['Transport', '#628fb2'], HOUSING: ['Housing', '#27634e'],
    UTILITIES: ['Utilities', '#d39e51'], HEALTH: ['Health', '#8d7bb1'], SHOPPING: ['Shopping', '#bc788b'], OTHER: ['Other', '#9aada2']
};
const pages = {
    overview: ['Spending overview', 'See where your money went this month.'],
    expenses: ['Expenses', 'Add, search, and edit expenses in the selected month.'],
    budgets: ['Category budgets', 'Set a limit for each category and see how much is left.'],
    recurring: ['Recurring expenses', 'Schedule monthly expenses such as rent and subscriptions.'],
    reports: ['Monthly reports', 'Compare spending, review trends, and export your ledger.']
};
const storage = 'spendline-sample-v1';
const moneyFormat = new Intl.NumberFormat(undefined, { style: 'currency', currency: 'EUR' });
const money = cents => moneyFormat.format(cents / 100);
const cents = value => Math.round(Number(value) * 100);
const dayLabel = new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', timeZone: 'UTC' });
const day = value => dayLabel.format(new Date(`${value}T00:00:00Z`));
function today() {
    const local = new Date();
    return new Date(local.getTime() - local.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
}
function monthOffset(value, offset) {
    const date = new Date(`${value}-01T00:00:00Z`);
    date.setUTCMonth(date.getUTCMonth() + offset);
    return date.toISOString().slice(0, 7);
}
function monthDays(value) {
    const date = new Date(`${monthOffset(value, 1)}-01T00:00:00Z`);
    date.setUTCDate(0);
    return date.getUTCDate();
}
const label = category => categories[category]?.[0] ?? category;
const sum = list => list.reduce((total, entry) => total + cents(entry.amount), 0);
let month = today().slice(0, 7);
let page = 'overview';
let account = null;
let data = { entries: [], previous: [], budgets: [], recurring: [] };
let sample = loadSample();
let submitEditor;
let loadVersion = 0;

function loadSample() {
    try {
        const saved = JSON.parse(localStorage.getItem(storage));
        if (saved && Array.isArray(saved.entries) && Array.isArray(saved.budgets) && Array.isArray(saved.recurring)) return saved;
    } catch {}
    const examples = [
        [1, 'Apartment rent', 820, 'HOUSING'], [2, 'Metro pass', 42, 'TRANSPORT'], [3, 'Groceries', 65.4, 'FOOD'],
        [4, 'Internet', 31, 'UTILITIES'], [5, 'Pharmacy', 18.6, 'HEALTH'], [6, 'Electricity', 76.2, 'UTILITIES'],
        [7, 'Dinner out', 38, 'FOOD'], [8, 'Weekend train', 56.5, 'TRANSPORT'], [10, 'Groceries', 71.3, 'FOOD'],
        [11, 'Bookshop', 30, 'SHOPPING'], [12, 'Coffee and lunch', 22.8, 'FOOD'], [14, 'Water bill', 23.5, 'UTILITIES']
    ];
    const dates = [monthOffset(month, -1), month];
    return {
        entries: dates.flatMap((value, index) => examples.map(([d, description, amount, category], row) => ({
            id: `sample-${index}-${row}`, spentOn: `${value}-${String(index ? Math.min(d, Number(today().slice(-2))) : d).padStart(2, '0')}`,
            description, amount: index ? amount : Math.round(amount * 109) / 100, category
        }))),
        budgets: [['FOOD', 280], ['HOUSING', 900], ['TRANSPORT', 130], ['UTILITIES', 160]]
            .map(([category, amount]) => ({ month, category, amount })),
        recurring: []
    };
}

function saveSample() {
    try { localStorage.setItem(storage, JSON.stringify(sample)); } catch {}
}

function syncSample() {
    for (const rule of sample.recurring) {
        while (rule.nextDate <= today()) {
            sample.entries.push({ ...rule, id: crypto.randomUUID(), spentOn: rule.nextDate, recurring: true });
            const next = monthOffset(rule.nextDate.slice(0, 7), 1);
            rule.nextDate = `${next}-${String(Math.min(rule.anchorDay, monthDays(next))).padStart(2, '0')}`;
        }
    }
    saveSample();
}

function el(tag, text, className) {
    const node = document.createElement(tag);
    if (text !== undefined) node.textContent = text;
    if (className) node.className = className;
    return node;
}
function button(text, action, className = 'text-button') {
    const node = el('button', text, className);
    node.type = 'button';
    node.addEventListener('click', action);
    return node;
}
function message(id, text = '') {
    $(id).textContent = text;
    $(id).hidden = !text;
}
function empty(text) { return el('p', text, 'empty'); }

async function api(path, method = 'GET', body) {
    const headers = { Accept: 'application/json, application/problem+json' };
    if (method !== 'GET') headers[account.csrfHeader] = account.csrfToken;
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    const response = await fetch(`/api/ledger${path}`, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
    if (response.ok) return method === 'GET' ? response.json() : undefined;
    if ([401, 403].includes(response.status)) throw new Error('Your session expired. Reload and sign in again.');
    const problem = await response.json().catch(() => null);
    throw new Error(problem?.detail || (response.status === 400 ? 'Check the amount, date, and category.' : 'Could not save or load the ledger. Try again.'));
}
async function refresh() {
    const version = ++loadVersion;
    const selected = month;
    if (account?.username) {
        const next = await api(`?month=${selected}`);
        if (version !== loadVersion) return;
        data = next;
    } else {
        const entriesFor = value => sample.entries.filter(entry => entry.spentOn.startsWith(value))
            .sort((a, b) => b.spentOn.localeCompare(a.spentOn));
        data = {
            entries: entriesFor(selected), previous: entriesFor(monthOffset(selected, -1)),
            budgets: sample.budgets.filter(budget => budget.month === selected), recurring: sample.recurring
        };
    }
    render();
}
async function initialize() {
    $('month').value = month;
    setCategories($('category-filter'));
    syncSample();
    await refresh();
    try {
        const response = await fetch('/account', { cache: 'no-store' });
        if (response.status === 401 || response.status === 404) {
            $('guest').hidden = true;
            return;
        }
        if (!response.ok) throw new Error('Could not check your session. Reload to try again.');
        account = await response.json();
        if (!account.username) return;
        $('guest').hidden = true;
        $('account').hidden = false;
        $('mode').textContent = 'Personal ledger';
        $('username').textContent = account.username;
        $('account').elements._csrf.value = account.csrfToken;
        data = { entries: [], previous: [], budgets: [], recurring: [] };
        render();
        await api('/sync', 'POST');
        await refresh();
    } catch (error) {
        message('status', error.message);
    }
}
function renderPage() {
    const hash = location.hash.slice(1);
    if (hash !== 'content') page = Object.hasOwn(pages, hash) ? hash : 'overview';
    [$('title').textContent, $('subtitle').textContent] = pages[page];
    for (const link of document.querySelectorAll('[data-page]')) {
        if (link.dataset.page === page) link.setAttribute('aria-current', 'page');
        else link.removeAttribute('aria-current');
    }
    const overview = page === 'overview';
    const reports = page === 'reports';
    $('summary').hidden = !overview && !reports;
    $('categories').hidden = !overview && !reports;
    $('trend').hidden = !overview && !reports;
    $('budget-panel').hidden = !overview && page !== 'budgets';
    $('budget-panel').classList.toggle('wide', page === 'budgets');
    $('insight-panel').hidden = !overview && !reports && page !== 'budgets';
    $('insight-panel').classList.toggle('wide', !overview);
    $('expense-panel').hidden = !overview && page !== 'expenses';
    $('expense-title').textContent = overview ? 'Recent expenses' : 'Expense ledger';
    $('expense-controls').hidden = overview;
    $('view-expenses').hidden = !overview;
    $('recurring-panel').hidden = page !== 'recurring';
    $('export-panel').hidden = !reports;
}
function byCategory(list) {
    const totals = new Map();
    for (const row of list) totals.set(row.category, (totals.get(row.category) || 0) + cents(row.amount));
    return new Map([...totals].sort((a, b) => b[1] - a[1]));
}
function render() {
    renderPage();
    const total = sum(data.entries);
    const previous = sum(data.previous);
    const groups = byCategory(data.entries);
    renderSummary(total, previous);
    renderCategories(total, groups);
    renderTrend();
    renderBudgets(groups);
    renderInsights(total, previous, groups);
    renderExpenses();
    renderRecurring();
}
function stat(name, value, note) {
    const card = el('article');
    card.append(el('span', name), el('strong', value), el('small', note));
    return card;
}
function renderSummary(total, previous) {
    const difference = total - previous;
    const comparison = previous ? (difference ? `${money(Math.abs(difference))} ${difference > 0 ? 'more' : 'less'} vs last month` :
        'Same total as last month') : 'No expenses in the previous month';
    $('summary').replaceChildren(
        stat('Total spent', money(total), comparison),
        stat('Expenses logged', data.entries.length, `${data.entries.filter(row => row.recurring).length} recurring entries`),
        stat('Budgeted categories', data.budgets.length, `${money(sum(data.budgets))} allocated`)
    );
}
function renderCategories(total, groups) {
    $('donut-total').textContent = money(total);
    let start = 0;
    const slices = [];
    const rows = [];
    for (const [category, amount] of groups) {
        const end = start + amount / total * 100;
        const color = categories[category][1];
        slices.push(`${color} ${start}% ${end}%`);
        start = end;
        const row = el('li');
        const name = el('span');
        const dot = el('span', undefined, 'dot');
        dot.style.background = color;
        dot.setAttribute('aria-hidden', 'true');
        name.append(dot, document.createTextNode(label(category)));
        row.append(name, el('strong', money(amount)));
        rows.push(row);
    }
    $('donut').style.background = slices.length ? `conic-gradient(${slices.join(',')})` : '';
    $('category-list').replaceChildren(...(rows.length ? rows : [el('li', 'No expenses this month.')]));
}
function renderTrend() {
    const canvas = $('trend-plot');
    if ($('trend').hidden) return;
    const length = monthDays(month);
    const amounts = Array(length).fill(0);
    for (const entry of data.entries) amounts[Number(entry.spentOn.slice(-2)) - 1] += cents(entry.amount);
    const ctx = canvas.getContext('2d');
    const width = canvas.clientWidth || 520;
    const height = 143;
    const ratio = window.devicePixelRatio || 1;
    canvas.width = Math.round(width * ratio);
    canvas.height = Math.round(height * ratio);
    ctx.scale(ratio, ratio);
    const left = 46;
    const bottom = 116;
    const peak = Math.max(...amounts);
    const scale = Math.max(100, Math.ceil(peak / 2000) * 2000);
    const step = (width - left - 8) / length;
    ctx.font = '11px system-ui';
    for (let tick = 0; tick <= 2; tick++) {
        const y = bottom - tick * 46;
        ctx.strokeStyle = '#dce7de';
        ctx.beginPath();
        ctx.moveTo(left, y);
        ctx.lineTo(width - 6, y);
        ctx.stroke();
        ctx.fillStyle = '#52665d';
        ctx.fillText(`€${Math.round(scale / 200 * tick).toLocaleString()}`, 0, y + 4);
    }
    amounts.forEach((amount, index) => {
        const bar = amount / scale * 92;
        ctx.fillStyle = amount === peak ? '#155844' : '#80ae98';
        ctx.fillRect(left + index * step + 1, bottom - bar, Math.max(1, step - 3), bar);
        if ([0, 7, 14, 21, length - 1].includes(index)) {
            ctx.fillStyle = '#52665d';
            ctx.fillText(String(index + 1), left + index * step, 135);
        }
    });
    const strongest = amounts.indexOf(peak) + 1;
    const text = peak ? `Highest day: ${day(`${month}-${String(strongest).padStart(2, '0')}`)}, ${money(peak)}.` : 'Add an expense to see daily spending.';
    $('trend-detail').textContent = text;
    canvas.setAttribute('aria-label', `Daily spending for ${month}. ${text}`);
}
function renderBudgets(groups) {
    const rows = data.budgets.map(budget => {
        const spent = groups.get(budget.category) ?? 0;
        const cap = cents(budget.amount);
        const row = el('div', undefined, 'budget-row');
        const heading = el('div', undefined, 'budget-label');
        const text = `${money(spent)} of ${money(cap)}${spent > cap ? ' · Over budget' : ''}`;
        heading.append(button(label(budget.category), () => editBudget(budget)), el('span', text, spent > cap ? 'danger' : 'muted'));
        const progress = el('progress', undefined, spent > cap ? 'over' : '');
        progress.max = cap;
        progress.value = Math.min(spent, cap);
        progress.setAttribute('aria-label', `${label(budget.category)}: ${text}`);
        row.append(heading, progress);
        return row;
    });
    $('budget-list').replaceChildren(...(rows.length ? rows : [empty('Set a category limit to track it here.')]));
}
function renderInsights(total, previous, groups) {
    const notes = [];
    const top = groups.entries().next().value;
    if (top) notes.push(`${label(top[0])} was your largest category at ${Math.round(top[1] / total * 100)}% of spending.`);
    if (previous && total) {
        const change = Math.round(Math.abs(total - previous) / previous * 100);
        notes.push(change ? `You spent ${change}% ${total > previous ? 'more' : 'less'} than last month.` : 'Your total is within 1% of last month.');
    }
    const close = data.budgets.find(budget => (groups.get(budget.category) ?? 0) >= cents(budget.amount) * .85);
    if (close) {
        const available = cents(close.amount) - (groups.get(close.category) ?? 0);
        notes.push(available < 0 ? `${label(close.category)} is ${money(-available)} over budget. Review that category’s expenses.` :
            `${label(close.category)} has ${money(available)} left this month. Check upcoming expenses in that category.`);
    } else {
        const unbudgeted = [...groups].find(([category]) => !data.budgets.some(budget => budget.category === category));
        if (unbudgeted) notes.push(`Consider setting a limit for ${label(unbudgeted[0]).toLowerCase()}. You spent ${money(unbudgeted[1])} there this month.`);
    }
    $('insight-list').replaceChildren(...(notes.length ? notes : ['Insights appear once you log expenses.']).map(note => el('li', note)));
}

function renderExpenses() {
    let entries = data.entries;
    if (page === 'overview') entries = entries.slice(0, 5);
    else {
        const category = $('category-filter').value;
        const query = $('entry-search').value.toLowerCase().trim();
        entries = entries.filter(row => (!category || row.category === category) && row.description.toLowerCase().includes(query));
    }
    $('expenses-body').replaceChildren(...entries.map(entry => {
        const row = el('tr');
        const actions = el('td', undefined, 'table-actions');
        actions.append(button('Edit', () => editEntry(entry)), button('Remove', () => removeEntry(entry), 'text-button danger'));
        row.append(el('td', `${entry.description}${entry.recurring ? ' ↻' : ''}`), el('td', label(entry.category)),
            el('td', day(entry.spentOn)), el('td', money(cents(entry.amount))), actions);
        return row;
    }));
    $('expenses-table').hidden = !entries.length;
    message('expenses-empty', entries.length ? '' : 'No expenses match this view.');
}
function renderRecurring() {
    $('recurring-list').replaceChildren(...(data.recurring.length ? data.recurring.map(rule => {
        const row = el('div', undefined, 'rule');
        const details = el('div');
        details.append(el('strong', rule.description), el('p',
            `${label(rule.category)} · ${money(cents(rule.amount))} per month · Next ${rule.nextDate}`, 'muted'));
        row.append(details, button('Stop schedule', () => stopRule(rule), 'text-button danger'));
        return row;
    }) : [empty('Schedule a monthly expense to add it automatically when due.')]));
}
function setCategories(select) {
    for (const [key, [name]] of Object.entries(categories)) {
        const option = el('option', name);
        option.value = key;
        select.append(option);
    }
}
function field(name, caption, type, value = '', attributes = {}) {
    const wrapper = el('label', caption);
    const input = el('input');
    input.type = type;
    input.name = name;
    input.value = value;
    input.required = true;
    Object.assign(input, attributes);
    wrapper.append(input);
    return wrapper;
}
function categoryField(value) {
    const wrapper = el('label', 'Category');
    const select = el('select');
    select.name = 'category';
    setCategories(select);
    select.value = value;
    wrapper.append(select);
    return wrapper;
}
function editor(title, actionLabel, fields, action) {
    $('editor-title').textContent = title;
    $('editor-submit').textContent = actionLabel;
    $('editor-fields').replaceChildren(...fields);
    message('editor-status');
    submitEditor = action;
    $('editor').showModal();
}
async function save(path, method, body, mutate) {
    if (account?.username) await api(path, method, body);
    else {
        mutate();
        saveSample();
    }
    try {
        await refresh();
        message('status');
    } catch {
        message('status', 'Saved. Reload the ledger to see the change.');
    }
}
function editEntry(entry) {
    const template = $('entry-fields').content.cloneNode(true);
    const actualDay = today();
    const initialDate = month === actualDay.slice(0, 7) ? actualDay : month < actualDay.slice(0, 7) ? `${month}-01` : actualDay;
    const chosenDate = template.querySelector('[name=spentOn]');
    chosenDate.value = entry?.spentOn ?? initialDate;
    chosenDate.max = actualDay;
    chosenDate.readOnly = Boolean(entry?.recurring);
    template.querySelector('[name=description]').value = entry?.description ?? '';
    template.querySelector('[name=amount]').value = entry?.amount ?? '';
    const category = template.querySelector('[name=category]');
    setCategories(category);
    category.value = entry?.category ?? 'FOOD';
    const scan = template.querySelector('.receipt');
    scan.hidden = Boolean(entry);
    scanReceipt(scan);
    editor(entry ? 'Edit expense' : 'Add expense', 'Save expense', [template], async body => {
        body.description = body.description.trim();
        if (!body.description) throw new Error('Enter a description.');
        if (body.spentOn > today()) throw new Error('Choose today or an earlier date.');
        const previousMonth = month;
        month = body.spentOn.slice(0, 7);
        $('month').value = month;
        try {
            await save(entry ? `/entries/${entry.id}` : '/entries', entry ? 'PUT' : 'POST', body, () => {
                if (entry) sample.entries = sample.entries.map(row => row.id === entry.id ? { ...row, ...body } : row);
                else sample.entries.push({ ...body, id: crypto.randomUUID() });
            });
        } catch (error) {
            month = previousMonth;
            $('month').value = month;
            throw error;
        }
    });
}
function removeEntry(entry) {
    editor('Remove expense?', 'Remove', [empty(entry.description)], () => save(`/entries/${entry.id}`, 'DELETE', undefined,
        () => { sample.entries = sample.entries.filter(row => row.id !== entry.id); }));
}
function editBudget(existing) {
    const selected = month;
    const category = categoryField(existing?.category ?? 'FOOD');
    const amount = field('amount', 'Monthly limit in EUR', 'number', existing?.amount ?? '',
        { min: '0', max: '9999999999.99', step: '0.01' });
    category.querySelector('select').addEventListener('change', event => {
        amount.querySelector('input').value = data.budgets.find(row => row.category === event.target.value)?.amount ?? '';
    });
    editor(`Set a budget for ${selected}`, 'Save budget', [category, amount, empty('Set the limit to zero to remove it.')], body =>
        save(`/budgets/${selected}/${body.category}`, 'PUT', { amount: body.amount }, () => {
            sample.budgets = sample.budgets.filter(row => row.month !== selected || row.category !== body.category);
            if (Number(body.amount)) sample.budgets.push({ ...body, month: selected });
        }));
}
function newRule() {
    const now = today();
    const dateIn = offset => {
        const target = monthOffset(now.slice(0, 7), offset);
        return `${target}-${String(Math.min(Number(now.slice(-2)), monthDays(target))).padStart(2, '0')}`;
    };
    editor('Schedule a monthly expense', 'Schedule expense', [
        field('description', 'Description', 'text', '', { maxLength: 120 }),
        field('amount', 'Amount in EUR', 'number', '', { min: '.01', max: '9999999999.99', step: '.01' }),
        categoryField('UTILITIES'),
        field('firstDate', 'First date', 'date', now, { min: dateIn(-12), max: dateIn(60) }),
        empty('A schedule on the 31st uses the last day of shorter months.')
    ], body => {
        body.description = body.description.trim();
        if (!body.description) throw new Error('Enter a description.');
        return save('/recurring', 'POST', body, () => {
            sample.recurring.push({ ...body, id: crypto.randomUUID(), nextDate: body.firstDate, anchorDay: Number(body.firstDate.slice(-2)) });
            syncSample();
        });
    });
}
function stopRule(rule) {
    editor('Stop monthly expense?', 'Stop schedule', [empty(`Future entries for ${rule.description} will stop. Posted expenses stay in the ledger.`)], () =>
        save(`/recurring/${rule.id}`, 'DELETE', undefined, () => {
            sample.recurring = sample.recurring.filter(row => row.id !== rule.id);
        }));
}
function exportReport() {
    const quote = value => {
        const text = String(value);
        const safe = /^[\s]*[=+\-@]|\t|\r/.test(text) ? `'${text}` : text;
        return `"${safe.replaceAll('"', '""')}"`;
    };
    const rows = data.entries.map(row => [row.spentOn, row.description, label(row.category), (cents(row.amount) / 100).toFixed(2), 'EUR']);
    const csv = [['Date', 'Description', 'Category', 'Amount', 'Currency'], ...rows]
        .map(row => row.map(quote).join(',')).join('\r\n');
    const url = URL.createObjectURL(new Blob(['\ufeff', csv], { type: 'text/csv;charset=utf-8' }));
    const link = el('a');
    link.href = url;
    link.download = `spendline-${month}.csv`;
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
}
function converter() {
    const currency = el('label', 'From currency');
    const select = el('select');
    select.name = 'from';
    for (const code of ['USD', 'GBP', 'CHF', 'CAD', 'AUD', 'JPY', 'ALL', 'PLN']) {
        const option = el('option', code);
        option.value = code;
        select.append(option);
    }
    currency.append(select);
    const output = el('p', '', 'muted');
    output.setAttribute('role', 'status');
    const attribution = el('a', 'Rates from Frankfurter');
    attribution.href = 'https://frankfurter.dev/';
    attribution.target = '_blank';
    attribution.rel = 'noreferrer';
    editor('Convert to EUR', 'Convert', [currency,
        field('amount', 'Amount to convert', 'number', '', { min: '.01', max: '9999999999.99', step: '.01' }),
        output, attribution
    ], async ({ from, amount }) => {
        output.textContent = 'Fetching the latest published rate…';
        try {
            const response = await fetch(`https://api.frankfurter.dev/v2/rate/${from.toLowerCase()}/eur`, { signal: AbortSignal.timeout(8000) });
            if (!response.ok) throw new Error();
            const result = await response.json();
            if (!Number.isFinite(result.rate) || result.rate <= 0 || !result.date) throw new Error();
            output.textContent = `${amount} ${from} ≈ ${money(cents(Number(amount) * result.rate))} using the rate for ${result.date}.`;
        } catch {
            output.textContent = '';
            throw new Error('The exchange rate is unavailable. Try again in a moment.');
        }
        return false;
    });
}
$('editor-form').addEventListener('submit', async event => {
    event.preventDefault();
    const payload = Object.fromEntries(new FormData(event.currentTarget));
    delete payload.receipt;
    const submit = $('editor-submit');
    submit.disabled = true;
    message('editor-status');
    try {
        if (await submitEditor(payload) !== false) $('editor').close();
    } catch (error) {
        message('editor-status', error.message);
    } finally {
        submit.disabled = false;
    }
});
for (const id of ['editor-close', 'editor-cancel']) $(id).addEventListener('click', () => {
    if (!$('editor-submit').disabled) $('editor').close();
});
$('editor').addEventListener('cancel', event => {
    if ($('editor-submit').disabled) event.preventDefault();
});
$('add').addEventListener('click', () => editEntry());
$('set-budget').addEventListener('click', () => editBudget());
$('new-rule').addEventListener('click', newRule);
$('export').addEventListener('click', exportReport);
$('currency-tool').addEventListener('click', converter);
$('month').addEventListener('change', async event => {
    if (!event.currentTarget.checkValidity()) return;
    const prior = month;
    month = event.currentTarget.value;
    const selected = month;
    message('status');
    try { await refresh(); }
    catch (error) {
        if (month !== selected) return;
        month = prior;
        $('month').value = prior;
        render();
        message('status', error.message);
    }
});
$('entry-search').addEventListener('input', renderExpenses);
$('category-filter').addEventListener('change', renderExpenses);
window.addEventListener('hashchange', () => { render(); if (location.hash !== '#content') window.scrollTo(0, 0); });
window.addEventListener('resize', renderTrend);
initialize();

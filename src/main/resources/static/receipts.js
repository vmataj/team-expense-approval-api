const library = 'https://cdn.jsdelivr.net/npm/tesseract.js@6.0.1/dist/tesseract.min.js';
let ocr;

function engine() {
    if (!ocr) {
        ocr = new Promise((resolve, reject) => {
            const script = document.createElement('script');
            script.src = library;
            script.onload = () => window.Tesseract ? resolve(window.Tesseract) : reject(new Error());
            script.onerror = reject;
            document.head.append(script);
        }).catch(error => {
            ocr = undefined;
            throw error;
        });
    }
    return ocr;
}

function receiptFields(text) {
    const lines = text.split(/\r?\n/).map(line => line.trim()).filter(Boolean);
    const totalLines = lines.filter(line => /\b(total(?:\s+(?:paid|due))?|amount\s+(?:paid|due))\b/i.test(line) &&
        !/\b(sub\s*total|tax total|savings total|total tax|total items)\b/i.test(line));
    const amountText = totalLines.at(-1)?.match(/(?:\d{1,3}(?:[., ]\d{3})+|\d+)[.,]\d{2}(?!\d)/g)?.at(-1);
    const amount = amountText?.replace(/[., ](?=.*[.,])/g, '').replace(',', '.');
    const iso = text.match(/\b(20\d{2})[-/.](0?[1-9]|1[0-2])[-/.](0?[1-9]|[12]\d|3[01])\b/);
    const spentOn = iso ? `${iso[1]}-${iso[2].padStart(2, '0')}-${iso[3].padStart(2, '0')}` : null;
    const merchant = lines.find(line => /\p{L}{3}/u.test(line) && !/\b(receipt|invoice|tax|total|date)\b/i.test(line));
    return { amount, spentOn, merchant };
}

export function scanReceipt(section) {
    const input = section.querySelector('input');
    const button = section.querySelector('.scan');
    const status = section.querySelector('.scan-status');
    const preview = section.querySelector('textarea');
    button.addEventListener('click', async () => {
        const file = input.files[0];
        if (!file || !['image/png', 'image/jpeg', 'image/webp'].includes(file.type) || file.size > 12 * 1024 * 1024) {
            status.textContent = 'Choose a PNG, JPEG, or WebP image up to 12 MB.';
            return;
        }
        const form = section.closest('form');
        const save = form.querySelector('#editor-submit');
        button.disabled = true;
        save.disabled = true;
        preview.hidden = true;
        status.textContent = 'Loading receipt scanner…';
        let worker;
        try {
            const tesseract = await engine();
            worker = await tesseract.createWorker('eng', 1, {
                logger: event => {
                    if (event.status === 'recognizing text') status.textContent = `Reading receipt… ${Math.round(event.progress * 100)}%`;
                }
            });
            const { data } = await worker.recognize(file);
            const text = data.text.trim();
            if (!text) {
                status.textContent = 'No text was found. Try a clearer photo.';
                return;
            }
            const { amount, spentOn, merchant } = receiptFields(text);
            const foreign = /(?:\b(?:USD|GBP|CHF|CAD|AUD|JPY|ALL|PLN)\b|[$£¥])/i.test(text);
            if (amount && !foreign) form.elements.amount.value = amount;
            if (spentOn && spentOn <= form.elements.spentOn.max && !Number.isNaN(Date.parse(spentOn)) &&
                new Date(spentOn).toISOString().slice(0, 10) === spentOn) form.elements.spentOn.value = spentOn;
            if (merchant && !form.elements.description.value) form.elements.description.value = merchant.slice(0, 120);
            preview.value = text;
            preview.hidden = false;
            status.textContent = foreign ? 'Review the text and convert the amount to EUR before saving.' : 'Check the description, date, amount, and currency before saving.';
        } catch {
            status.textContent = 'The receipt could not be read. Try again or enter the details manually.';
        } finally {
            try { await worker?.terminate(); } catch {}
            button.disabled = false;
            save.disabled = false;
        }
    });
}

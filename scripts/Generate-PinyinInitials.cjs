// Input: Unihan_Readings.txt from https://www.unicode.org/Public/17.0.0/ucd/Unihan.zip
// The pinned input and generated data are covered by the bundled Unicode License V3.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const zlib = require('node:zlib');

const input = fs.readFileSync(process.argv[2]);
const hash = crypto.createHash('sha256').update(input).digest('hex');
if (hash !== '575e69c9ad85a4737a889a4f94cbd987042a90a1a6cc16dd3f4ed995c715b17c') {
    throw new Error('Expected the unmodified Unicode 17.0.0 Unihan_Readings.txt');
}
const readings = new Map();
for (const line of input.toString('utf8').split(/\r?\n/)) {
    const match = /^U\+([0-9A-F]+)\t(kMandarin|kHanyuPinyin|kXHC1983)\t(.+)$/.exec(line);
    if (!match) continue;
    const code = parseInt(match[1], 16);
    const words = match[3].split(/\s+/).flatMap(value =>
        (match[2] === 'kMandarin' ? value : value.substring(value.indexOf(':') + 1)).split(','));
    for (const word of words) {
        const initial = word.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase()[0];
        if (!initial || !/[a-z]/.test(initial)) throw new Error(`Unsupported reading: ${line}`);
        readings.set(code, (readings.get(code) || 0) | (1 << (initial.charCodeAt(0) - 97)));
    }
}
const start = 0x3400;
const length = 0xa000 - start;
const extras = [...readings].filter(([code]) => code < start || code >= start + length)
    .sort(([left], [right]) => left - right);
const output = Buffer.alloc(16 + length * 4 + extras.length * 8);
output.write('PIN1');
output.writeInt32BE(start, 4);
output.writeInt32BE(length, 8);
output.writeInt32BE(extras.length, 12);
for (let index = 0; index < length; index++) output.writeInt32BE(readings.get(start + index) || 0, 16 + index * 4);
extras.forEach(([code, mask], index) => {
    output.writeInt32BE(code, 16 + length * 4 + index * 8);
    output.writeInt32BE(mask, 20 + length * 4 + index * 8);
});
const destination = path.join(__dirname, '..', 'app', 'src', 'main', 'resources', 'pinyin-initials.bin');
fs.mkdirSync(path.dirname(destination), { recursive: true });
fs.writeFileSync(destination, output);
console.log(JSON.stringify({
    unicode: '17.0.0', characters: readings.size, supplementalEntries: extras.length,
    bytes: output.length, deflatedBytes: zlib.deflateRawSync(output).length,
    sha256: crypto.createHash('sha256').update(output).digest('hex'),
}, null, 2));

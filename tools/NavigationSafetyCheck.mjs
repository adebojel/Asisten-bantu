import { readFile } from 'node:fs/promises';
import assert from 'node:assert/strict';
const source = await readFile('src/screens/NavigationScreen.tsx', 'utf8');
assert.doesNotMatch(source, /Math\.random|setInterval|beepDistance/);
assert.match(source, /JARAK HALANGAN TIDAK TERSEDIA/);
assert.match(source, /Pengukuran jarak dan deteksi halangan belum tersedia/);
assert.match(source, /jalur tidak dapat dinyatakan aman/);
assert.match(source, /deviceorientation/);
console.log('NavigationSafetyCheck: PASS');

interface KbEntry {
  keys: string[];
  answer: string;
  shortOnly: boolean;
}

const SHORT_MAX_WORDS = 4;
const UNITS: Record<string, number> = {
  nol: 0, kosong: 0, satu: 1, dua: 2, tiga: 3, empat: 4, lima: 5, enam: 6, tujuh: 7, delapan: 8, sembilan: 9, sepuluh: 10, sebelas: 11,
};

const FILLERS = new Set([
  'berapa', 'hitung', 'hitungkan', 'hasil', 'hasilnya', 'adalah', 'sama', 'dengan',
  'tolong', 'coba', 'ya', 'itu', 'nya', 'kalkulator', 'jadi', 'apa'
]);

const KB_RAW = `
pertolongan pertama|p3k => Saya bisa memberi panduan dasar untuk: tersedak, luka berdarah, luka bakar, pingsan, serangan jantung, stroke, keracunan, kejang, dan patah tulang. Sebutkan salah satunya. Untuk keadaan gawat, hubungi 112.
tersedak|keselak|tercekik makanan => Jika orang dewasa tersedak dan tidak bisa bicara atau batuk, minta orang lain menghubungi 112. Berdiri di belakangnya, beri lima tepukan keras di punggung antara tulang belikat, lalu lima hentakan perut ke atas. Ulangi sampai benda keluar.
luka berdarah|pendarahan|berdarah banyak|darah keluar => Tekan luka kuat-kuat dengan kain bersih dan jangan dilepas. Angkat bagian yang terluka lebih tinggi dari jantung bila bisa. Jika darah tidak berhenti dalam sepuluh menit atau lukanya dalam, hubungi 112.
luka bakar|terbakar|tersiram air panas => Alirkan air biasa pada luka selama dua puluh menit. Jangan oleskan odol, mentega, atau es. Tutup longgar dengan kain bersih. Jika lukanya luas, melepuh besar, atau di wajah, hubungi 112.
pingsan|tidak sadar|tak sadarkan diri => Baringkan orangnya, tinggikan kakinya, dan longgarkan pakaian. Periksa napasnya. Jika tidak sadar lebih dari satu menit atau tidak bernapas, hubungi 112 segera.
serangan jantung|nyeri dada|dada sakit => Nyeri dada bisa tanda gawat. Hubungi 112 sekarang, duduk tenang, dan jangan banyak bergerak. Jangan menunggu sampai nyerinya hilang.
stroke|wajah miring|bicara pelo => Tanda stroke: wajah miring, lengan lemah, bicara pelo. Segera hubungi 112 dan catat jam mulainya.
keracunan|minum racun|menelan bahan kimia => Jangan dipaksa muntah. Hubungi 112 dan sebutkan zat yang tertelan.
kejang|ayan|epilepsi kambuh => Jangan menahan gerakannya dan jangan memasukkan apa pun ke mulut. Singkirkan benda berbahaya di sekitarnya, lalu miringkan badannya setelah kejang berhenti. Hubungi 112 jika kejang lebih dari lima menit.
patah tulang|tulang patah|terkilir => Jangan digerakkan atau diluruskan. Topang bagian yang cedera, kompres es yang dibungkus kain, lalu hubungi 112 atau pergi ke fasilitas kesehatan.
nomor darurat|nomor ambulans|telepon darurat => Nomor darurat nasional adalah 112. Bilang Kobra SOS untuk mengirim lokasi ke kontak darurat Anda.
menyeberang|nyeberang jalan => Berhenti di tepi jalan, dengarkan kendaraan dari kedua arah, dan menyeberang hanya jika benar-benar sepi atau minta bantuan orang sekitar. Mode tongkat Kobra hanya pembantu, bukan pengganti tongkat putih.
tersesat|nyasar|salah jalan => Berhenti dan tetap tenang. Bilang Kobra arahkan ke tujuan Anda untuk membuka navigasi, atau minta bantuan orang sekitar. Jika dalam bahaya, bilang Kobra SOS.
uang palsu|ciri uang asli => Uang rupiah punya tanda khusus untuk perabaan dan benang pengaman. Pastikan keasliannya dengan bantuan orang yang dipercaya atau bank.
hemat baterai|baterai boros => Kurangi kecerahan layar, matikan Bluetooth yang tidak dipakai, dan matikan mikrofon Kobra saat tidak dibutuhkan dengan perintah Kobra matikan mikrofon.
cara pakai|apa itu kobra|siapa kamu|nama kamu => Saya Kobra, asisten suara untuk membantu mengendalikan HP. Sebut kata Kobra lalu perintah. Bilang Kobra bantuan untuk daftar perintah.
sedih|galau|kesepian|murung => Saya mendengarkan. Tidak apa-apa merasa seperti ini. Tarik napas pelan-pelan. Jika perasaan ini berat dan berlangsung lama, ceritakan ke orang yang Anda percaya atau tenaga profesional.
cemas|takut|khawatir|panik => Tarik napas dalam lewat hidung selama empat hitungan, tahan sebentar, lalu hembuskan pelan. Ulangi beberapa kali. Saya di sini.
semangat|motivasi|lelah|capek|menyerah => Anda sudah berusaha dengan baik. Istirahat sebentar tidak apa-apa, lalu lanjutkan satu langkah kecil berikutnya.
lelucon|lucu|joke|humor => Kenapa ikan tidak bisa main komputer? Karena takut kena jaring.
~halo|hai|selamat pagi|selamat siang|selamat sore|selamat malam => Halo. Ada yang bisa saya bantu?
~terima kasih|makasih|thanks => Sama-sama. Senang bisa membantu.
~apa kabar|gimana kabarmu => Baik, terima kasih. Bagaimana dengan Anda?
`;

export function normalizeText(s: string): string {
  return (s || '').toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();
}

function parseKb(text: string): KbEntry[] {
  const entries: KbEntry[] = [];
  for (const raw of text.split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith('#')) continue;
    const sep = line.indexOf('=>');
    if (sep < 0) continue;
    const shortOnly = line.startsWith('~');
    const keyPart = line.substring(shortOnly ? 1 : 0, sep);
    const answer = line.substring(sep + 2).trim();
    const keys: string[] = [];
    for (const k of keyPart.split('|')) {
      const n = normalizeText(k);
      if (n) keys.push(n);
    }
    if (keys.length > 0 && answer) {
      entries.push({ keys, answer, shortOnly });
    }
  }
  return entries;
}

const kbEntries: KbEntry[] = parseKb(KB_RAW);

function parseWords(t: string[], start: number): [number, number] | null {
  let total = 0;
  let cur = 0;
  let last = 0;
  let any = false;
  let prevUnit = false;
  let j = start;

  while (j < t.length) {
    const w = t[j];
    const u = UNITS[w];
    if (u !== undefined) {
      if (prevUnit) break;
      cur += u;
      last = u;
      any = true;
      prevUnit = true;
      j++;
      continue;
    }
    prevUnit = false;
    if (w === 'seratus') { cur += 100; last = 0; any = true; }
    else if (w === 'seribu') { total += 1000; last = 0; any = true; }
    else if (w === 'sejuta') { total += 1000000; last = 0; any = true; }
    else if (any && w === 'belas' && last >= 1 && last <= 9) { cur += 10; last = 0; }
    else if (any && w === 'puluh' && last >= 1 && last <= 9) { cur += last * 9; last = 0; }
    else if (any && w === 'ratus' && last >= 1 && last <= 9) { cur += last * 99; last = 0; }
    else if (any && w === 'ribu') { total += (cur === 0 ? 1 : cur) * 1000; cur = 0; last = 0; }
    else if (any && w === 'juta') { total += (cur === 0 ? 1 : cur) * 1000000; cur = 0; last = 0; }
    else break;
    j++;
  }

  if (!any) return null;
  return [total + cur, j];
}

function operatorOf(w: string): string | null {
  switch (w) {
    case '+': case 'tambah': case 'plus': case 'ditambah': return '+';
    case '-': case 'kurang': case 'dikurangi': case 'dikurang': case 'minus': return '-';
    case '*': case 'x': case 'kali': case 'dikali': case '×': return '*';
    case '/': case 'bagi': case 'dibagi': case '÷': return '/';
    case '(': return '(';
    case ')': return ')';
    default: return null;
  }
}

class ExpressionParser {
  items: (number | string)[];
  pos = 0;
  err = false;
  divZero = false;

  constructor(items: (number | string)[]) {
    this.items = items;
  }

  private isOp(a: string, b: string): boolean {
    if (this.pos >= this.items.length) return false;
    const o = this.items[this.pos];
    return typeof o === 'string' && (o === a || o === b);
  }

  expr(): number {
    let v = this.term();
    while (!this.err && !this.divZero && this.isOp('+', '-')) {
      const o = this.items[this.pos++] as string;
      const r = this.term();
      v = o === '+' ? v + r : v - r;
    }
    return v;
  }

  term(): number {
    let v = this.factor();
    while (!this.err && !this.divZero && this.isOp('*', '/')) {
      const o = this.items[this.pos++] as string;
      const r = this.factor();
      if (o === '/') {
        if (r === 0) {
          this.divZero = true;
          return 0;
        }
        v /= r;
      } else {
        v *= r;
      }
    }
    return v;
  }

  factor(): number {
    if (this.pos >= this.items.length) {
      this.err = true;
      return 0;
    }
    const o = this.items[this.pos++];
    if (typeof o === 'number') return o;
    if (o === '(') {
      const v = this.expr();
      if (this.pos < this.items.length && this.items[this.pos] === ')') {
        this.pos++;
      } else {
        this.err = true;
      }
      return v;
    }
    this.err = true;
    return 0;
  }
}

export function calculateSpoken(input: string | null | undefined): number | null {
  if (!input) return null;
  let s = input.toLowerCase();
  s = s.replace(/(?<=\d),(?=\d)/g, '.');
  s = s.replace(/[^a-z0-9.+*/×÷()\- ]/g, ' ');
  s = s.replace(/([+*/×÷()\- drop])/g, ' $1 ');

  const tokens: string[] = [];
  for (const raw of s.trim().split(/\s+/)) {
    const y = raw.replace(/^\.+|\.+$/g, '');
    if (y) tokens.push(y);
  }

  const items: (number | string)[] = [];
  let sawOp = false;
  let i = 0;

  while (i < tokens.length) {
    const w = tokens[i];
    if (/^\d+(\.\d+)?$/.test(w)) {
      items.push(parseFloat(w));
      i++;
      continue;
    }
    const op = operatorOf(w);
    if (op !== null) {
      items.push(op);
      if (op !== '(' && op !== ')') sawOp = true;
      i++;
      continue;
    }
    const r = parseWords(tokens, i);
    if (r !== null) {
      items.push(r[0]);
      i = r[1];
      continue;
    }
    if (FILLERS.has(w)) {
      i++;
      continue;
    }
    return null;
  }

  if (!sawOp) return null;
  const p = new ExpressionParser(items);
  const v = p.expr();
  if (p.divZero) return NaN;
  if (p.err || p.pos !== items.length) return null;
  return v;
}

export function formatResultNumber(v: number): string {
  if (Number.isInteger(v) && Math.abs(v) < 1e15) {
    return v.toString();
  }
  const s = v.toFixed(4).replace(/\.?0+$/, '');
  return s.replace('.', ',');
}

export function getOfflineAnswer(input: string): string | null {
  if (!input || !input.trim()) return null;

  const calc = calculateSpoken(input);
  if (calc !== null) {
    if (isNaN(calc)) return 'Tidak bisa dibagi dengan nol.';
    return `Hasilnya ${formatResultNumber(calc)}.`;
  }

  const q = ` ${normalizeText(input)} `;
  const words = q.trim().split(/\s+/);
  const wordCount = q.trim() ? words.length : 0;

  let best: KbEntry | null = null;
  let bestScore = 0;

  for (const e of kbEntries) {
    if (e.shortOnly && wordCount > SHORT_MAX_WORDS) continue;
    let score = 0;
    for (const k of e.keys) {
      if (q.includes(` ${k} `)) {
        score = Math.max(score, k.split(/\s+/).length);
      }
    }
    if (score > bestScore) {
      bestScore = score;
      best = e;
    }
  }

  return best ? best.answer : null;
}

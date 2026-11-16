import fs from 'node:fs';
import path from 'node:path';
import type { Plugin } from 'vite';

/**
 * Serves PDF.js runtime assets under /pdfjs/ (dev) and copies them into the build (prod):
 *  - wasm/            JBIG2 + JPEG 2000 image decoders (common in scanned books) and colour management
 *  - cmaps/           character maps for CJK and other CID-keyed fonts
 *  - standard_fonts/  metrics/data for PDFs that do not embed the standard 14 fonts
 *  - iccs/            ICC colour profiles
 * Without these, PDF.js 6 renders such images/fonts blank ("JBig2 failed to initialize").
 * quickjs-eval (the JavaScript engine for PDF form scripting) is deliberately NOT shipped: the
 * reader never enables PDF scripting, so it would only add attack surface.
 */
const ROOT = path.resolve('node_modules/pdfjs-dist');
const DIRS = ['wasm', 'cmaps', 'standard_fonts', 'iccs'];
const EXCLUDE = /^quickjs/;

const TYPES: Record<string, string> = {
  '.wasm': 'application/wasm',
  '.js': 'text/javascript',
  '.bcmap': 'application/octet-stream',
  '.pfb': 'application/octet-stream',
  '.ttf': 'font/ttf',
  '.icc': 'application/vnd.iccprofile',
};

function files(): { dir: string; name: string; abs: string }[] {
  return DIRS.flatMap((dir) =>
    fs
      .readdirSync(path.join(ROOT, dir))
      .filter((name) => !EXCLUDE.test(name) && fs.statSync(path.join(ROOT, dir, name)).isFile())
      .map((name) => ({ dir, name, abs: path.join(ROOT, dir, name) })),
  );
}

export default function pdfjsAssets(): Plugin {
  return {
    name: 'pdfjs-assets',
    configureServer(server) {
      const allowed = new Map(files().map((f) => [`/${f.dir}/${f.name}`, f.abs]));
      server.middlewares.use('/pdfjs', (req, res, next) => {
        // Exact allow-list lookup: no path joining with user input, so no traversal.
        const abs = allowed.get(decodeURIComponent((req.url ?? '').split('?')[0]!));
        if (!abs) return next();
        res.setHeader('Content-Type', TYPES[path.extname(abs)] ?? 'application/octet-stream');
        fs.createReadStream(abs).pipe(res);
      });
    },
    generateBundle() {
      for (const f of files()) {
        this.emitFile({ type: 'asset', fileName: `pdfjs/${f.dir}/${f.name}`, source: fs.readFileSync(f.abs) });
      }
    },
  };
}

const { app, BrowserWindow } = require('electron');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '../..');
const output = path.join(root, 'image-validation');
fs.mkdirSync(output, { recursive: true });
app.setPath('userData', path.join(output, 'electron-profile'));
app.whenReady().then(async () => {
  const window = new BrowserWindow({ show: false, width: 1000, height: 800, webPreferences: { sandbox: true } });
  try {
    const results = [];
    for (const project of [path.resolve(root, '../manager'), root]) {
      const frontend = path.join(project, 'frontend');
      const esbuild = require(path.join(frontend, 'node_modules/esbuild'));
      const bundle = esbuild.buildSync({
        stdin: { contents: `export { montarPlayers } from './src/features/doc/DocContent'; export { compressImageToBase64 } from './src/services/imageService'; export { default as ResizableImage } from './src/components/RichTextEditor/extensions/ResizableImage'; export { Editor } from '@tiptap/core'; export { default as StarterKit } from '@tiptap/starter-kit';`, resolveDir: frontend, loader: 'tsx' },
        bundle: true, format: 'iife', globalName: 'ImageTest', write: false,
      }).outputFiles[0].text;
      await window.loadURL('about:blank');
      await window.webContents.executeJavaScript(bundle);
      const result = await window.webContents.executeJavaScript(`(async () => {
        const check = (condition, message) => { if (!condition) throw Error(message); };
        const canvas = document.createElement('canvas'); canvas.width = 240; canvas.height = 120;
        const ctx = canvas.getContext('2d'); ctx.fillStyle = '#2563eb'; ctx.fillRect(0, 0, 240, 120);
        ctx.fillStyle = '#fff'; ctx.font = '24px sans-serif'; ctx.fillText('QA Manager', 20, 65);
        const webp = canvas.toDataURL('image/webp', 0.8);
        const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/webp'));
        const png = await ImageTest.compressImageToBase64(new File([blob], 'example.webp', {type:'image/webp'}));
        check(png.startsWith('data:image/png;base64,'), 'Upload must produce PNG');
        const old = '<div data-resizable-image data-src="' + webp + '" data-width="240" data-align="right"></div>';
        const restored = ImageTest.montarPlayers(old);
        document.body.innerHTML = restored;
        await document.querySelector('img').decode();
        check(document.querySelector('img').naturalWidth === 240, 'Legacy WebP must render');
        check(document.querySelector('div').style.textAlign === 'right', 'Alignment must survive');
        const editor = new ImageTest.Editor({extensions:[ImageTest.StarterKit, ImageTest.ResizableImage],content:old});
        const html = editor.getHTML();
        check(new DOMParser().parseFromString(html,'text/html').querySelectorAll('img').length === 1, 'Saved HTML needs a real image');
        editor.commands.setContent(html);
        check(editor.getJSON().content.filter(node=>node.type==='resizableImage').length === 1, 'Reopening must not duplicate images');
        check(editor.getJSON().content.find(node=>node.type==='resizableImage').attrs.src === webp, 'Source must survive reopening');
        const preview = ImageTest.montarPlayers(html);
        check(new DOMParser().parseFromString(preview,'text/html').querySelectorAll('img').length === 1, 'Reader must not duplicate images');
        editor.destroy();
        document.body.innerHTML = '<h1>Imagens de documentação</h1><p>Imagem antiga em WebP, recuperada no leitor:</p>' + restored + '<p>Nova imagem em PNG:</p><img src="' + png + '">';
        await Promise.all([...document.images].map(img=>img.decode()));
        return { passed: true, webp, png };
      })()`);
      fs.writeFileSync(path.join(output, 'legacy.webp'), Buffer.from(result.webp.split(',')[1], 'base64'));
      fs.writeFileSync(path.join(output, 'preview-' + path.basename(project) + '.png'), (await window.webContents.capturePage()).toPNG());
      results.push({ project, passed: result.passed });
    }
    fs.writeFileSync(path.join(output, 'frontend-results.json'), JSON.stringify(results, null, 2));
    app.exit(0);
  } catch (error) {
    fs.writeFileSync(path.join(output, 'frontend-error.txt'), error.stack);
    app.exit(1);
  }
});

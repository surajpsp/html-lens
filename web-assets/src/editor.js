/*
 * HTML Lens code editor (CodeMirror 6). Embedded by the desktop app (CEF) and the web app (iframe).
 * Host → editor: {op:'init'|'set'|'load'|'options'|'reveal'|'focus'|'insert'}
 * Editor → host: {type:'ready'|'change'|'save'|'run'|'cursor'}
 */
import { EditorView, keymap, lineNumbers, highlightActiveLine, highlightActiveLineGutter, drawSelection,
  dropCursor, rectangularSelection, crosshairCursor, highlightSpecialChars } from '@codemirror/view';
import { EditorState, Compartment } from '@codemirror/state';
import { defaultKeymap, history, historyKeymap, indentWithTab } from '@codemirror/commands';
import { searchKeymap, highlightSelectionMatches, search } from '@codemirror/search';
import { autocompletion, completionKeymap, closeBrackets, closeBracketsKeymap } from '@codemirror/autocomplete';
import { foldGutter, indentOnInput, bracketMatching, foldKeymap, syntaxHighlighting, HighlightStyle, indentUnit } from '@codemirror/language';
import { html } from '@codemirror/lang-html';
import { css } from '@codemirror/lang-css';
import { javascript } from '@codemirror/lang-javascript';
import { tags as t } from '@lezer/highlight';
import { abbreviationTracker, expandAbbreviation } from '@emmetio/codemirror6-plugin';

const params = new URLSearchParams(location.search);
const T = params.get('t') || 'web';
const KEY = params.get('k') || '';

function post(msg) {
  msg.__hved = 1;
  try {
    if (T === 'http') {
      fetch('/__hv/ed/' + KEY, { method: 'POST', body: JSON.stringify(msg), headers: { 'Content-Type': 'text/plain' } }).catch(() => {});
    } else if (T === 'cef') {
      if (window.cefQuery) window.cefQuery({ request: JSON.stringify(msg), persistent: false, onSuccess() {}, onFailure() {} });
    } else if (window.parent !== window) {
      window.parent.postMessage(msg, location.origin);
    }
  } catch (e) { /* ignore */ }
}

const palettes = {
  dark: { bg: '#14141c', fg: '#e6e6ef', gutter: '#14141c', gutterFg: '#5c5c70', active: '#1d1d29', sel: '#3a3360', cursor: '#a594ff',
    tag: '#ff7aa8', attr: '#ffcb6b', str: '#a6e3a1', kw: '#c3a6ff', num: '#fab387', com: '#6c7086', prop: '#89dceb', fn: '#89b4fa', op: '#94e2d5', meta: '#f9e2af' },
  light: { bg: '#ffffff', fg: '#1f2330', gutter: '#ffffff', gutterFg: '#a0a3b1', active: '#f5f4fb', sel: '#d9d2ff', cursor: '#6246ea',
    tag: '#c2185b', attr: '#b26a00', str: '#2e7d32', kw: '#6a1b9a', num: '#d84315', com: '#8e8ea0', prop: '#00838f', fn: '#1565c0', op: '#00695c', meta: '#795548' },
};

function themeFor(name, fontSize) {
  const p = palettes[name] || palettes.dark;
  const dark = name !== 'light';
  return [
    EditorView.theme({
      '&': { color: p.fg, backgroundColor: p.bg, height: '100%', fontSize: fontSize + 'px' },
      '.cm-scroller': { fontFamily: '"JetBrains Mono", "SF Mono", Menlo, Consolas, "Liberation Mono", monospace', lineHeight: '1.6' },
      '.cm-content': { caretColor: p.cursor, padding: '10px 0' },
      '.cm-cursor, .cm-dropCursor': { borderLeftColor: p.cursor, borderLeftWidth: '2px' },
      '&.cm-focused > .cm-scroller > .cm-selectionLayer .cm-selectionBackground, .cm-selectionBackground, .cm-content ::selection': { backgroundColor: p.sel },
      '.cm-gutters': { backgroundColor: p.gutter, color: p.gutterFg, border: 'none', paddingRight: '6px' },
      '.cm-activeLine': { backgroundColor: p.active },
      '.cm-activeLineGutter': { backgroundColor: p.active, color: p.fg },
      '.cm-foldPlaceholder': { backgroundColor: 'transparent', border: 'none', color: p.gutterFg },
      '.cm-tooltip': { border: 'none', backgroundColor: dark ? '#22222f' : '#ffffff', boxShadow: '0 8px 24px rgba(0,0,0,.25)', borderRadius: '8px', overflow: 'hidden' },
      '.cm-tooltip-autocomplete > ul > li[aria-selected]': { backgroundColor: p.sel, color: p.fg },
      '.cm-panels': { backgroundColor: dark ? '#1b1b26' : '#f6f5fb', color: p.fg },
      '.cm-panels.cm-panels-top': { borderBottom: '1px solid ' + (dark ? '#2a2a3a' : '#e4e2ef') },
      '.cm-search input, .cm-search button': { borderRadius: '6px' },
      '.cm-textfield': { backgroundColor: dark ? '#11111a' : '#fff', border: '1px solid ' + (dark ? '#33334a' : '#d5d2e6'), color: p.fg },
      '.cm-button': { backgroundImage: 'none', backgroundColor: dark ? '#2a2a3a' : '#ebe8f7', border: 'none', color: p.fg },
      '.cm-searchMatch': { backgroundColor: dark ? '#5a4a1f' : '#ffe58f' },
      '.cm-searchMatch.cm-searchMatch-selected': { backgroundColor: dark ? '#8a6d1f' : '#ffc53d' },
      '.cm-selectionMatch': { backgroundColor: dark ? '#2c2c44' : '#ece8ff' },
      '.cm-matchingBracket': { backgroundColor: dark ? '#3a3360' : '#e2dcff', outline: 'none' },
      '.emmet-preview': { backgroundColor: dark ? '#22222f' : '#fff' },
    }, { dark }),
    syntaxHighlighting(HighlightStyle.define([
      { tag: [t.tagName, t.angleBracket], color: p.tag },
      { tag: [t.attributeName], color: p.attr },
      { tag: [t.attributeValue, t.string, t.special(t.string)], color: p.str },
      { tag: [t.keyword, t.controlKeyword, t.moduleKeyword, t.operatorKeyword, t.definitionKeyword], color: p.kw },
      { tag: [t.number, t.bool, t.null, t.atom, t.unit], color: p.num },
      { tag: [t.comment, t.lineComment, t.blockComment], color: p.com, fontStyle: 'italic' },
      { tag: [t.propertyName, t.className], color: p.prop },
      { tag: [t.function(t.variableName), t.function(t.propertyName)], color: p.fn },
      { tag: [t.operator, t.punctuation], color: p.op },
      { tag: [t.meta, t.documentMeta, t.processingInstruction], color: p.meta },
      { tag: [t.variableName], color: p.fg },
      { tag: t.invalid, color: '#ff5370' },
    ])),
  ];
}

function languageFor(lang) {
  if (lang === 'css') return css();
  if (lang === 'js') return javascript();
  return html({ matchClosingTags: true, autoCloseTags: true });
}

const themeC = new Compartment();
const readOnlyC = new Compartment();
const wrapC = new Compartment();
const langC = new Compartment();
const emmetC = new Compartment();

let suppress = false;
let changeTimer = 0;
let opts = { theme: 'dark', fontSize: 14, readOnly: false, wrap: true, lang: 'html' };

const view = new EditorView({
  parent: document.getElementById('editor'),
  state: EditorState.create({
    doc: '',
    extensions: [
      lineNumbers(), highlightActiveLineGutter(), highlightSpecialChars(), history(), foldGutter(), drawSelection(),
      dropCursor(), EditorState.allowMultipleSelections.of(true), indentOnInput(), bracketMatching(), closeBrackets(),
      autocompletion(), rectangularSelection(), crosshairCursor(), highlightActiveLine(), highlightSelectionMatches(),
      search({ top: true }), indentUnit.of('  '),
      keymap.of([
        { key: 'Mod-s', run: () => { post({ type: 'save' }); return true; } },
        { key: 'Mod-Enter', run: () => { post({ type: 'run' }); return true; } },
        { key: 'Mod-e', run: expandAbbreviation },
        ...closeBracketsKeymap, ...defaultKeymap, ...searchKeymap, ...historyKeymap, ...foldKeymap, ...completionKeymap, indentWithTab,
      ]),
      langC.of(languageFor('html')),
      emmetC.of(abbreviationTracker()),
      themeC.of(themeFor('dark', 14)),
      readOnlyC.of(EditorState.readOnly.of(false)),
      wrapC.of(EditorView.lineWrapping),
      EditorView.updateListener.of((u) => {
        if (u.docChanged && !suppress) {
          clearTimeout(changeTimer);
          changeTimer = setTimeout(() => post({ type: 'change', value: view.state.doc.toString() }), 120);
        }
        if (u.selectionSet) {
          const head = u.state.selection.main.head;
          const line = u.state.doc.lineAt(head);
          post({ type: 'cursor', line: line.number, col: head - line.from + 1, length: u.state.doc.length, lines: u.state.doc.lines });
        }
      }),
    ],
  }),
});

function setValue(value, keepCursor) {
  suppress = true;
  const sel = view.state.selection.main.head;
  view.dispatch({
    changes: { from: 0, to: view.state.doc.length, insert: value || '' },
    selection: keepCursor ? { anchor: Math.min(sel, (value || '').length) } : { anchor: 0 },
  });
  suppress = false;
}

function applyOptions(o) {
  opts = Object.assign(opts, o || {});
  document.documentElement.style.background = (palettes[opts.theme] || palettes.dark).bg;
  view.dispatch({
    effects: [
      themeC.reconfigure(themeFor(opts.theme, opts.fontSize)),
      readOnlyC.reconfigure(EditorState.readOnly.of(!!opts.readOnly)),
      wrapC.reconfigure(opts.wrap ? EditorView.lineWrapping : []),
      langC.reconfigure(languageFor(opts.lang)),
      emmetC.reconfigure(opts.lang === 'html' && !opts.readOnly ? abbreviationTracker() : []),
    ],
  });
}

function handle(c) {
  if (!c || !c.op) return;
  switch (c.op) {
    case 'init': applyOptions(c.options); setValue(c.value, false); break;
    case 'set': setValue(c.value, !!c.keepCursor); break;
    case 'load':
      fetch(c.url).then((r) => r.text()).then((text) => { setValue(text, !!c.keepCursor); if (c.options) applyOptions(c.options); });
      break;
    case 'options': applyOptions(c.options); break;
    case 'focus': view.focus(); break;
    case 'reveal': {
      const line = view.state.doc.line(Math.max(1, Math.min(c.line || 1, view.state.doc.lines)));
      view.dispatch({ selection: { anchor: line.from }, effects: EditorView.scrollIntoView(line.from, { y: 'center' }) });
      view.focus();
      break;
    }
    case 'insert': {
      const { from, to } = view.state.selection.main;
      view.dispatch({ changes: { from, to, insert: c.text || '' }, selection: { anchor: from + (c.text || '').length } });
      view.focus();
      break;
    }
  }
}

window.__hvEditor = { cmd: handle, view };
if (T === 'web') {
  window.addEventListener('message', (e) => {
    if (e.source !== window.parent || e.origin !== location.origin) return;
    if (e.data && e.data.__hvedcmd) handle(e.data);
  });
}
const initial = params.get('theme');
if (initial) applyOptions({ theme: initial });
post({ type: 'ready' });

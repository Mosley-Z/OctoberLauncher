(function () {
  'use strict';
  var root = document.documentElement, session = root.dataset.readerSession;
  var book = null, generation = 0, sheet = 0, row = 0, column = 0, lastQuery = '', lastMatch = null;
  function post(value) { value.session = session; window.ReaderBridge.postMessage(JSON.stringify(value)); }
  function pause() { return new Promise(function (resolve) { setTimeout(resolve, 0); }); }
  function cellKeys(source) {
    return Object.keys(source).filter(function (key) { return key.charAt(0) !== '!'; }).sort(function (a, b) {
      var x = XLSX.utils.decode_cell(a), y = XLSX.utils.decode_cell(b);
      return x.r - y.r || x.c - y.c;
    });
  }
  function bounds(index) {
    var source = book.Sheets[book.SheetNames[index]], range = source['!ref'];
    var end = range ? XLSX.utils.decode_range(range).e : {r: 0, c: 0};
    (source['!merges'] || []).forEach(function (merge) { end.r = Math.max(end.r, merge.e.r); end.c = Math.max(end.c, merge.e.c); });
    return end;
  }
  function text(cell) {
    if (!cell) return '';
    if (cell.f && (cell.v === undefined || cell.v === null)) return '[公式无缓存]';
    return String(cell.w !== undefined ? cell.w : (cell.v === undefined || cell.v === null ? '' : cell.v));
  }
  function table(command, found) {
    var source = book.Sheets[book.SheetNames[sheet]], end = bounds(sheet), cells = [];
    row = Math.max(0, Math.min(end.r, row)); column = Math.max(0, Math.min(end.c, column));
    var merges = (source['!merges'] || []).filter(function (merge) {
      return merge.s.r < row + 20 && merge.e.r >= row && merge.s.c < column + 10 && merge.e.c >= column;
    });
    if (merges.length > 200) throw {code: 'limit'};
    for (var r = row; r <= Math.min(end.r, row + 19); r++) {
      var values = [];
      for (var c = column; c <= Math.min(end.c, column + 9); c++) {
        var anchor = {r: r, c: c};
        merges.some(function (merge) {
          if (r >= merge.s.r && r <= merge.e.r && c >= merge.s.c && c <= merge.e.c) { anchor = merge.s; return true; }
          return false;
        });
        values.push(text(source[XLSX.utils.encode_cell(anchor)]).slice(0, 512));
      }
      cells.push(values);
    }
    var value = {kind: 'table', request: command.request, sheet: sheet,
      sheets: book.SheetNames.map(function (name) { return name.slice(0, 128); }), row: row, column: column,
      totalRows: end.r + 1, totalColumns: end.c + 1, cells: cells, found: found !== false,
      merges: merges.map(function (merge) {
        return {row: merge.s.r, column: merge.s.c, rows: merge.e.r - merge.s.r + 1, columns: merge.e.c - merge.s.c + 1};
      })};
    // Escaped control characters and non-ASCII can make a 200×512 payload exceed the bridge byte budget.
    while (new TextEncoder().encode(JSON.stringify(value)).length > 250000) {
      value.cells = value.cells.map(function (line) { return line.map(function (cell) { return cell.slice(0, cell.length >> 1); }); });
    }
    post(value);
  }
  async function search(command, token) {
    var query = String(command.query || '').slice(0, 256), visited = 0;
    var previous = query === lastQuery ? lastMatch : null;
    var fromSheet = previous ? previous.sheet : 0;
    for (var s = fromSheet; s < book.SheetNames.length; s++) {
      var source = book.Sheets[book.SheetNames[s]], keys = cellKeys(source);
      for (var i = 0; i < keys.length; i++) {
        if (++visited % 2048 === 0) { await pause(); if (token !== generation) return; }
        var at = XLSX.utils.decode_cell(keys[i]);
        if (previous && s === previous.sheet && (at.r < previous.row || at.r === previous.row && at.c <= previous.column)) continue;
        if (query && text(source[keys[i]]).indexOf(query) >= 0) {
          if (token !== generation) return;
          sheet = s; row = at.r; column = at.c; lastQuery = query;
          lastMatch = {sheet: sheet, row: row, column: column}; table(command, true); return;
        }
      }
    }
    if (token === generation) table(command, false);
  }
  async function searchAll(command, token) {
    var query = String(command.query || '').slice(0, 256), offset = Math.max(0, Math.floor(Number(command.searchOffset) || 0));
    var total = 0, hits = [], visited = 0;
    if (query) for (var s = 0; s < book.SheetNames.length; s++) {
      var source = book.Sheets[book.SheetNames[s]], keys = cellKeys(source);
      for (var i = 0; i < keys.length; i++) {
        if (++visited % 2048 === 0) { await pause(); if (token !== generation) return; }
        var value = text(source[keys[i]]), at = value.indexOf(query);
        if (at >= 0) {
          if (total >= offset && hits.length < 64) {
            var cell = XLSX.utils.decode_cell(keys[i]);
            hits.push({sheet: s, row: cell.r, column: cell.c, snippet: value.slice(Math.max(0, at - 32)).slice(0, 160)});
          }
          total++;
        }
      }
    }
    if (token === generation) post({kind: 'searchResults', request: command.request, offset: offset, total: total, hits: hits});
  }
  async function command(value) {
    var token = ++generation;
    if (value.kind === 'cancel') return;
    try {
      if (!book) return;
      if (value.kind === 'searchAll') { await searchAll(value, token); return; }
      sheet = Math.max(0, Math.min(book.SheetNames.length - 1, Number(value.sheet) || 0));
      row = Math.max(0, Number(value.row) || 0); column = Math.max(0, Number(value.column) || 0);
      if (value.kind === 'turn') row += value.delta < 0 ? -20 : 20;
      if (value.kind === 'search') await search(value, token);
      else { lastQuery = ''; lastMatch = null; table(value, true); }
    } catch (error) { if (token === generation) post({kind: 'error', request: value.request, code: error.code || 'corrupt'}); }
  }
  window.ReaderHost = {command: command};
  var initial = JSON.parse(decodeURIComponent(root.dataset.readerCommand));
  fetch('res/source.bin').then(function (response) {
    if (!response.ok) throw {code: 'renderer'};
    return response.arrayBuffer();
  }).then(function (bytes) {
    if (bytes.byteLength > 8388608) throw {code: 'limit'};
    book = XLSX.read(bytes, {type: 'array', cellFormula: true, cellHTML: false, cellStyles: false,
      bookVBA: false, bookFiles: false, cellDates: false, dense: false});
    if (!book.SheetNames.length || book.SheetNames.length > 100) throw {code: 'limit'};
    var count = 0;
    book.SheetNames.forEach(function (name, index) {
      var end = bounds(index);
      if (end.r >= 100000 || end.c >= 1024) throw {code: 'limit'};
      count += Object.keys(book.Sheets[name]).filter(function (key) { return key.charAt(0) !== '!'; }).length;
    });
    if (count > 100000) throw {code: 'limit'};
    command(initial);
  }).catch(function (error) { book = null; post({kind: 'error', request: initial.request, code: error.code || 'corrupt'}); });
})();

import { describe, expect, it } from 'vitest';
import { createCsvBlob } from './csv';

describe('createCsvBlob', () => {
  it('adds a UTF-8 BOM so Excel detects Korean text correctly', async () => {
    const blob = createCsvBlob(['교회', '금액'], [['캡스톤 장로교회', 125]]);
    const bytes = new Uint8Array(await readBlob(blob, 'arrayBuffer') as ArrayBuffer);

    expect(Array.from(bytes.slice(0, 3))).toEqual([0xef, 0xbb, 0xbf]);
    expect(await readBlob(blob, 'text')).toContain('캡스톤 장로교회');
    expect(blob.type).toBe('text/csv;charset=utf-8');
  });
});

function readBlob(blob: Blob, mode: 'arrayBuffer' | 'text') {
  return new Promise<ArrayBuffer | string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(reader.error);
    reader.onload = () => resolve(reader.result as ArrayBuffer | string);
    if (mode === 'arrayBuffer') {
      reader.readAsArrayBuffer(blob);
    } else {
      reader.readAsText(blob, 'utf-8');
    }
  });
}

const UTF8_BOM = '\uFEFF';

export function createCsvBlob(
  headers: string[],
  rows: Array<Array<string | number | undefined>>,
) {
  const escape = (value: string | number | undefined) =>
    `"${String(value ?? '').replaceAll('"', '""')}"`;
  const csv = [headers, ...rows].map((row) => row.map(escape).join(',')).join('\r\n');

  return new Blob([UTF8_BOM, csv], { type: 'text/csv;charset=utf-8' });
}

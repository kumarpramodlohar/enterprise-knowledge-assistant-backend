import { execFile } from "node:child_process";
import { readFile, writeFile, mkdir } from "node:fs/promises";
import { dirname } from "node:path";

const [seedPath, outPath] = process.argv.slice(2);
const collection = process.env.EKA_EVAL_COLLECTION;
if (!seedPath || !outPath || !collection) {
  throw new Error(
    "Usage: EKA_EVAL_COLLECTION=<name> node evaluation/build-dataset.mjs <seed.jsonl> <out.jsonl> (uses PGHOST/PGPORT/PGUSER/PGPASSWORD/PGDATABASE)",
  );
}

const query = `select coalesce(json_agg(json_build_object('id', c.id, 'content', c.content)), '[]'::json)
  from document_chunks c join documents d on d.id = c.document_id join collections col on col.id = d.collection_id
  where col.name = :'collection' and d.status = 'INDEXED' and d.deleted_at is null`;
// psql interpolates :'var' only for stdin or -f input, not -c
const psql = execFile("psql", [
  "-At",
  "-v",
  "ON_ERROR_STOP=1",
  "-v",
  `collection=${collection}`,
]);
let stdout = "";
psql.stdout.on("data", (data) => (stdout += data));
psql.stdin.end(`${query};\n`);
await new Promise((resolve, reject) => {
  psql.on("error", reject);
  psql.on("close", (code) =>
    code === 0 ? resolve() : reject(new Error(`psql exited with ${code}`)),
  );
});
const chunks = JSON.parse(stdout.trim()).map((c) => ({
  id: c.id,
  text: normalize(c.content),
}));
if (!chunks.length)
  throw new Error("No indexed chunks found for the collection.");

function normalize(value) {
  return value.normalize("NFKC").replace(/\s+/g, " ").toLowerCase();
}

const rows = [];
for (const [index, line] of (await readFile(seedPath, "utf8"))
  .split(/\r?\n/)
  .entries()) {
  if (!line.trim()) continue;
  const seed = JSON.parse(line);
  const ids = new Set();
  for (const phrase of seed.evidence ?? []) {
    const matches = chunks.filter((chunk) =>
      chunk.text.includes(normalize(phrase)),
    );
    if (!matches.length)
      throw new Error(
        `Seed row ${index + 1} (${seed.id}): evidence phrase matches no chunk.`,
      );
    matches.forEach((chunk) => ids.add(chunk.id));
  }
  if (seed.answerable && !ids.size)
    throw new Error(
      `Seed row ${index + 1} (${seed.id}): answerable question has no evidence.`,
    );
  const { evidence, ...row } = seed;
  rows.push({ ...row, relevantChunkIds: [...ids] });
}

await mkdir(dirname(outPath), { recursive: true });
await writeFile(
  outPath,
  rows.map((row) => JSON.stringify(row)).join("\n") + "\n",
);
console.log(
  `Wrote ${rows.length} cases (${rows.filter((r) => r.answerable).length} answerable) to ${outPath}`,
);

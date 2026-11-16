import { readFile } from "node:fs/promises";
import { writeApprovalRecord } from "./lib/approval-record.mjs";

const [reportPath, approvalPath] = process.argv.slice(2);
if (!reportPath || !approvalPath) {
  console.error(
    "Usage: node eka_eval_suite/record-approval.mjs <report.json> <approval.json>",
  );
  process.exitCode = 2;
} else {
  try {
    const approval = JSON.parse(await readFile(approvalPath, "utf8"));
    const outputPath = await writeApprovalRecord(reportPath, approval);
    console.log(`Immutable approval record written: ${outputPath}`);
  } catch {
    console.error(
      "Approval record was not written; validate the decision fields and report reference.",
    );
    process.exitCode = 1;
  }
}

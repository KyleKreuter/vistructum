## Training 20260928-073734-cd20373c

Commit `cd20373caf4caef2a10c9272fc4cea0efa5a7dc5`, exit codes `{"configs/scan-v2.yaml": 0}`

| Model | Split | Verdict | Threshold | Precision | Recall | FP rate | Failures |
|---|---|---|---|---|---|---|---|
| scan-v2 | test | FAIL | 0.96 | 0.9964 | 0.5584 | 0.0005 | recall 0.5584 < 0.6 |
| scan-v2 | holdout | FAIL | 0.96 | 0.9918 | 0.5324 | 0.0011 | fp_rate 0.00110 > 0.001; recall 0.5324 < 0.6 |
| scan-v2 | findings | FAIL | 0.96 | 1 | 0.125 | 0 | negatives 29 < 5000; recall 0.1250 < 0.6 |

| Run | Scan split | Verdict | Threshold | Votes | Recall | False flags | Failures |
|---|---|---|---|---|---|---|---|
| scan-v2-cd20373c | calib | calibrated | 0.98 | 1 | 0.3644 | 1/243000 |  |
| scan-v2-cd20373c | scan | FAIL | 0.98 | 1 | 0.3283 | 2/243000 | recall 0.3283 < 0.5 |
| scan-v2-cd20373c | scan-holdout | FAIL | 0.98 | 1 | 0.2773 | 1/243000 | recall 0.2773 < 0.5 |

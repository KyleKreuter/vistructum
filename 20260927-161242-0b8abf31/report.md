## Training 20260927-161242-0b8abf31

Commit `0b8abf312064006e6967c6a3e7cb3c594166e0bf`, exit codes `{"configs/scan-v1.yaml": 0}`

| Model | Split | Verdict | Threshold | Precision | Recall | FP rate | Failures |
|---|---|---|---|---|---|---|---|
| scan-v1 | test | FAIL | 0.98 | 0.9958 | 0.284 | 0.0003 | recall 0.2840 < 0.6 |
| scan-v1 | holdout | FAIL | 0.98 | 1 | 0.2544 | 0 | recall 0.2544 < 0.6 |

| Run | Scan split | Verdict | Threshold | Votes | Recall | False flags | Failures |
|---|---|---|---|---|---|---|---|
| scan-v1-0b8abf31 | calib | calibrated | 0.95 | 3 | 0.3243 | 4/243000 |  |
| scan-v1-0b8abf31 | scan | FAIL | 0.95 | 3 | 0.3243 | 7/243000 | recall 0.3243 < 0.5 |
| scan-v1-0b8abf31 | scan-holdout | FAIL | 0.95 | 3 | 0.3003 | 6/243000 | recall 0.3003 < 0.5 |

## Training 20260928-213133-58926db1

Commit `58926db179af12bdb9ffa5dc5a89e60a235007ab`, exit codes `{"configs/scan-v4.yaml": 0}`

| Model | Split | Verdict | Threshold | Precision | Recall | FP rate | Failures |
|---|---|---|---|---|---|---|---|
| scan-v4 | test | PASS | 0.97 | 0.9978 | 0.7424 | 0.0004 |  |
| scan-v4 | holdout | PASS | 0.97 | 0.9994 | 0.6536 | 0.0001 |  |
| scan-v4 | findings | FAIL | 0.97 | 1 | 0.5 | 0 | negatives 29 < 5000; recall 0.5000 < 0.6 |

| Run | Scan split | Verdict | Threshold | Votes | Recall | False flags | Failures |
|---|---|---|---|---|---|---|---|
| scan-v4-58926db1 | calib | calibrated | 0.97 | 1 | 0.7938 | 2/243000 |  |
| scan-v4-58926db1 | scan | PASS | 0.97 | 1 | 0.7728 | 5/243000 |  |
| scan-v4-58926db1 | scan-holdout | PASS | 0.97 | 1 | 0.7377 | 6/243000 |  |

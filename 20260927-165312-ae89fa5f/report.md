## Training 20260927-165312-ae89fa5f

Commit `ae89fa5f7d3f187ed0aa65e285d2dd71a2fce754`, exit codes `{"configs/scan-v1.yaml": 0}`

| Model | Split | Verdict | Threshold | Precision | Recall | FP rate | Failures |
|---|---|---|---|---|---|---|---|
| scan-v1 | test | FAIL | 0.97 | 0.9982 | 0.4504 | 0.0002 | recall 0.4504 < 0.6 |
| scan-v1 | holdout | FAIL | 0.97 | 0.999 | 0.4084 | 0.0001 | recall 0.4084 < 0.6 |

| Run | Scan split | Verdict | Threshold | Votes | Recall | False flags | Failures |
|---|---|---|---|---|---|---|---|
| scan-v1-ae89fa5f | calib | calibrated | 0.99 | 1 | 0.1852 | 3/243000 |  |
| scan-v1-ae89fa5f | scan | FAIL | 0.99 | 1 | 0.1772 | 2/243000 | recall 0.1772 < 0.5 |
| scan-v1-ae89fa5f | scan-holdout | FAIL | 0.99 | 1 | 0.1762 | 1/243000 | recall 0.1762 < 0.5 |

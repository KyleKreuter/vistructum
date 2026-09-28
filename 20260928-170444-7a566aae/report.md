## Training 20260928-170444-7a566aae

Commit `7a566aaedd36c2eb6215ff4eff7123666c890929`, exit codes `{"configs/scan-v3.yaml": 0}`

| Model | Split | Verdict | Threshold | Precision | Recall | FP rate | Failures |
|---|---|---|---|---|---|---|---|
| scan-v3 | test | FAIL | 0.97 | 0.9903 | 0.4916 | 0.0012 | fp_rate 0.00120 > 0.001; recall 0.4916 < 0.6 |
| scan-v3 | holdout | FAIL | 0.97 | 0.9992 | 0.4708 | 0.0001 | recall 0.4708 < 0.6 |
| scan-v3 | findings | FAIL | 0.97 | 1 | 0.5 | 0 | negatives 29 < 5000; recall 0.5000 < 0.6 |

| Run | Scan split | Verdict | Threshold | Votes | Recall | False flags | Failures |
|---|---|---|---|---|---|---|---|
| scan-v3-7a566aae | calib | calibrated | 0.97 | 1 | 0.5005 | 4/243000 |  |
| scan-v3-7a566aae | scan | FAIL | 0.97 | 1 | 0.4795 | 8/243000 | false flags/window 3.29e-05 > 3e-05; recall 0.4795 < 0.5 |
| scan-v3-7a566aae | scan-holdout | FAIL | 0.97 | 1 | 0.5055 | 9/243000 | false flags/window 3.70e-05 > 3e-05 |

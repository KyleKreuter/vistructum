## Training 20260927-225137-13d951fd

Commit `13d951fd688abdbbde7bdf27e530af7544241f88`, exit codes `{"configs/scan-v2.yaml": 0}`

| Model | Split | Verdict | Threshold | Precision | Recall | FP rate | Failures |
|---|---|---|---|---|---|---|---|
| scan-v2 | test | PASS | 0.97 | 0.9981 | 0.6472 | 0.0003 |  |
| scan-v2 | holdout | FAIL | 0.97 | 0.9979 | 0.5736 | 0.0003 | recall 0.5736 < 0.6 |
| scan-v2 | findings | FAIL | 0.97 | 0.8333 | 0.625 | 0.03448 | negatives 29 < 5000; precision 0.8333 < 0.9; fp_rate 0.03448 > 0.001 |

| Run | Scan split | Verdict | Threshold | Votes | Recall | False flags | Failures |
|---|---|---|---|---|---|---|---|
| scan-v2-13d951fd | calib | calibrated | 0.95 | 3 | 0.4815 | 4/243000 |  |
| scan-v2-13d951fd | scan | FAIL | 0.95 | 3 | 0.4725 | 14/243000 | false flags/window 5.76e-05 > 3e-05; recall 0.4725 < 0.5 |
| scan-v2-13d951fd | scan-holdout | FAIL | 0.95 | 3 | 0.4505 | 11/243000 | false flags/window 4.53e-05 > 3e-05; recall 0.4505 < 0.5 |

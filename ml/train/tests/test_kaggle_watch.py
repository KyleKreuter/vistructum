import subprocess

import kaggle_watch


def _answer(monkeypatch, text):
    monkeypatch.setattr(kaggle_watch, "kaggle", lambda *args: subprocess.CompletedProcess(args, 0, text, ""))
    return kaggle_watch.status("kaggle", "owner/kernel")[0]


def test_status_reads_the_kernel_state(monkeypatch):
    text = 'owner/kernel has status "KernelWorkerStatus.COMPLETE"'
    assert _answer(monkeypatch, text) == "complete"


def test_api_errors_are_not_mistaken_for_a_failed_kernel(monkeypatch):
    text = "503 Server Error: Service Unavailable for url: https://api.kaggle.com/v1/kernels"
    assert _answer(monkeypatch, text) == "unknown"

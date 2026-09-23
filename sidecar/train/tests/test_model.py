import torch
from model import ExportNet, SymbolNet

from vistructum_ml.contract import GRID, LABELS


def test_forward_shape_mask():
    model = SymbolNet("mask", [8, 16], 0.0)
    x = torch.randint(0, 2, (4, 1, GRID, GRID), dtype=torch.uint8)
    out = model(x)
    assert out.shape == (4, len(LABELS))


def test_forward_shape_fullscan():
    model = SymbolNet("fullscan", [8, 16, 32], 0.1)
    x = torch.randint(0, 256, (3, 3, GRID, GRID), dtype=torch.uint8)
    out = model(x)
    assert out.shape == (3, len(LABELS))


def test_norm_buffer_not_trainable():
    model = SymbolNet("mask", [8], 0.0)
    param_names = dict(model.named_parameters())
    assert "norm.scale" not in param_names
    assert "norm.offset" not in param_names
    buffer_names = dict(model.named_buffers())
    assert "norm.scale" in buffer_names
    assert "norm.offset" in buffer_names


def test_single_width_has_no_pool():
    model = SymbolNet("mask", [8], 0.0)
    x = torch.zeros(1, 1, GRID, GRID, dtype=torch.uint8)
    with torch.no_grad():
        feats = model.blocks(model.norm(x))
    assert feats.shape[-2:] == (GRID, GRID)


def test_export_net_returns_probabilities():
    model = SymbolNet("mask", [8, 16], 0.0)
    model.eval()
    x = torch.randint(0, 2, (5, 1, GRID, GRID), dtype=torch.uint8)
    with torch.no_grad():
        probs = ExportNet(model)(x)
    assert probs.shape == (5, len(LABELS))
    assert torch.allclose(probs.sum(dim=1), torch.ones(5), atol=1e-5)
    assert (probs >= 0).all() and (probs <= 1).all()


def test_d4_augment_is_a_permutation_of_d4_views():
    from data import d4_augment

    x = torch.randint(0, 255, (64, 3, 64, 64), dtype=torch.uint8)
    out = d4_augment(x, torch.Generator().manual_seed(0))
    assert out.shape == x.shape and out.dtype == x.dtype
    for i in range(len(x)):
        views = [torch.rot90(x[i], k, dims=(1, 2)) for k in range(4)]
        views += [torch.flip(v, dims=(2,)) for v in views]
        assert any(torch.equal(out[i], v) for v in views)
    assert not torch.equal(out, x)

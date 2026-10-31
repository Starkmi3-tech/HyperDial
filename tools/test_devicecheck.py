from PIL import Image

from devicecheck import dp_to_px, parse_node, pixel_matches


def _solid_png(tmp_path, hex_color):
    path = tmp_path / "solid.png"
    Image.new("RGB", (4, 4), hex_color).save(path)
    return str(path)


def test_pixel_matches_exact(tmp_path):
    png = _solid_png(tmp_path, "#21C063")
    assert pixel_matches(png, 1, 1, "#21C063")


def test_pixel_mismatch_beyond_tol(tmp_path):
    png = _solid_png(tmp_path, "#21C063")
    assert not pixel_matches(png, 1, 1, "#21C073", tol=8)


def test_parse_node_bounds_text():
    xml = (
        '<hierarchy><node resource-id="app.hyperdial.debug:id/main_large_title" '
        'text="Contacts" bounds="[0,120][1220,220]"/></hierarchy>'
    )
    assert parse_node(xml, "main_large_title") == {"text": "Contacts", "bounds": (0, 120, 1220, 220)}


def test_parse_node_missing_returns_none():
    assert parse_node("<hierarchy/>", "nope") is None


def test_dp_to_px():
    assert dp_to_px(76, 480) == 228

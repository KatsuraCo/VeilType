# Contributing

## Goal

Contributions should improve auditability, interoperability, and correctness of
the public core.

Good contribution areas:
- clarifying protocol docs
- improving schema precision
- adding deterministic vectors
- fixing reference implementation bugs
- adding compatibility tests

## Ground rules

- Keep changes narrow and reviewable.
- Preserve wire-format compatibility unless a change is explicitly marked as a
  format revision.
- Update vectors and tests when changing protocol behavior.
- Avoid adding product-shell concerns to this repository.

## Development

Install in editable mode:

```bash
python -m pip install -e .
```

Run tests:

```bash
python -m unittest discover -s tests -v
python scripts/verify_vectors.py
```

## Pull requests

A good pull request should include:
- what changed
- why it changed
- compatibility impact
- updated tests or vectors when applicable


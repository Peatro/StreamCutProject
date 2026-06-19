"""Unit tests for the offline evaluation harness (TASK-087).

Covers a tiny hand-crafted fixture with exactly:
  - 1 hit   (label center inside a candidate)
  - 1 miss  (label center outside all candidates)
  - 1 false positive (candidate matching no label)
"""

from pathlib import Path
import sys
import unittest

# Ensure the eval package is importable from the worker root.
_WORKER_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(_WORKER_ROOT))
sys.path.insert(0, str(_WORKER_ROOT / "src"))

from eval.harness import Candidate, Label, evaluate


class EvalHarnessTests(unittest.TestCase):
    def test_hit_miss_false_positive(self) -> None:
        """1 hit, 1 miss, 1 false positive -- metrics must be deterministic."""
        labels = [
            # Label A: center at 15.0s -- should be HIT by candidate 1 (10-40s)
            Label(source="test", start_sec=10.0, end_sec=20.0, note="label A"),
            # Label B: center at 105.0s -- no candidate covers this -> MISS
            Label(source="test", start_sec=100.0, end_sec=110.0, note="label B"),
        ]
        candidates = [
            # Candidate 1: covers label A's center (15.0)
            Candidate(start_sec=10.0, end_sec=40.0, score=0.8),
            # Candidate 2: covers nothing labeled -> FALSE POSITIVE
            Candidate(start_sec=200.0, end_sec=230.0, score=0.6),
        ]

        result = evaluate(labels, candidates, tolerance_sec=0.0)

        # Hit-rate: 1 of 2 labels hit = 0.5
        self.assertAlmostEqual(result.hit_rate, 0.5)
        self.assertEqual(result.hits, 1)
        self.assertEqual(result.misses, 1)
        self.assertEqual(result.false_positives, 1)
        self.assertEqual(result.total_labels, 2)
        self.assertEqual(result.total_candidates, 2)

        # Per-label breakdown
        self.assertTrue(result.per_label[0].hit)   # label A = HIT
        self.assertFalse(result.per_label[1].hit)   # label B = MISS

    def test_tolerance_converts_miss_to_hit(self) -> None:
        """Adding tolerance widens the candidate range enough to catch a near-miss."""
        labels = [
            # Center at 50.0s; candidate ends at 45.0s -> miss at tolerance=0
            Label(source="test", start_sec=45.0, end_sec=55.0, note="near miss"),
        ]
        candidates = [
            Candidate(start_sec=10.0, end_sec=45.0, score=0.7),
        ]

        result_strict = evaluate(labels, candidates, tolerance_sec=0.0)
        self.assertEqual(result_strict.hits, 0)

        result_tolerant = evaluate(labels, candidates, tolerance_sec=5.0)
        self.assertEqual(result_tolerant.hits, 1)

    def test_empty_inputs(self) -> None:
        """No labels and no candidates should produce zero metrics."""
        result = evaluate([], [], tolerance_sec=0.0)
        self.assertAlmostEqual(result.hit_rate, 0.0)
        self.assertEqual(result.hits, 0)
        self.assertEqual(result.false_positives, 0)


if __name__ == "__main__":
    unittest.main()

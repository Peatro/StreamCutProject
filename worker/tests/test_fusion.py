import unittest

from streamcut_worker.analysis.fusion import (
    _ranks,
    audio_loudness_scores,
    chat_density_scores,
    chat_weight,
    fuse,
)


class RanksTests(unittest.TestCase):
    def test_best_score_gets_rank_one(self):
        # higher score -> better (lower) rank
        self.assertEqual(_ranks([10, 5, 6, 1]), [1.0, 3.0, 2.0, 4.0])

    def test_ties_share_average_rank(self):
        self.assertEqual(_ranks([5, 5, 1]), [1.5, 1.5, 3.0])
        self.assertEqual(_ranks([1, 7, 7, 7]), [4.0, 2.0, 2.0, 2.0])


class FuseTests(unittest.TestCase):
    def _three_axes(self):
        # cand0 is BEST in text but WORST in audio+chat (the anti-veto case);
        # cand3 is consistently near-worst. Equal weights.
        return {
            "text":  [10, 5, 6, 1],   # ranks 1,3,2,4
            "audio": [1, 5, 6, 4],    # ranks 4,2,1,3
            "chat":  [1, 5, 6, 4],    # ranks 4,2,1,3
        }, {"text": 1.0, "audio": 1.0, "chat": 1.0}

    def test_additive_order_matches_hand_computation(self):
        scorers, w = self._three_axes()
        # fused rank-sums: c0=9, c1=7, c2=4, c3=10 -> ascending [2,1,0,3]
        self.assertEqual(fuse(scorers, w), [2, 1, 0, 3])

    def test_strong_in_one_axis_is_not_vetoed(self):
        # cand0 (best-in-text, worst elsewhere) must beat cand3 (bad everywhere).
        # Multiplication-as-veto would crush a one-axis champion; addition keeps it.
        scorers, w = self._three_axes()
        order = fuse(scorers, w)
        self.assertLess(order.index(0), order.index(3))

    def test_rank_all_returns_full_pool(self):
        scorers, w = self._three_axes()
        order = fuse(scorers, w)
        self.assertEqual(sorted(order), [0, 1, 2, 3])  # permutation, nothing cut

    def test_weight_shifts_priority(self):
        # Heavier text weight must move the text champion (cand0) EARLIER than it
        # sits under equal weights (it need not reach #1: cand2 is #1 on the other
        # two axes, so addition still rewards its broad support).
        scorers, w = self._three_axes()
        equal = fuse(scorers, w)
        heavy_text = fuse(scorers, {"text": 5.0, "audio": 1.0, "chat": 1.0})
        self.assertLess(heavy_text.index(0), equal.index(0))

    def test_missing_axis_is_dropped_not_zero_filled(self):
        # chat absent -> fuse on text+audio only, still all candidates returned
        scorers, w = self._three_axes()
        del scorers["chat"]
        order = fuse(scorers, w)
        self.assertEqual(sorted(order), [0, 1, 2, 3])

    def test_unequal_lengths_rejected(self):
        with self.assertRaises(ValueError):
            fuse({"a": [1, 2], "b": [1, 2, 3]}, {"a": 1, "b": 1})


class ChatWeightTests(unittest.TestCase):
    def test_scales_linearly_then_caps(self):
        self.assertEqual(chat_weight(0, 500), 0.0)
        self.assertEqual(chat_weight(250, 500), 0.5)
        self.assertEqual(chat_weight(500, 500), 1.0)
        self.assertEqual(chat_weight(1000, 500), 1.0)  # capped


class ScorerTests(unittest.TestCase):
    def test_chat_density_counts_within_window(self):
        offs = [10, 20, 25, 200]
        # center 22, +-15 -> window [7, 37] -> 10, 20, 25 = 3
        self.assertEqual(chat_density_scores([22], offs, half_window_sec=15), [3.0])
        self.assertEqual(chat_density_scores([200], offs, half_window_sec=15), [1.0])

    def test_audio_mean_ignores_silence_floor(self):
        t = [100, 101, 102, 103]
        db = [-100, -20, -10, -100]  # two real samples, two silence
        # center 101.5 +-2 -> all four in window; mean of non-silence (-20,-10) = -15
        self.assertEqual(audio_loudness_scores([101.5], t, db, half_window_sec=2), [-15.0])

    def test_audio_all_silence_returns_floor(self):
        self.assertEqual(
            audio_loudness_scores([50], [49, 50, 51], [-100, -100, -100], half_window_sec=2),
            [-99.0],
        )


if __name__ == "__main__":
    unittest.main()

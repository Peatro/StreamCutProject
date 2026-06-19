# Ground-Truth Labels

This directory holds hand-labeled moment files that the evaluation harness
(`worker/eval/harness.py`) scores the detector against.

## File format

Each file is a JSON array of labeled moments:

```json
[
  {
    "source": "my-stream-2026-06-15",
    "start_sec": 3420.0,
    "end_sec": 3455.0,
    "note": "car crash into lake, chat went wild"
  }
]
```

| Field       | Type   | Description                                         |
|-------------|--------|-----------------------------------------------------|
| `source`    | string | Identifier for the stream/VOD (matches job source). |
| `start_sec` | float  | Start of the moment in seconds from stream start.   |
| `end_sec`   | float  | End of the moment in seconds from stream start.     |
| `note`      | string | Free-text note explaining why this moment matters.  |

## How to label

1. Watch a VOD you know well.
2. Note the timestamps of moments you would personally cut as a highlight clip.
3. Write them into a JSON file in this directory following the format above.
4. Use a descriptive filename, e.g. `msc-2026-06-15.json`.

Keep labels honest: mark the moments you would actually ship, not every mildly
interesting second.  A dozen well-chosen labels per VOD is enough to make the
hit-rate metric meaningful.

## Matching definition

The harness uses **label-center-inside-candidate** matching by default:
a label is "hit" if the midpoint of the labeled interval (`(start_sec + end_sec) / 2`)
falls within some candidate's `[start_sec, end_sec]` range (with a configurable
tolerance in seconds added to each side of the candidate).

This was chosen over IoU because ground-truth labels and detector candidates
often have very different durations (a 5-second labeled moment vs. a 30-second
sliding window), which makes IoU misleadingly low even when the detector clearly
found the right moment.

## Sample file

`sample.json` is a synthetic example for format reference only -- it does not
contain real stream labels.

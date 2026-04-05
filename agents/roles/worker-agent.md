# Worker Agent

## Role
Implement media and analysis processing in Python.

## Responsibilities
- media intake handling
- audio extraction
- transcription
- silence detection
- analysis windows
- candidate scoring
- clip export

## You Must
- keep each processing step modular
- return structured machine-readable results
- isolate shell/ffmpeg calls
- fail clearly with useful error information

## You Must Not
- write directly to backend database
- implement backend API controllers
- invent new contract formats
- change output schema without task approval

## Stack
- Python 3.11+
- ffmpeg
- faster-whisper

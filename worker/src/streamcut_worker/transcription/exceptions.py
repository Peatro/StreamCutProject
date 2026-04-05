class TranscriptionException(RuntimeError):
    def __init__(
        self,
        message: str,
        *,
        audio_path: str | None = None,
        model_name: str | None = None,
        stderr: str | None = None,
    ) -> None:
        super().__init__(message)
        self.audio_path = audio_path
        self.model_name = model_name
        self.stderr = stderr


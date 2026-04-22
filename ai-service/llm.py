import httpx

from config import AI_PROVIDER, AI_API_KEY, AI_MODEL

_DEFAULT_MODELS = {
    "anthropic": "claude-sonnet-4-20250514",
    "openai": "gpt-4o-mini",
}


class LLMClient:
    def __init__(self) -> None:
        self.provider = AI_PROVIDER
        self.api_key = AI_API_KEY
        self.model = AI_MODEL or _DEFAULT_MODELS.get(AI_PROVIDER, _DEFAULT_MODELS["anthropic"])
        self._http = httpx.AsyncClient(timeout=30.0)

    @property
    def is_configured(self) -> bool:
        return bool(self.api_key)

    async def chat(self, system: str, messages: list[dict]) -> str:
        if not self.is_configured:
            raise RuntimeError("LLM not configured")
        if self.provider == "openai":
            return await self._openai(system, messages)
        return await self._anthropic(system, messages)

    async def _anthropic(self, system: str, messages: list[dict]) -> str:
        resp = await self._http.post(
            "https://api.anthropic.com/v1/messages",
            headers={
                "x-api-key": self.api_key,
                "anthropic-version": "2023-06-01",
                "content-type": "application/json",
            },
            json={
                "model": self.model,
                "max_tokens": 500,
                "system": system,
                "messages": messages,
            },
        )
        resp.raise_for_status()
        return resp.json()["content"][0]["text"]

    async def _openai(self, system: str, messages: list[dict]) -> str:
        msgs = [{"role": "system", "content": system}] + messages
        resp = await self._http.post(
            "https://api.openai.com/v1/chat/completions",
            headers={
                "Authorization": f"Bearer {self.api_key}",
                "Content-Type": "application/json",
            },
            json={
                "model": self.model,
                "max_tokens": 500,
                "messages": msgs,
            },
        )
        resp.raise_for_status()
        return resp.json()["choices"][0]["message"]["content"]

    async def close(self) -> None:
        await self._http.aclose()

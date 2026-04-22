import numpy as np
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity


class VectorStore:
    def __init__(self) -> None:
        self.documents: list[dict] = []
        self._vectorizer = TfidfVectorizer(max_features=5000, stop_words="english")
        self._matrix = None
        self._dirty = True

    def clear(self) -> None:
        self.documents.clear()
        self._matrix = None
        self._dirty = True

    def add(self, doc_id: str, text: str, metadata: dict | None = None) -> None:
        self.documents.append({"id": doc_id, "text": text, "metadata": metadata or {}})
        self._dirty = True

    def _rebuild(self) -> None:
        if not self.documents:
            self._matrix = None
            self._dirty = False
            return
        texts = [d["text"] for d in self.documents]
        self._matrix = self._vectorizer.fit_transform(texts)
        self._dirty = False

    def search(self, query: str, top_k: int = 5) -> list[dict]:
        if self._dirty:
            self._rebuild()
        if self._matrix is None or self._matrix.shape[0] == 0:
            return []
        query_vec = self._vectorizer.transform([query])
        scores = cosine_similarity(query_vec, self._matrix).flatten()
        top_indices = np.argsort(scores)[::-1][:top_k]
        return [
            {"document": self.documents[i], "score": float(scores[i])}
            for i in top_indices
            if scores[i] > 0.01
        ]

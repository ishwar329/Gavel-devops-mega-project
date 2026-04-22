import chromadb


class VectorStore:
    def __init__(self) -> None:
        self._client = chromadb.Client()
        self._collection = self._client.get_or_create_collection(
            name="gavel", metadata={"hnsw:space": "cosine"}
        )

    @property
    def count(self) -> int:
        return self._collection.count()

    def clear(self) -> None:
        self._client.delete_collection("gavel")
        self._collection = self._client.get_or_create_collection(
            name="gavel", metadata={"hnsw:space": "cosine"}
        )

    def add(self, doc_id: str, text: str, metadata: dict | None = None) -> None:
        safe_meta: dict | None = None
        if metadata:
            filtered = {k: v for k, v in metadata.items() if isinstance(v, (str, int, float, bool))}
            if filtered:
                safe_meta = filtered
        self._collection.upsert(
            ids=[doc_id],
            documents=[text],
            metadatas=[safe_meta] if safe_meta else None,
        )

    def search(self, query: str, top_k: int = 5) -> list[dict]:
        if self._collection.count() == 0:
            return []
        n = min(top_k, self._collection.count())
        results = self._collection.query(query_texts=[query], n_results=n)
        output = []
        for i in range(len(results["ids"][0])):
            distance = results["distances"][0][i] if results.get("distances") else 0
            score = 1.0 - distance
            if score < 0.01:
                continue
            output.append({
                "document": {
                    "id": results["ids"][0][i],
                    "text": results["documents"][0][i],
                    "metadata": results["metadatas"][0][i] if results.get("metadatas") else {},
                },
                "score": score,
            })
        return output

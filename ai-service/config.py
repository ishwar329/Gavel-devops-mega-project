import os

JWT_SECRET = os.getenv("JWT_SECRET", "dev-secret-change-in-production-key")
AI_PROVIDER = os.getenv("AI_PROVIDER", "anthropic")
AI_API_KEY = os.getenv("AI_API_KEY", "")
AI_MODEL = os.getenv("AI_MODEL", "")
KAFKA_BROKERS = os.getenv("KAFKA_BROKERS", "localhost:9092")
AUCTION_SERVICE_URL = os.getenv("AUCTION_SERVICE_URL", "http://localhost:8081")
USER_SERVICE_URL = os.getenv("USER_SERVICE_URL", "http://localhost:8082")
SHOP_SERVICE_URL = os.getenv("SHOP_SERVICE_URL", "http://localhost:8083")
PORT = int(os.getenv("PORT", "8086"))

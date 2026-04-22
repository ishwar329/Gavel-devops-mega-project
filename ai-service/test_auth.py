import time
import unittest

import jwt
from fastapi import HTTPException
from starlette.testclient import TestClient

from config import JWT_SECRET


def _make_token(user_id: str = "u-1", role: str = "buyer", expired: bool = False) -> str:
    payload = {
        "sub": user_id,
        "username": "testuser",
        "email": "test@test.com",
        "role": role,
        "iat": int(time.time()),
        "exp": int(time.time()) + (-3600 if expired else 86400),
    }
    return jwt.encode(payload, JWT_SECRET, algorithm="HS256")


class TestAuth(unittest.TestCase):

    def test_valid_token(self):
        from auth import get_current_user
        from starlette.requests import Request
        from starlette.datastructures import Headers

        token = _make_token("u-42", "buyer")
        scope = {"type": "http", "headers": [(b"authorization", f"Bearer {token}".encode())]}
        request = Request(scope)
        user = get_current_user(request)
        self.assertEqual(user["user_id"], "u-42")
        self.assertEqual(user["role"], "buyer")

    def test_missing_token(self):
        from auth import get_current_user
        from starlette.requests import Request

        scope = {"type": "http", "headers": []}
        request = Request(scope)
        with self.assertRaises(HTTPException) as ctx:
            get_current_user(request)
        self.assertEqual(ctx.exception.status_code, 401)

    def test_expired_token(self):
        from auth import get_current_user
        from starlette.requests import Request

        token = _make_token(expired=True)
        scope = {"type": "http", "headers": [(b"authorization", f"Bearer {token}".encode())]}
        request = Request(scope)
        with self.assertRaises(HTTPException) as ctx:
            get_current_user(request)
        self.assertEqual(ctx.exception.status_code, 401)

    def test_invalid_token(self):
        from auth import get_current_user
        from starlette.requests import Request

        scope = {"type": "http", "headers": [(b"authorization", b"Bearer not-a-jwt")]}
        request = Request(scope)
        with self.assertRaises(HTTPException) as ctx:
            get_current_user(request)
        self.assertEqual(ctx.exception.status_code, 401)


if __name__ == "__main__":
    unittest.main()

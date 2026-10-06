import hashlib
import json
import tempfile
import threading
import urllib.error
import urllib.request
from unittest.mock import patch
import server


def main():
    data = dict(language="ko", questionId="q1", kind="translation", prompt="请翻译", reference="학교에 가요.", explanation="目的地用 에", answer="학교에 가요")
    feedback = dict(questionId="q1", grade="CORRECT", feedback="意思正确，表达自然。", hint="回想目的地助词。")
    assert server.validate_feedback(feedback, "q1") == feedback
    for bad in ({**feedback, "questionId": "other"}, {**feedback, "grade": "MASTERED"}, {**feedback, "hint": "x" * 501}):
        try:
            server.validate_feedback(bad, "q1")
            raise AssertionError("Invalid feedback accepted")
        except server.Problem:
            pass
    with tempfile.TemporaryDirectory() as folder:
        http = server.create_server(("127.0.0.1", 0), folder + "/budget.sqlite")
        http.user_limit = 1
        worker = threading.Thread(target=http.serve_forever, daemon=True)
        worker.start()
        def post(value, authorized=True):
            raw = json.dumps(value).encode()
            headers = {"Content-Type": "application/json", "Idempotency-Key": hashlib.sha256(raw).hexdigest()}
            if authorized:
                headers["Authorization"] = "Bearer user-test-token"
            req = urllib.request.Request(f"http://127.0.0.1:{http.server_port}/v1/teach", data=raw, headers=headers)
            try:
                with urllib.request.urlopen(req, timeout=2) as response:
                    return response.status, json.load(response)
            except urllib.error.HTTPError as error:
                return error.code, json.load(error)
        try:
            assert post(data, False)[0] == 401
            with patch.object(server, "authenticate", return_value="user"), patch.object(server, "evaluate", return_value=feedback) as model:
                assert post(data) == (200, feedback)
                assert post(data) == (200, feedback)
                assert model.call_count == 1
                assert post({**data, "answer": "other"})[0] == 429
            reopened = server.Budget(folder + "/budget.sqlite")
            raw = json.dumps(data).encode()
            assert reopened.reserve("user", hashlib.sha256(raw).hexdigest(), 1, 100)[1][0] == 200
        finally:
            http.shutdown(); http.server_close(); worker.join()
    print("Teaching gateway checks passed: validation, auth gate, idempotency, durable quota and HTTP round trip; no paid API calls.")


if __name__ == "__main__":
    main()

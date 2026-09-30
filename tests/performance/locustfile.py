"""Taller 2 - pruebas de rendimiento con Locust sobre auth-service.

Flujo: login (que internamente llama a identity-service) y generacion de QR.
Ajuste USERNAME/PASSWORD a un usuario que exista en su base (local_users).
Ejecucion:
  locust -f tests/performance/locustfile.py --headless -u 50 -r 5 -t 3m \
         --host http://localhost:18180 --csv reports/locust/carga
"""
import os
from locust import HttpUser, task, between

USERNAME = os.getenv("LOAD_USER", "admin")
PASSWORD = os.getenv("LOAD_PASS", "password")


class EstudianteUser(HttpUser):
    wait_time = between(1, 3)
    token = None

    def on_start(self):
        with self.client.post("/api/v1/auth/login",
                              json={"username": USERNAME, "password": PASSWORD},
                              name="POST /login", catch_response=True) as r:
            if r.status_code == 200:
                self.token = r.json().get("token")
                r.success()
            else:
                r.failure(f"login {r.status_code}")

    @task(3)
    def login(self):
        self.client.post("/api/v1/auth/login",
                         json={"username": USERNAME, "password": PASSWORD},
                         name="POST /login")

    @task(1)
    def generar_qr(self):
        headers = {"Authorization": f"Bearer {self.token}"} if self.token else {}
        self.client.get("/api/v1/auth/qr/generate", headers=headers, name="GET /qr/generate")

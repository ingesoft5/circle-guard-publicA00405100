"""Taller 2 - Pruebas E2E sobre los servicios desplegados en Kubernetes.

Cada prueba recorre un flujo completo de usuario a traves de varios servicios.
Se ejecutan contra las URLs de los servicios (port-forward en el pipeline):
  AUTH_URL, IDENTITY_URL, FORM_URL, PROMOTION_URL, DASHBOARD_URL
Usuarios semilla (auth-service, V2__seed_test_users.sql): staff_guard,
health_user (rol HEALTH_CENTER) y super_admin, todos con contrasena "password".
"""
import os
import time
import uuid

import requests

AUTH = os.getenv("AUTH_URL", "http://localhost:8180")
IDENTITY = os.getenv("IDENTITY_URL", "http://localhost:8083")
PROMOTION = os.getenv("PROMOTION_URL", "http://localhost:8088")
DASHBOARD = os.getenv("DASHBOARD_URL", "http://localhost:8084")
TIMEOUT = 20


def login(username, password="password"):
    return requests.post(f"{AUTH}/api/v1/auth/login",
                         json={"username": username, "password": password}, timeout=TIMEOUT)


def anonymous_id(real_identity):
    r = requests.post(f"{IDENTITY}/api/v1/identities/map",
                      json={"realIdentity": real_identity}, timeout=TIMEOUT)
    assert r.status_code == 200, r.text
    return r.json()["anonymousId"]


def eventually(fn, seconds=45, step=3):
    """Reintenta hasta que fn() devuelva algo verdadero (flujos asincronos por Kafka)."""
    end = time.time() + seconds
    last = None
    while time.time() < end:
        last = fn()
        if last:
            return last
        time.sleep(step)
    return last


def test_e2e_1_login_valido_devuelve_token_y_anonymous_id():
    """auth -> identity: un login valido entrega JWT y un anonymousId (nunca el usuario real)."""
    r = login("staff_guard")
    assert r.status_code == 200, r.text
    body = r.json()
    assert body["type"] == "Bearer"
    assert len(body["token"].split(".")) == 3
    uuid.UUID(body["anonymousId"])          # debe ser un UUID valido
    assert "staff_guard" not in body["anonymousId"]


def test_e2e_2_login_invalido_se_rechaza_con_401():
    """Credenciales incorrectas: 401 controlado, no un error 500 ni un token."""
    r = login("staff_guard", "clave-incorrecta")
    assert r.status_code == 401
    assert "token" not in r.json()


def test_e2e_3_anonymous_id_es_estable_entre_sesiones():
    """Dos sesiones del mismo estudiante deben mapear al mismo anonymousId,
    o el grafo de contactos se fragmentaria."""
    a = login("health_user").json()["anonymousId"]
    b = login("health_user").json()["anonymousId"]
    assert a == b
    otro = login("super_admin").json()["anonymousId"]
    assert otro != a


def test_e2e_4_flujo_qr_de_acceso_requiere_sesion():
    """Login -> QR de ingreso al campus. Sin token el QR no se genera."""
    sin_token = requests.get(f"{AUTH}/api/v1/auth/qr/generate", timeout=TIMEOUT)
    assert sin_token.status_code in (401, 403)

    token = login("staff_guard").json()["token"]
    r = requests.get(f"{AUTH}/api/v1/auth/qr/generate",
                     headers={"Authorization": f"Bearer {token}"}, timeout=TIMEOUT)
    assert r.status_code == 200, r.text
    assert len(r.json()["qrToken"].split(".")) == 3
    assert r.json()["expiresIn"] == "60"


def test_e2e_5_caso_confirmado_se_propaga_a_contactos_y_llega_al_dashboard():
    """Flujo central: auth -> identity -> promotion (grafo Neo4j) -> dashboard.
    Un caso CONFIRMED marca a su contacto como SUSPECT y ambos se reflejan
    en el resumen del campus que publica el dashboard."""
    token = login("health_user").json()["token"]
    headers = {"Authorization": f"Bearer {token}"}

    caso = anonymous_id(f"e2e-caso-{uuid.uuid4()}@u.test")
    contacto = anonymous_id(f"e2e-contacto-{uuid.uuid4()}@u.test")

    base = requests.get(f"{DASHBOARD}/api/v1/analytics/summary", timeout=TIMEOUT).json()

    r = requests.post(f"{PROMOTION}/api/v1/encounters/report",
                      json={"sourceId": caso, "targetId": contacto, "locationId": "aula-e2e"},
                      timeout=TIMEOUT)
    assert r.status_code == 200, r.text

    r = requests.post(f"{PROMOTION}/api/v1/health/confirmed",
                      json={"anonymousId": caso}, headers=headers, timeout=TIMEOUT)
    assert r.status_code == 200, r.text

    def resumen_actualizado():
        s = requests.get(f"{DASHBOARD}/api/v1/analytics/summary", timeout=TIMEOUT).json()
        ok = (s.get("confirmedCount", 0) > base.get("confirmedCount", 0)
              and s.get("suspectCount", 0) > base.get("suspectCount", 0))
        return s if ok else None

    resumen = eventually(resumen_actualizado)
    assert resumen, "el dashboard no reflejo el caso confirmado ni su contacto sospechoso"

# pip install cryptography
# python keygen.py                      -> prints PUBLIC KEY for Core.kt (first run creates private.pem; keep it SECRET)
# python keygen.py DEVICEID 30 Name     -> prints a 30-day key locked to that device
import sys, os, time, base64
from cryptography.hazmat.primitives import hashes, serialization as s
from cryptography.hazmat.primitives.asymmetric import ec
F = "private.pem"
b = lambda x: base64.urlsafe_b64encode(x).decode().rstrip("=")
if not os.path.exists(F):
    open(F, "wb").write(ec.generate_private_key(ec.SECP256R1()).private_bytes(s.Encoding.PEM, s.PrivateFormat.PKCS8, s.NoEncryption()))
k = s.load_pem_private_key(open(F, "rb").read(), None)
pub = base64.b64encode(k.public_key().public_bytes(s.Encoding.DER, s.PublicFormat.SubjectPublicKeyInfo)).decode()
if len(sys.argv) < 3:
    print("PUBLIC KEY (paste into Core.kt):\n" + pub); sys.exit()
dev, days = sys.argv[1].upper(), int(sys.argv[2]); name = sys.argv[3] if len(sys.argv) > 3 else "user"
p = f"{dev}|{int(time.time() // 86400) + days}|{name}".encode()
print("UX-" + b(p) + "." + b(k.sign(p, ec.ECDSA(hashes.SHA256()))))

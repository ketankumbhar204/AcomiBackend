# ACOMI Backend

Spring Boot API for **ACOMI** (Accommodation + Meals - Manage Your Stay & Meals).

- Maven: com.acomi:acomi-backend
- Package: com.acomi.acomi_backend
- Config prefix: acomi.* (JWT, OTP, CORS)

## OTP / SMS (2Factor)

Production SMS OTP is sent by the backend to [2Factor](https://2factor.in). Clients (mobile/web) call ACOMI `POST /api/v1/auth/send-otp` and `POST /api/v1/auth/verify-otp` only. They never call 2Factor and never receive the API key.

| Variable | Where | Notes |
|----------|--------|--------|
| `TWOFACTOR_API_KEY` | Hosting env / `~/acomi-backend.env` on EC2 | **Required** for `prod`. Never commit. No default in `application-prod.yml`. |
| `TWOFACTOR_OTP_TEMPLATE` | Optional env | Defaults to `OTP1`. Set this if 2Factor assigned a production template name. |
| `TWOFACTOR_PHONE_PREFIX` | Optional env | Empty by default (10-digit Indian numbers). |
| `ACOMI_OTP_SENDER` | Local only | Default `dev` (logs OTP to the console). Set `twofactor` to send real SMS locally. |
| `OTP_HASH_SECRET` | All deployed profiles | HMAC for local OTP rows / verification tokens. Not the 2Factor key. |

Production (`SPRING_PROFILES_ACTIVE=prod`) sets `acomi.otp.sender: twofactor`. Missing `TWOFACTOR_API_KEY` fails startup.

Copy `.env.example` for local placeholders. `.env` is gitignored.

Local real SMS (do not commit the key):

```bash
export ACOMI_OTP_SENDER=twofactor
export TWOFACTOR_API_KEY=<your-production-api-key>
```

Production EC2: add `TWOFACTOR_API_KEY` to `/home/ubuntu/acomi-backend.env`, then recreate the `acomi-backend` container with the existing `--env-file` (see the AWS deployment guide). Do not put the key in Docker, Git, or frontend code.

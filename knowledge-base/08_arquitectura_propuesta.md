# Arquitectura Propuesta

## Patrones aplicados

| Patrón | Dónde se usa | Por qué |
|--------|--------------|---------|
| Monolito modular | Backend Spring Boot, un artefacto | Mínimo costo operativo y de despliegue; fronteras claras por módulo |
| Arquitectura por capas dentro de cada módulo (api / application / domain / infrastructure) | Cada módulo | Dominio testeable sin framework (Strict TDD) |
| Dependencias entre módulos solo por interfaces públicas (`api` del módulo) | Todos | Evita acoplamiento; habilita separar en el futuro. Se puede verificar con ArchUnit o Spring Modulith (**Suposición**) |
| Restricción en la BD como fuente de verdad de invariantes | `appointment` (EXCLUDE gist), `UNIQUE` en notificaciones | Evita carreras que la validación de aplicación no cubre |
| Entradas inmutables append-only | Historia clínica, audit log | Ley 26.529; trazabilidad |
| Outbox simple (tabla `notification`) + scheduler | Emails | Reintentos e idempotencia sin broker externo (costo mínimo) |
| Preparación multi-tenant (`practice_id`) | Todas las tablas | No bloquear la evolución (DD-02) |
| SPA con módulos por funcionalidad y rutas hash/History API | Frontend | Sin framework: estructura propia simple y tipada |

## Módulos (backend)

| Módulo | Responsabilidad |
|--------|-----------------|
| `agenda` | Horarios, bloqueos, prestaciones, turnos, cálculo de disponibilidad |
| `reservas` | API pública de reserva, tokens, cancelación/reprogramación |
| `pacientes` | Fichas de pacientes |
| `historiaclinica` | Anamnesis, odontograma, evoluciones, correcciones |
| `notificaciones` | Cola de notificaciones, scheduler, proveedor de email |
| `auth` / `auditoria` | Login, sesión, rate limit, log de auditoría |
| `shared` | Tiempo/zona horaria, manejo de errores, configuración |

Reglas de dependencia: `reservas` → `agenda`, `pacientes`, `notificaciones`; `historiaclinica` → `pacientes`, `auditoria`; `auth` y `auditoria` no dependen de módulos de negocio.

## Estructura de directorios

```
proyecto/
├── infra/postgres/init/01-roles.sql  # base y roles para PostgreSQL local instalado (DD-10)
├── .env.example
├── backend/
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── java/ar/.../consultorio/        # paquete base (TBD)
│       │   │   ├── ConsultorioApplication.java
│       │   │   ├── agenda/
│       │   │   │   ├── api/            # controllers + DTOs
│       │   │   │   ├── application/    # casos de uso
│       │   │   │   ├── domain/         # entidades, reglas (puro)
│       │   │   │   └── infrastructure/ # repositorios JPA/JDBC
│       │   │   ├── reservas/           # (misma estructura)
│       │   │   ├── pacientes/
│       │   │   ├── historiaclinica/
│       │   │   ├── notificaciones/
│       │   │   ├── auth/
│       │   │   ├── auditoria/
│       │   │   └── shared/
│       │   └── resources/
│       │       ├── application.yml
│       │       ├── application-dev.yml
│       │       └── db/migration/       # Flyway: V1__init.sql, V2__exclusion_constraint.sql ...
│       └── test/                       # unit + integración (PostgreSQL embebido zonky)
└── frontend/
    ├── package.json
    ├── tsconfig.json
    ├── vite.config.ts
    ├── index.html
    └── src/
        ├── main.ts                     # bootstrap
        ├── router/                     # router propio (History API) + guards
        ├── core/
        │   ├── http/                   # cliente fetch tipado, manejo de errores/CSRF
        │   ├── auth/                   # estado de sesión
        │   ├── state/                  # store mínimo (pub/sub)
        │   └── dom/                    # helpers de render, escape de HTML
        ├── features/
        │   ├── public-booking/         # reservar, gestionar por token
        │   ├── agenda/                 # vistas diaria/semanal
        │   ├── patients/
        │   ├── clinical-record/        # anamnesis, odontograma, evoluciones
        │   ├── settings/               # horarios, prestaciones, bloqueos
        │   └── auth/                   # login
        ├── shared/
        │   ├── components/             # componentes web nativos / funciones de vista
        │   ├── types/                  # tipos de DTOs compartidos
        │   └── utils/                  # fechas (zona horaria), formateo
        └── styles/                     # CSS con variables, mobile-first
```

La carpeta `design-system/` en la raíz (si la política de diseño lo habilita) aportará `tokens.css` y reglas visuales.

## Seguridad

- **Autenticación del odontólogo**: método a decidir (ver 10). Opciones evaluadas: (a) sesión servidor con cookie `HttpOnly; Secure; SameSite=Strict` + protección CSRF; (b) JWT de corta vida + refresh. **Suposición:** se prefiere (a) por simplicidad y por permitir invalidación inmediata en una SPA sin framework; contraseñas con Argon2id/bcrypt, rate limit y bloqueo progresivo; 2FA TOTP como mejora a evaluar.
- **Autorización**: en v1 un único rol DENTIST; los endpoints `/api/v1/**` exigen autenticación salvo `/public/**` y `/auth/login`. Modelar roles con enum y `@PreAuthorize` para que sumar SECRETARY no requiera rediseño.
- **Links con token (paciente)**: token CSPRNG ≥128 bits en Base64URL; en BD solo el hash; ligado a un turno; expira y se revoca; comparación en tiempo constante; respuestas genéricas; rate limit; no se loguea el token completo; encabezado `Referrer-Policy: no-referrer` en la página de gestión.
- **Endpoints públicos**: rate limit por IP, honeypot, validación estricta, límite de turnos futuros activos por contacto; CAPTCHA como opción si hay abuso (a evaluar; privacidad/costo).
- **Auditoría**: toda lectura y escritura de datos clínicos y de pacientes se registra (usuario, acción, paciente, IP, fecha); tabla append-only con permisos de BD restringidos. Retención TBD.
- **Cifrado**: HTTPS obligatorio fuera de localhost (HSTS); cifrado en reposo a nivel disco/volumen y de copias de seguridad; **Suposición:** cifrado a nivel columna de campos clínicos sensibles se evalúa según análisis de riesgo/costo (no incluido por defecto en v1).
- **Inmutabilidad de la HC**: sin endpoints de UPDATE/DELETE; el rol de BD de la aplicación sin UPDATE/DELETE sobre tablas de HC y `audit_log` (se usa un rol distinto para migraciones).
- **Validación de input**: Bean Validation en el backend; en el frontend escape de HTML en todo render (sin `innerHTML` con datos de usuario) y CSP estricta.
- **Datos personales en logs/URLs**: nunca DNI, email, ni contenido clínico en logs, URLs o emails.
- **Secrets**: solo por variables de entorno; `.env` fuera del repositorio; credenciales de bootstrap no versionadas.
- **Cabeceras**: CSP, X-Content-Type-Options, X-Frame-Options/frame-ancestors, CORS restringido (mismo origen).
- **Backups**: pg_dump cifrado programado y prueba de restauración (definición pendiente; ver 10).

## Despliegue

- **Ahora**: ejecución local con PostgreSQL instalado en cada equipo, sin Docker (DD-10). Cloud/proveedor, estrategia de despliegue y ubicación de los datos = pregunta abierta (PA-03).
- **Suposición:** artefacto único de la app (backend sirviendo la SPA compilada) para minimizar piezas.
- **Riesgo single-tenant:** N clientes = N despliegues (actualizaciones, backups, monitoreo, certificados). Mitigación a evaluar: automatizar el despliegue (script/IaC), artefacto versionado, y revisar la decisión si N crece (ver DD-02).

## Variables de entorno

| Variable | Descripción | Ejemplo | Sensible |
|----------|-------------|---------|----------|
| `SPRING_PROFILES_ACTIVE` | Perfil | `dev` | N |
| `DB_URL` | JDBC de PostgreSQL | `jdbc:postgresql://localhost:5432/consultorio` | N |
| `DB_USER` / `DB_PASSWORD` | Credenciales del rol de aplicación | `app` / `***` | Y |
| `DB_MIGRATION_USER` / `DB_MIGRATION_PASSWORD` | Rol con DDL para Flyway | `migrator` / `***` | Y |
| `APP_BASE_URL` | URL pública (links en emails) | `https://turnos.ejemplo.com` | N |
| `APP_TIMEZONE` | Zona horaria del consultorio | `America/Argentina/Buenos_Aires` | N |
| `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD` | Alta inicial del odontólogo | `dr@ejemplo.com` / `***` | Y |
| `AUTH_SESSION_TIMEOUT_MIN` | Expiración por inactividad | `30` | N |
| `MAIL_PROVIDER` | Proveedor de email (TBD); en dev los emails se escriben en el log, sin envío (DD-10) | `smtp` | N |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USER` / `MAIL_PASSWORD` | SMTP | `smtp.ejemplo.com` / `587` | Y (password) |
| `MAIL_FROM` | Remitente | `turnos@ejemplo.com` | N |
| `REMINDER_HOURS_BEFORE` | Anticipación del recordatorio | `24` | N |
| `RATE_LIMIT_PUBLIC_PER_MIN` | Límite por IP en endpoints públicos | `30` | N |
| `BOOKING_TOKEN_TTL_DAYS` | Vigencia posterior al turno | `1` | N |
| `CORS_ALLOWED_ORIGINS` | Orígenes permitidos | `https://turnos.ejemplo.com` | N |

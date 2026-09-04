# PR Reviewer AI

Servicio backend en Spring Boot que recibe un diff de código y devuelve un review estructurado (JSON) generado por un LLM — como un compañero senior comentando tu Pull Request, pero automático.

## Qué hace

- **Entrada**: un diff de código (texto), vía `POST /review`.
- **Procesamiento**: el diff se inserta en un prompt estructurado y se envía a un LLM (Gemini API, tier gratuito) pidiendo explícitamente una respuesta en JSON.
- **Salida**: una lista de comentarios de revisión estructurados — no texto libre — cada uno con archivo, línea, severidad, categoría, mensaje y sugerencia opcional.

El objetivo del proyecto no es "llamar a la API de un LLM", sino resolver el problema real de conseguir **salida estructurada y fiable** de un modelo de lenguaje, con manejo de errores cuando el modelo no cumple el formato esperado.

## Stack

- **Backend**: Java 21, Spring Boot 4.1.1 (Jackson 3), Spring Web + WebFlux (para `WebClient`)
- **LLM**: Gemini API (Google AI Studio, tier gratuito — sin tarjeta), modelo principal **`gemini-3.6-flash`** (20 req/día en el tier gratuito), con **`gemini-3.5-flash-lite`** (500 req/día) como alternativa para desarrollo/evaluación intensiva cuando se agota la cuota del principal.
  - ⚠️ `gemini-2.5-flash` (el modelo "por defecto" en la documentación de ejemplos) está descontinuado para cuentas nuevas — comprobar `GET /v1beta/models?key=...` para ver qué modelos y cuotas están disponibles.
- **Testing**: JUnit 5 + Mockito (unitarios) + `@SpringBootTest` (evaluación de integración real)
- **Build**: Maven
- **IDE**: IntelliJ

### Notas de Jackson 3 (Spring Boot 4)
Spring Boot 4 usa Jackson 3, que cambió el paquete de `com.fasterxml.jackson.databind` a **`tools.jackson.databind`**, y `ObjectMapper` ya no se instancia con `new ObjectMapper()` (es inmutable). En su lugar, se inyecta el bean `tools.jackson.databind.json.JsonMapper` que Spring Boot autoconfigura.

## Contrato de la API

### Request

```json
POST /review
{
  "diff": "string con el diff de código",
  "fileName": "NombreArchivo.java"
}
```

### Response

```json
{
  "comments": [
    {
      "file": "UserService.java",
      "line": 2,
      "severity": "WARNING",
      "category": "SECURITY",
      "message": "descripción del problema detectado",
      "suggestion": "sugerencia de fix (opcional, puede ser null)"
    }
  ]
}
```

- `severity`: `INFO` | `WARNING` | `CRITICAL`
- `category`: `BUG` | `SECURITY` | `STYLE` | `PERFORMANCE` | `BEST_PRACTICE`

## Estructura del proyecto

```
com.jorge.prreviewer
├── controller/   → ReviewController (expone POST /review)
├── dto/          → ReviewRequest, ReviewResponse, ReviewComment
├── service/      → ReviewService (orquesta la llamada al LLM y arma la respuesta)
├── llm/          → LlmClient (interfaz) + GeminiClient (implementación)
├── exception/    → InvalidDiffException, LlmResponseException
├── config/       → LlmConfig (bean del cliente HTTP hacia Gemini + API key)
└── PrReviewerApplication
```

Cada paquete tiene una responsabilidad única:
- `config/` cablea el cliente HTTP y las credenciales, sin lógica de negocio.
- `llm/` habla con el proveedor externo y devuelve la respuesta cruda.
- `service/` decide qué hacer con esa respuesta (parsear, reintentar si falla, mapear al DTO final).
- `controller/` solo expone el endpoint, sin lógica.

## Estado del proyecto: MVP funcional completo

Todas las tareas base están cerradas:

1. DTOs (`ReviewRequest`, `ReviewResponse`, `ReviewComment`) + `ReviewController` (endpoint real, no mock)
2. `LlmConfig` — bean `WebClient` hacia Gemini, API key vía variable de entorno (`GEMINI_API_KEY`), nunca hardcodeada
3. `LlmClient` (interfaz) + `GeminiClient` (implementación) — llamada real a `gemini-3.6-flash`
4. `ReviewService` — prompt estructurado + parseo a JSON con `JsonMapper` (Jackson 3)
5. Manejo de errores: `InvalidDiffException` → `400` vía `@RestControllerAdvice` (no `500`); reintento (máx. 2) + `ReviewComment` de fallback si el LLM no devuelve JSON válido
6. Tests: `ReviewServiceTest` (unitarios, Mockito, `LlmClient` mockeado — caso feliz, fallback, reintento exitoso) + `ReviewServiceEvaluationTest` (integración real contra Gemini, mide si detecta la categoría esperada en 5 diffs con bugs reales)

### Roadmap / posibles extensiones (no bloqueantes)

- **v2**: migrar a *structured output* nativo de Gemini (JSON Schema forzado a nivel de API) en vez de parseo manual — comparar ambos enfoques es parte del valor del proyecto de cara a una entrevista.
- Conectar con la API de GitHub para traer el diff directamente de un PR (`GET /repos/{owner}/{repo}/pulls/{pr}/files`), en vez de pegado a mano.
- Guardar histórico de reviews en base de datos (JPA) y cachear resultados por hash de diff (Redis).

## Hallazgos de la capa de evaluación

Al montar `ReviewServiceEvaluationTest` con 5 diffs (SQL injection, null pointer, race condition, N+1 query, estilo) surgieron varios hallazgos reales, más valiosos para una entrevista que un simple "funciona":

- **El LLM a veces rechaza analizar código de seguridad.** En una ejecución, el primer intento sobre el diff de SQL injection devolvió un rechazo tipo "Sorry, I cannot fulfill your request to analyze or scan code snippets for security vulnerabilities" en vez de JSON. El mecanismo de reintento (pensado originalmente solo para JSON mal formado) acabó salvando el caso en el segundo intento — un efecto colateral útil, no diseñado a propósito, que merece revisarse (quizás un prompt distinto o un reintento específico para este caso).
- **El tier gratuito tiene rate limits reales y hay que diseñarlos.** Al ejecutar los 5 casos seguidos (más los reintentos de la tarea 5), aparecen `429 Too Many Requests` y `503 Service Unavailable`. Solución aplicada: `Thread.sleep()` entre casos del test de evaluación. Para una versión más robusta, valdría la pena un backoff exponencial en `GeminiClient` en vez de reintento inmediato.
- **Ojo con la dirección del diff al diseñar casos de prueba.** Un diff mal montado (líneas `+`/`-` invertidas, mostrando el código ya arreglado en vez del bug) hace que el LLM evalúe correctamente... el código equivocado. Los 5 casos de evaluación se revisaron para asegurar que las líneas `+` contienen el código con el bug real.
- **Con diffs bien montados y sin rate-limiting de por medio, Gemini detectó correctamente SQL injection, null pointer y N+1 query** (categorías `SECURITY`, `BUG`, `PERFORMANCE` respectivamente). El caso de estilo (`if (x == true)`) fue clasificado como `BUG` (por un posible NPE en el unboxing) en vez de `STYLE` — no es necesariamente un fallo, es una interpretación defendible distinta a la esperada.

### Resultado real de una ejecución completa (modelo `gemini-3.5-flash-lite`, sin errores de red)

**4/5 aciertos.** El único "fallo" (caso del null pointer) tampoco es un error real: el modelo priorizó marcar como `BEST_PRACTICE` el uso de un string mágico (`"unknown"`) como valor de fallback, en vez de centrarse en el riesgo de `NullPointerException` que se buscaba. Es una respuesta técnicamente válida sobre el mismo código, solo que enfocada en otro aspecto — un buen ejemplo de que "acierto/fallo" en evaluación de LLMs no siempre es binario.

**Sobre el cambio de modelo:** `gemini-3.6-flash` tiene solo 20 peticiones/día en el tier gratuito, insuficiente para iterar con pruebas repetidas. `gemini-3.5-flash-lite` ofrece 500 peticiones/día — mucho más margen para desarrollo y evaluación, a cambio de (previsiblemente) algo menos de profundidad de razonamiento. Merece la pena documentar ambos modelos como opción configurable (variable de entorno) en vez de hardcodear uno solo.

## Por qué este proyecto

Es un patrón que ya usan productos comerciales reales (CodeRabbit, GitHub Copilot review). Para el portfolio, demuestra:
- Diseño de un contrato claro (DTOs) entre tu backend y un LLM externo.
- Manejo de fallos de un modelo de IA (respuesta mal formada, reintentos).
- Integración de IA como pieza de una arquitectura, no como chatbot suelto.
- Criterio propio: medir si el LLM detecta bugs reales, no solo confiar en que "funciona".

## Cómo se está construyendo

El proyecto se está desarrollando con ayuda de [OpenCode](https://opencode.ai) (agente de código en terminal), por tareas pequeñas y revisadas una a una, no generadas de golpe.
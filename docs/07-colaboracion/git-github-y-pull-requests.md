# Git, GitHub y pull requests

La rama principal local es `main`. Trabaja en ramas cortas y fusiona mediante pull requests (PR). El CI comprueba reglas automatizadas; una persona sigue siendo responsable de aprobar alcance, arquitectura, seguridad y calidad de las pruebas.

## 1. Primera publicación: una sola vez

Todavía no hay commits ni un repositorio remoto configurado. Antes de publicar, revisa si el enunciado original, los documentos del proyecto y la evidencia de IA pueden compartirse; un repositorio privado también comparte contenido con sus colaboradores. `.gitignore` no detecta secretos dentro de documentos ni elimina archivos ya versionados.

1. Crea en GitHub un repositorio **vacío** en tu cuenta u organización. Elige nombre y visibilidad; no inicialices README, licencia ni `.gitignore` remotos.
2. Revisa los archivos locales y crea el commit inicial. Estos comandos son instrucciones: no se han ejecutado automáticamente.

```bash
git status --short
git status --short --ignored
python3 scripts/check_repository.py
python3 -m unittest discover -s odd/probes -p 'test_*.py' -v
git add .
git diff --cached --stat
git diff --cached
# Solo después de revisar lo que vas a compartir:
git commit -m "chore: initialize repository and CI"
git remote add origin https://github.com/PROPIETARIO/REPOSITORIO.git
git push -u origin main
```

Sustituye los marcadores por el destino elegido y usa tu sesión autorizada de GitHub; nunca incluyas tokens en URLs o archivos. Si `origin` ya existe, revisa `git remote -v` antes de cambiarlo. No uses un push forzado para resolver diferencias remotas.

La publicación inicial establece la base: después podrás abrir PR normales contra `main`. Confirma en GitHub que `main` sea la rama predeterminada y que la primera ejecución de Actions termine correctamente.

## 2. Ciclo diario de un cambio

Con el árbol limpio y la publicación inicial terminada:

```bash
git switch main
git pull --ff-only
git switch -c feat/inventory-query
# Modifica código, pruebas y documentación de una sola unidad de comportamiento.
python3 scripts/check_repository.py
python3 -m unittest discover -s odd/probes -p 'test_*.py' -v
git diff
git add RUTA_DEL_ARCHIVO
git diff --cached
git commit -m "feat(inventory): add inventory query"
git push -u origin feat/inventory-query
```

En GitHub, abre **Pull request**, selecciona base `main` y completa la plantilla. Usa un PR en borrador mientras falten pruebas o decisiones. Cada push a esa rama actualiza el mismo PR y vuelve a ejecutar el CI. No abras otro PR para corregir sus checks.

Prefiere cambios pequeños y coherentes: comportamiento, pruebas y documentación juntos; no un PR para entidades y otro para sus pruebas. `feat`, `fix`, `docs`, `test` y `chore` ayudan a describir la intención de los commits.

## 3. Proteger `main` en GitHub

Después de que existan la rama y una ejecución del workflow, configura un ruleset o una regla de protección en **Settings → Rules → Rulesets** o **Settings → Branches**, según disponibilidad:

- Exigir PR antes de fusionar y resolver conversaciones pendientes.
- Exigir los checks **Integridad del repositorio** y **Consistencia documental**. Selecciona los nombres que aparezcan en la ejecución real.
- Exigir que la rama esté actualizada con `main` antes de fusionar.
- Bloquear force pushes y eliminación de `main`; limitar excepciones de bypass.
- Con otro colaborador, exigir al menos una aprobación y descartar aprobaciones obsoletas. Si trabajas solo, no exijas una aprobación ajena que nadie pueda dar.

La disponibilidad de protección depende del plan y de la visibilidad del repositorio; compruébala antes de confiar en ella. **Tener un archivo CI no obliga a que sus checks pasen para fusionar:** la protección es una configuración adicional del servidor. No se ha configurado automáticamente.

Para ramas desactualizadas, integra `main` en la rama del cambio, resuelve conflictos y vuelve a ejecutar las pruebas. No resuelvas conflictos aceptando todos los cambios de un lado sin revisarlos.

## 4. Qué comprueba el CI actual

Se ejecuta en PR hacia `main`, pushes a `main` y manualmente. No usa filtros por ruta, secretos, despliegues, auto-merge ni permisos de escritura. Cancela ejecuciones obsoletas del mismo evento/rama y limita cada job a cinco minutos. Las acciones se fijan por SHA; actualizarlas exige revisar el nuevo origen y ejecutar de nuevo el CI.

| Check | Evidencia que aporta | No demuestra |
|---|---|---|
| Integridad del repositorio | Nombres de archivos sensibles/generados, lectura JSON y Excalidraw, límite explícito del alcance actual | Detección completa de secretos, validez visual de diagramas o cumplimiento funcional |
| Consistencia documental | Pruebas del modelo de datos existente y de las reglas del repositorio | Compilación Java/React, integración PostgreSQL, renderizado o pruebas de seguridad |

Utiliza Python 3 de Ubuntu 24.04 y biblioteca estándar, solo para comprobaciones auxiliares; no introduce un runtime del producto. Localmente requiere Python 3.10 o posterior. El workflow no instala dependencias de aplicaciones inexistentes.

**Al incorporar aplicaciones, ampliar el CI en ese mismo PR.** El comprobador rechaza los manifiestos iniciales `backend/pom.xml`, `frontend/package.json` y Compose en la raíz para evitar un verde documental presentado como validación del producto. Sustituye esa guarda y actualiza sus pruebas cuando existan jobs reales; no la elimines sin cubrir la aplicación correspondiente. Si se adopta otra ubicación, actualiza también la detección. Es una guarda de transición, no detección universal de aplicaciones.

| Componente futuro | Comprobación a incorporar conforme a la arquitectura aprobada |
|---|---|
| Backend | Temurin y Maven fijados, preferentemente Maven Wrapper; verificación Maven y pruebas reales, incluyendo PostgreSQL/Flyway cuando corresponda |
| Frontend | Node y pnpm fijados, `pnpm install --frozen-lockfile`, scripts reales de tipos, pruebas y construcción |
| Infraestructura | Validación de Compose con configuración de ejemplo sin secretos; después arranque y pruebas de salud/persistencia aisladas |

Las versiones aprobadas están en [arquitectura §5.4](../04-arquitectura/arquitectura-y-diseno-tecnico.md#54-versiones-y-compatibilidad-tecnológica). No se generaron aplicaciones ni se cambiaron esas decisiones. Los nuevos checks deberán añadirse también a la protección de `main`.

## 5. Revisar y fusionar

1. Comprueba que los jobs corresponden al **último commit**, no a uno anterior, y lee sus logs ante fallos.
2. Revisa objetivo, diff, trazabilidad, pruebas y decisiones pendientes. Un test verde puede comprobar una regla equivocada.
3. Resuelve comentarios y conflictos; espera a que las comprobaciones vuelvan a terminar.
4. Fusiona cuando esté aprobado. Para una rama corta, **Squash and merge** deja un commit coherente; conserva el historial completo cuando aporte valor. Es una recomendación, no una política remota aplicada.
5. Elimina la rama remota desde GitHub si ya no es necesaria; actualiza tu `main` con `git pull --ff-only`.

Un check rojo se investiga; no se desactiva para fusionar. Si se publicó una credencial, ignorarla después no basta: revócala o rótala primero y coordina el saneamiento del historial.

## Referencias oficiales

- [Sintaxis de GitHub Actions](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax).
- [Protección de ramas](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches).
- [Revisión de pull requests](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/reviewing-changes-in-pull-requests/about-pull-request-reviews).

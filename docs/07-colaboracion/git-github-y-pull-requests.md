# Git, GitHub y pull requests

La rama principal es `main`. Por decisión del proyecto, se trabajará en una rama por bloque de requisitos funcionales, con integración local y sin protección de `main` ni PR obligatorio. El hook `pre-push` valida antes del envío; GitHub Actions vuelve a comprobar después del push a `main`. Ninguno sustituye revisar alcance, arquitectura, seguridad y calidad de las pruebas.

## 1. Primera publicación: una sola vez

La publicación inicial ya se realizó. Esta sección queda como referencia para un repositorio nuevo; no repitas estos pasos en la copia actual. Antes de publicar, revisa si el enunciado original, los documentos del proyecto y la evidencia de IA pueden compartirse; un repositorio privado también comparte contenido con sus colaboradores. `.gitignore` no detecta secretos dentro de documentos ni elimina archivos ya versionados.

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

## 2. Instalar y usar las comprobaciones compartidas

Requiere Git y Python **3.10 o posterior**. Python es una herramienta auxiliar, no el runtime del producto. GitHub Actions usa Python 3 de Ubuntu 24.04. No se añaden paquetes Python.

La instalación es **local a esta copia** y se repite después de clonar. Primero revisa si ya existe una configuración de hooks; no la sobrescribas sin integrar los hooks existentes:

```bash
git config --show-origin --get core.hooksPath
# Si no existe una configuración previa ni hooks propios en .git/hooks:
chmod +x .githooks/pre-push
git config --local core.hooksPath .githooks
```

El comando único para comprobar el árbol de trabajo, sin publicar nada, es:

```bash
python3 scripts/run_checks.py
```

Ejecuta los dos grupos en paralelo, espera ambos y devuelve error si falla cualquiera. Los resultados se muestran agrupados para no mezclar diagnósticos. El workflow invoca el mismo registro de comandos, con jobs independientes:

```bash
python3 scripts/run_checks.py repository
python3 scripts/run_checks.py documentation
```

Al hacer `git push`, el hook toma de Git cada commit de destino que se va a enviar, copia sus blobs originales a una carpeta temporal fuera del proyecto y ejecuta ambos grupos allí. Los atributos de exportación y filtros de checkout no pueden omitir ni transformar ese contenido. Un arreglo sin commit no puede ocultar un error del commit enviado. Las ramas distintas de la actual también se validan; destinos repetidos se comprueban una vez. Las eliminaciones de referencias no tienen contenido nuevo que validar. No se inspecciona cada commit histórico intermedio, solo cada snapshot de destino. Los submódulos y enlaces simbólicos externos se rechazan explícitamente, no se omiten en silencio.

Si algo falla, el push se cancela; tus archivos, commits y referencias locales se conservan. Corrige, crea el commit correspondiente y repite. El hook no crea commits, no publica por su cuenta y no bloquea el merge local. Las copias temporales se retiran al terminar normalmente.

El hook se puede omitir o desactivar y no protege el servidor. Los entornos local y GitHub pueden diferir aun usando los mismos comandos. No ejecutes pruebas de commits ajenos sin revisar su código: el hook ejecuta comprobaciones del proyecto, no constituye un sandbox.

## 3. Ciclo diario por bloque funcional

Con el árbol limpio y la publicación inicial terminada:

```bash
git switch main
git pull --ff-only
git switch -c feat/inventory-query
# Modifica código, pruebas y documentación de una sola unidad de comportamiento.
python3 scripts/run_checks.py
git diff
git add RUTA_DEL_ARCHIVO
git diff --cached
git commit -m "feat(inventory): add inventory query"
# Publicar la rama es opcional y también ejecuta el hook:
git push -u origin feat/inventory-query
```

Para integrar sin PR, con el árbol limpio y las comprobaciones correctas:

```bash
git switch main
git pull --ff-only
git merge --ff-only feat/inventory-query
git push origin main
```

Si el merge no puede ser fast-forward, se detiene: integra los cambios de `main` en la rama del bloque, revisa los conflictos y vuelve a comprobar. No fuerces el historial ni aceptes un lado completo sin revisar. Un fallo del hook no revierte el merge local; bloquea el envío hasta corregirlo.

Publicar una rama de trabajo no activa actualmente el workflow por push: este sigue limitado a `main`. Si eliges abrir un PR hacia `main`, el CI sí se ejecutará y la plantilla seguirá disponible. No se ha modificado la protección remota.

Prefiere cambios pequeños y coherentes: comportamiento, pruebas y documentación juntos; no un PR para entidades y otro para sus pruebas. `feat`, `fix`, `docs`, `test` y `chore` ayudan a describir la intención de los commits.

## 4. Protección y PR como alternativa opcional

El proyecto no requiere estas reglas actualmente. Si más adelante decides proteger `main`, puedes configurar una regla de protección en **Settings → Branches**:

- Exigir PR antes de fusionar y resolver conversaciones pendientes.
- Exigir los checks **Integridad del repositorio** y **Consistencia documental**. Selecciona los nombres que aparezcan en la ejecución real.
- Exigir que la rama esté actualizada con `main` antes de fusionar.
- Bloquear force pushes y eliminación de `main`; limitar excepciones de bypass.
- Con otro colaborador, exigir al menos una aprobación y descartar aprobaciones obsoletas. Si trabajas solo, no exijas una aprobación ajena que nadie pueda dar.

La disponibilidad de protección depende del plan y de la visibilidad del repositorio; compruébala antes de confiar en ella. **Tener un archivo CI no obliga a que sus checks pasen para fusionar:** la protección es una configuración adicional del servidor. No se ha configurado automáticamente.

Para ramas desactualizadas, integra `main` en la rama del cambio, resuelve conflictos y vuelve a ejecutar las pruebas. No resuelvas conflictos aceptando todos los cambios de un lado sin revisarlos.

## 5. Qué comprueba el CI actual

Se ejecuta en PR hacia `main`, pushes a `main` y manualmente. No usa filtros por ruta, secretos, despliegues, auto-merge ni permisos de escritura. Cancela ejecuciones obsoletas del mismo evento/rama y limita cada job a cinco minutos. Las acciones se fijan por SHA; actualizarlas exige revisar el nuevo origen y ejecutar de nuevo el CI.

| Check | Evidencia que aporta | No demuestra |
|---|---|---|
| Integridad del repositorio | Nombres de archivos sensibles/generados, lectura JSON y Excalidraw, límite explícito del alcance actual | Detección completa de secretos, validez visual de diagramas o cumplimiento funcional |
| Consistencia documental | Pruebas del modelo de datos existente y de las reglas del repositorio | Compilación Java/React, integración PostgreSQL, renderizado o pruebas de seguridad |

Utiliza Python 3 de Ubuntu 24.04 y biblioteca estándar, solo para comprobaciones auxiliares; no introduce un runtime del producto. Para el flujo completo local se requiere Python 3.10 o posterior. El workflow no instala dependencias de aplicaciones inexistentes.

**Al incorporar aplicaciones, ampliar las comprobaciones compartidas y el CI en ese mismo bloque de trabajo.** El comprobador rechaza los manifiestos iniciales `backend/pom.xml`, `frontend/package.json` y Compose en la raíz para evitar un verde documental presentado como validación del producto. Sustituye esa guarda y actualiza sus pruebas cuando existan jobs reales; no la elimines sin cubrir la aplicación correspondiente. Si se adopta otra ubicación, actualiza también la detección. Es una guarda de transición, no detección universal de aplicaciones.

| Componente futuro | Comprobación a incorporar conforme a la arquitectura aprobada |
|---|---|
| Backend | Temurin y Maven fijados, preferentemente Maven Wrapper; verificación Maven y pruebas reales, incluyendo PostgreSQL/Flyway cuando corresponda |
| Frontend | Node y pnpm fijados, `pnpm install --frozen-lockfile`, scripts reales de tipos, pruebas y construcción |
| Infraestructura | Validación de Compose con configuración de ejemplo sin secretos; después arranque y pruebas de salud/persistencia aisladas |

Las versiones aprobadas están en [arquitectura §5.4](../04-arquitectura/arquitectura-y-diseno-tecnico.md#54-versiones-y-compatibilidad-tecnológica). No se generaron aplicaciones ni se cambiaron esas decisiones. Cada grupo nuevo tendrá su comando compartido y su job; no se encadenarán verificaciones independientes ni se ocultarán fallos. Si se usan matrices, desactivar `fail-fast` para conservar los otros resultados. Las dependencias reales, como compilar antes de ciertas pruebas, siguen siendo necesarias.

## 6. Revisión si eliges abrir un PR

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

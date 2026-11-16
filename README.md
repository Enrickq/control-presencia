# Control de Presencia

Proyecto individual de **Ingeniería del Software II** de la Universidad de Las Palmas de Gran Canaria (ULPGC).

## Descripción

Sistema de control de presencia orientado al registro de entradas y salidas de empleados, la definición y asignación de horarios laborales y el cálculo de indicadores de asistencia.

El proyecto se desarrolla de forma incremental, tomando como punto de partida el modelo de registro trabajado en clase y ampliándolo mediante principios de diseño orientado a objetos, programación funcional cuando resulta apropiada, pruebas automatizadas y arquitectura hexagonal.

El objetivo es construir un sistema modular, mantenible y verificable, capaz de representar diferentes situaciones laborales y evolucionar hacia varias aplicaciones independientes.

## Estado actual del proyecto

**Estado: en desarrollo.**

Actualmente se han implementado:

- Modelo de dominio para empleados, lugares y fichajes.
- Registro y consulta de eventos de entrada y salida.
- Validación de secuencias de fichajes.
- Persistencia de eventos en archivos.
- Horarios continuos, partidos y por turnos.
- Soporte de turnos que cruzan la medianoche.
- Modelo de guardias presenciales y localizadas.
- Asignaciones de horarios con periodos de validez.
- Cálculo de indicadores diarios de asistencia.
- Arquitectura hexagonal mediante puertos y adaptadores.
- Proyecto Maven multimódulo.
- Backend HTTP/JSON desarrollado con Javalin.
- API de administración de empleados, horarios y asignaciones.
- Persistencia administrativa mediante archivos JSONL.
- Ejecución del backend con Docker Compose.
- Conservación de datos después de recrear el contenedor.
- Aplicación de escritorio Swing para registrar entradas y salidas mediante la API HTTP.
- Pruebas automatizadas con JUnit 5 y AssertJ.

**Sesión 11: primera aplicación frontend de registro.**

La verificación de la sesión 11, del 15 de noviembre de 2026, superó **141 pruebas** sin fallos, errores ni pruebas omitidas: las 77 existentes y 64 del nuevo módulo. El resultado completo se recoge en la sección de compilación y pruebas.

Todavía están pendientes las aplicaciones de consulta y administración, la integración completa del cálculo de asistencia con los datos administrativos persistidos y otras funcionalidades indicadas al final del documento.

---

## Arquitectura del sistema

El proyecto sigue una **arquitectura hexagonal**, separando el modelo de dominio, los casos de uso y las tecnologías de infraestructura.

El diseño aplica especialmente los siguientes principios:

- **Responsabilidad única (SRP):** cada componente tiene una función definida.
- **Inversión de dependencias (DIP):** los casos de uso dependen de contratos, no de adaptadores concretos.
- **DRY:** se evita duplicar responsabilidades y lógica compartida.
- **Sustitución de Liskov:** las implementaciones de los contratos pueden sustituirse respetando su comportamiento.
- **Bajo acoplamiento:** las tecnologías externas se mantienen fuera del núcleo del dominio.
- **Composición:** las dependencias se construyen y conectan desde un punto de configuración.

### Organización Maven multimódulo

El proyecto contiene cuatro módulos:

| Módulo | Responsabilidad |
|---|---|
| `domain` | Modelos, contratos del dominio y reglas de negocio |
| `application` | Casos de uso y puertos de entrada y salida |
| `backend` | Adaptadores HTTP, persistencia y configuración de ejecución |
| `frontend-register` | Aplicación Swing de fichajes, controlador y cliente HTTP |

La estructura principal es:

```text
control-presencia/
├── pom.xml
├── Dockerfile
├── compose.yaml
├── .dockerignore
├── .gitignore
├── README.md
│
├── domain/
│   ├── pom.xml
│   └── src/
│       ├── main/java/software/ulpgc/register/domain/model/
│       │   ├── attendance/
│       │   └── schedule/
│       └── test/java/
│
├── application/
│   ├── pom.xml
│   └── src/
│       ├── main/java/software/ulpgc/register/application/
│       │   ├── port/
│       │   │   ├── in/
│       │   │   └── out/
│       │   └── usecase/
│       └── test/java/
│
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/software/ulpgc/register/infrastructure/
│       │   ├── adapter/
│       │   │   ├── in/http/
│       │   │   └── out/persistence/
│       │   └── configuration/
│       └── test/java/
│
└── frontend-register/
    ├── pom.xml
    └── src/
        ├── main/java/software/ulpgc/register/frontend/
        │   ├── FrontendMain.java
        │   ├── adapter/
        │   │   ├── http/
        │   │   └── swing/
        │   ├── application/
        │   ├── model/
        │   └── port/
        └── test/java/
```

### Dirección de dependencias

```text
                 domain
                   ▲
                   │
              application
                   ▲
                   │
                backend  ◄── HTTP/JSON ── frontend-register
```

El módulo `backend` también puede depender directamente de `domain` cuando necesita construir o adaptar sus modelos.

Las dependencias cumplen estas reglas:

- `domain` no depende de `application` ni de `backend`.
- `application` depende del dominio, pero no de la infraestructura.
- `backend` depende de los contratos y modelos necesarios para implementar sus adaptadores.
- `frontend-register` no depende de los módulos del servidor en producción. Utiliza tipos propios para el contrato HTTP, `java.net.http.HttpClient` y Jackson.
- Las pruebas de integración del frontend dependen de `backend` con alcance Maven `test`, para probar el cliente contra Javalin y repositorios temporales reales.

De esta forma, la lógica de negocio no necesita conocer Javalin, Docker, Jackson ni los detalles de los archivos.

### Arquitectura del frontend de registro

`FrontendMain` construye y conecta los componentes de la aplicación de escritorio:

| Componente | Responsabilidad |
|---|---|
| `PunchData`, `PunchType`, `PunchReceipt` | Datos del formulario y confirmación de la API |
| `PunchGateway` | Puerto que permite registrar un fichaje |
| `PunchController` | Validación de entrada, envío y estado de la operación |
| `HttpPunchGateway` | Conversión JSON, llamada HTTP y tratamiento de errores |
| `PunchPanel` | Formulario Swing y presentación del resultado |

El controlador depende del puerto remoto y de una vista, sin conocer Swing ni Javalin. Las peticiones se ejecutan fuera del hilo de eventos de Swing; la actualización de los componentes se realiza en ese hilo. Mientras hay una petición pendiente se bloquea un segundo envío. El servidor conserva la responsabilidad de validar la secuencia de entradas y salidas.

### Puertos de entrada

Los puertos de entrada expresan las operaciones disponibles para los consumidores de la aplicación.

Entre los contratos implementados se encuentran:

| Puerto | Responsabilidad |
|---|---|
| `RegisterPunchCommand` | Registrar fichajes |
| `TrackingEventsQuery` | Consultar fichajes almacenados |
| `DailyAttendanceQuery` | Consultar un resumen diario de asistencia |
| `RegisterEmployeeCommand` | Registrar empleados |
| `RegisterScheduleCommand` | Registrar horarios |
| `AssignScheduleCommand` | Asignar horarios |
| `AdministrationQuery` | Consultar datos administrativos |

Los adaptadores HTTP utilizan estos contratos para acceder a los casos de uso sin depender directamente de sus implementaciones concretas.

### Puertos de salida

Los puertos de salida definen las operaciones de persistencia que necesita la aplicación.

| Puerto | Responsabilidad |
|---|---|
| `EventStore` | Registrar y recuperar eventos de presencia |
| `EmployeeRepository` | Almacenar y consultar empleados |
| `ScheduleRepository` | Almacenar y consultar horarios |
| `ScheduleAssignmentRepository` | Almacenar y consultar asignaciones |

Estas interfaces pertenecen al módulo `application`.

### Casos de uso

Los principales casos de uso implementados son:

- `RegisterTrackingEvent`: valida y registra entradas y salidas.
- `ListTrackingEvents`: consulta eventos registrados.
- `CalculateDailyAttendance`: calcula indicadores diarios de asistencia.
- `RegisterEmployee`: registra empleados y rechaza identificadores duplicados.
- `RegisterSchedule`: registra definiciones de horarios.
- `AssignScheduleToEmployee`: comprueba referencias y periodos antes de guardar una asignación.
- `ReadAdministrationData`: recupera empleados, horarios y asignaciones.

Cada caso de uso coordina las operaciones necesarias sin implementar directamente el acceso a archivos ni la comunicación HTTP.

### Adaptadores de infraestructura

El backend incorpora adaptadores de entrada y salida.

**Adaptadores HTTP:**

- `ApiServer`: configura las rutas HTTP y los manejadores de errores.
- `AdministrationHandlers`: transforma las peticiones de administración y sus respuestas.

**Adaptadores de persistencia:**

- `FileEventStore`
- `FileEmployeeRepository`
- `FileScheduleRepository`
- `FileScheduleAssignmentRepository`
- `JsonlFile`
- `ScheduleJsonCodec`

`ScheduleJsonCodec` centraliza la conversión de los distintos tipos de horario para su almacenamiento JSONL.

### Configuración y arranque

`ServerMain` actúa como raíz de composición del backend.

Su responsabilidad es construir los repositorios, conectar los casos de uso con sus puertos y configurar el servidor HTTP.

---

## Registro de presencia

El sistema representa empleados y lugares mediante `Employee` y `Place`.

Los fichajes se modelan mediante:

- `TrackingEvent`: evento de registro con marca temporal.
- `TrackingType`: tipo de evento, entrada o salida.
- `StoredTrackingEvent`: representación de eventos recuperados del almacenamiento.

### Registro de entradas y salidas

El caso de uso `RegisterTrackingEvent` verifica que los eventos respeten una secuencia coherente.

Entre las reglas implementadas se encuentran:

- Una entrada no puede seguir a otra entrada que todavía esté abierta.
- Una salida necesita una entrada anterior.
- No se permiten dos salidas consecutivas sin una nueva entrada.
- Los nuevos eventos deben respetar el orden cronológico.
- La validación de la secuencia se realiza por empleado.

Estas comprobaciones impiden registrar determinadas secuencias incoherentes.

### Fichajes nocturnos

El caso de uso consulta eventos de la fecha UTC del nuevo fichaje y de la fecha UTC anterior.

Esto permite reconocer una entrada antes de medianoche y su correspondiente salida durante el día siguiente.

Ejemplo:

```text
04/11/2026 22:00 UTC → Entrada
05/11/2026 06:00 UTC → Salida
```

La implementación cubre este escenario básico de trabajo nocturno.

Todavía no constituye un mecanismo general de reconstrucción de jornadas abiertas durante varios días ni ofrece garantías de registro atómico entre procesos concurrentes.

### Persistencia de fichajes

`FileEventStore` implementa el puerto `EventStore`.

Almacena los eventos en archivos de texto organizados por año y fecha UTC.

Cada registro contiene:

- Marca temporal.
- Identificador del empleado.
- Identificador del lugar.
- Tipo de fichaje.

Los campos se almacenan en líneas delimitadas por tabuladores.

**Importante:** el almacenamiento de fichajes es diferente del almacenamiento administrativo JSONL.

Los fichajes existentes no se han migrado a JSONL.

---

## Modelo de horarios

El dominio dispone de un contrato común `Schedule` que permite trabajar con diferentes modalidades mediante polimorfismo.

Entre sus operaciones se encuentran la consulta de tramos de trabajo para una fecha y el cálculo de minutos previstos.

### Tramos horarios

`TimeSlot` representa un intervalo mediante una hora de inicio y una hora de finalización.

Permite calcular la duración de un tramo y reconocer intervalos que cruzan medianoche.

### Horarios continuos

`ContinuousSchedule` representa jornadas con un único tramo durante determinados días laborables.

Ejemplo:

```text
Lunes a viernes
08:00–16:00
Duración prevista: 8 horas
```

### Horarios partidos

`SplitSchedule` representa jornadas con dos o más tramos diferenciados.

Ejemplo:

```text
10:00–14:00 → 4 horas
18:00–22:00 → 4 horas

Total previsto: 8 horas
```

El modelo permite ordenar los tramos cronológicamente, comprobar sus restricciones y rechazar solapamientos.

### Horarios por turnos

`ShiftSchedule` permite asociar distintos tramos a los días de la semana.

Ejemplos:

```text
Turno de mañana: 06:00–14:00
Turno de tarde:  14:00–22:00
Turno nocturno:  22:00–06:00
```

Los turnos nocturnos se atribuyen a la fecha en la que comienzan.

El modelo actual permite una distribución semanal de turnos, pero no incorpora rotaciones automáticas de varias semanas.

### Asignaciones de horarios

`ScheduleAssignment` relaciona un empleado con un horario durante un periodo de fechas.

Los extremos del intervalo se consideran incluidos.

Las asignaciones cuentan con:

- Persistencia JSONL.
- Comprobación de existencia del empleado.
- Comprobación de existencia del horario.
- Validación de periodos.
- Rechazo de asignaciones solapadas para un mismo empleado.

Las validaciones actuales no constituyen una garantía de atomicidad frente a operaciones concurrentes.

### Guardias

`Duty` representa una guardia asociada a un empleado, un periodo y una modalidad.

Se contemplan dos modalidades:

- `ON_SITE`: guardia presencial.
- `ON_CALL`: guardia localizada.

En el modelo de dominio, una guardia presencial aporta los minutos previstos correspondientes.

Una guardia localizada representa un periodo de disponibilidad, que no se considera automáticamente tiempo de presencia efectiva.

La administración persistente de guardias, las activaciones de guardias localizadas y su integración completa con la asistencia todavía están pendientes.

---

## Cálculo diario de asistencia

`CalculateDailyAttendance` calcula un resumen diario para un empleado, una fecha y una asignación de horario.

Utiliza el puerto `EventStore` para recuperar los fichajes y el contrato `Schedule` para consultar los periodos de trabajo previstos.

El resultado se representa mediante `AttendanceSummary`.

### Indicadores calculados

- Minutos previstos.
- Minutos trabajados.
- Minutos de retraso.
- Diferencia entre minutos trabajados y previstos.
- Estado de asistencia.

Los estados disponibles son:

| Estado | Significado general |
|---|---|
| `PRESENT` | Jornada con presencia registrada |
| `ABSENT` | Ausencia respecto al trabajo previsto |
| `OFF` | Día sin trabajo previsto |
| `UNSCHEDULED` | Sin una programación aplicable |
| `INCOMPLETE` | Jornada con registros incompletos |

### Ejemplo de cálculo

Horario previsto:

```text
08:00–16:00
480 minutos
```

Fichajes:

```text
Entrada: 08:10
Salida:  16:05
```

Resultado:

```text
Minutos previstos: 480
Minutos trabajados: 475
Retraso: 10 minutos
Diferencia: -5 minutos
Estado: PRESENT
```

### Alcance del cálculo

Los periodos de trabajo se atribuyen a la fecha UTC de entrada.

Para completar una jornada nocturna se consultan los fichajes necesarios de la fecha de inicio y de la siguiente.

El cálculo comprueba la coherencia de la asignación con el empleado, el horario y el periodo solicitado.

Los resultados de jornadas incompletas no deben considerarse definitivos.

Todavía están pendientes la integración automática con los repositorios administrativos, la exposición del cálculo mediante HTTP y el tratamiento completo de festivos, permisos, incidencias y zonas horarias propias de cada centro.

---

## Backend HTTP

El backend utiliza **Javalin 7** sobre Java 27.

El puerto predeterminado es `7070`.

La dirección base para ejecución local es:

`http://localhost:7070`

Las operaciones se comunican mediante HTTP y JSON.

### Endpoints de registro de presencia

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/v1/health` | Comprueba el estado del servidor |
| POST | `/api/v1/punches` | Registra una entrada o salida |
| GET | `/api/v1/punches` | Consulta fichajes por empleado y fecha |

### Registrar una entrada

Petición `POST /api/v1/punches`:

```json
{
  "employeeId": "EMP-DEMO-001",
  "employeeName": "Empleado de prueba",
  "placeId": "OFFICE-DEMO",
  "placeName": "Oficina de prueba",
  "type": "IN"
}
```

Para registrar una salida se utiliza `OUT` en el campo `type`.

La marca temporal del evento se genera en el servidor.

Una petición válida devuelve HTTP `201 Created`.

### Consultar fichajes

Ejemplo:

```text
GET /api/v1/punches?date=2026-11-11&employeeId=EMP-DEMO-001
```

La respuesta contiene los eventos correspondientes al empleado y a la fecha UTC solicitada.

### Endpoints administrativos

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/v1/employees` | Registrar empleado |
| GET | `/api/v1/employees/{id}` | Consultar empleado |
| POST | `/api/v1/schedules` | Registrar horario |
| GET | `/api/v1/schedules/{id}` | Consultar horario |
| POST | `/api/v1/assignments` | Crear asignación |
| GET | `/api/v1/employees/{id}/assignments` | Consultar asignaciones |

### Registrar un empleado

Petición `POST /api/v1/employees`:

```json
{
  "id": "EMP-S10-001",
  "name": "Empleado de prueba"
}
```

Una petición válida devuelve HTTP `201 Created`.

Intentar registrar nuevamente el mismo identificador produce HTTP `409 Conflict` con el mensaje `Employee already exists`.

### Registrar un horario continuo

Petición `POST /api/v1/schedules`:

```json
{
  "type": "CONTINUOUS",
  "id": "OFFICE-S10-001",
  "name": "Oficina",
  "slot": {
    "start": "08:00",
    "end": "16:00"
  },
  "workingDays": [
    "MONDAY",
    "TUESDAY",
    "WEDNESDAY",
    "THURSDAY",
    "FRIDAY"
  ]
}
```

### Registrar un horario partido

Petición `POST /api/v1/schedules`:

```json
{
  "type": "SPLIT",
  "id": "RESTAURANT-S10-001",
  "name": "Restaurante",
  "slots": [
    {
      "start": "10:00",
      "end": "14:00"
    },
    {
      "start": "18:00",
      "end": "22:00"
    }
  ],
  "workingDays": [
    "MONDAY",
    "TUESDAY",
    "WEDNESDAY"
  ]
}
```

### Registrar un turno nocturno

Petición `POST /api/v1/schedules`:

```json
{
  "type": "SHIFT",
  "id": "HOSPITAL-S10-001",
  "name": "Hospital",
  "weeklySlots": {
    "MONDAY": {
      "start": "22:00",
      "end": "06:00"
    }
  }
}
```

Los tres tipos de horario se han registrado y recuperado correctamente mediante peticiones HTTP durante las comprobaciones de integración manual.

### Asignar un horario a un empleado

Petición `POST /api/v1/assignments`:

```json
{
  "employeeId": "EMP-S10-001",
  "scheduleId": "OFFICE-S10-001",
  "validFrom": "2026-11-16",
  "validThrough": "2026-11-30"
}
```

La operación exige que el empleado y el horario existan.

Las asignaciones solapadas para un mismo empleado son rechazadas.

La consulta de asignaciones se realiza mediante:

```text
GET /api/v1/employees/EMP-S10-001/assignments
```

### Respuestas HTTP

| Código | Uso |
|---|---|
| `200 OK` | Consulta correcta |
| `201 Created` | Recurso o fichaje registrado |
| `400 Bad Request` | Datos o parámetros inválidos |
| `404 Not Found` | Recurso solicitado no encontrado |
| `409 Conflict` | Registro duplicado o conflicto de reglas |

Por ejemplo, una asignación solapada produce una respuesta como:

```json
{
  "error": "CONFLICT",
  "message": "Schedule assignments overlap"
}
```

En el estado actual de la API, algunas referencias administrativas inexistentes se clasifican como peticiones inválidas (`400`).

La clasificación de errores podrá refinarse cuando se complete el contrato HTTP.

**Limitación de integración:** el endpoint de fichajes todavía no valida automáticamente que el empleado esté registrado en el catálogo administrativo.

---

## Persistencia

El proyecto utiliza dos mecanismos de almacenamiento basados en archivos.

### Persistencia de eventos de presencia

`FileEventStore` registra los fichajes mediante líneas de texto delimitadas por tabuladores y organizadas por fecha UTC.

Permite recuperar eventos previamente almacenados, incluso mediante nuevas instancias del adaptador.

### Persistencia administrativa JSONL

Los adaptadores administrativos utilizan archivos **JSON Lines (JSONL)**, donde cada línea representa un objeto JSON independiente.

| Archivo | Información |
|---|---|
| `employees.jsonl` | Empleados registrados |
| `schedules.jsonl` | Definiciones de horarios |
| `assignments.jsonl` | Asignaciones entre empleados y horarios |

`JsonlFile` centraliza las operaciones de lectura y escritura.

`ScheduleJsonCodec` permite reconstruir los distintos tipos de horario al recuperar los registros.

El directorio de almacenamiento se configura mediante la variable de entorno `DATA_DIR`.

Por defecto, en ejecución local se utiliza:

```text
data/events
```

En Docker Compose se configura:

```text
/data/events
```

### Alcance y limitaciones

El almacenamiento permite conservar los datos utilizados en las pruebas de integración.

Sin embargo, los repositorios administrativos utilizan archivos de solo adición y no garantizan transacciones entre varios repositorios.

Tampoco proporcionan control completo de concurrencia entre procesos, actualización o eliminación segura de registros ni recuperación transaccional ante fallos durante una escritura.

La auditoría administrativa completa, incluyendo actor, operación y marca temporal, sigue pendiente.

---

## Ejecutar la aplicación de registro

Se necesitan JDK 27 y un entorno gráfico de escritorio. El frontend puede ejecutarse en el equipo y conectarse tanto al backend local como al backend de Docker.

### 1. Compilar y ejecutar las pruebas

Desde la raíz del repositorio:

```bash
mvn clean verify
```

Para construir el frontend y los módulos necesarios para sus pruebas de integración:

```bash
mvn -pl frontend-register -am clean verify
```

El empaquetado genera `frontend-register/target/control-presencia-frontend-register-0.1.0-SNAPSHOT.jar` y su carpeta `lib/`. Para mover la aplicación a otro directorio, hay que conservar el JAR y `lib/` juntos. Las bibliotecas del servidor y de pruebas no se incluyen en `lib/`.

### 2. Iniciar el backend

Con Docker disponible, seguir las instrucciones de la siguiente sección. Como alternativa, desde la raíz del proyecto:

```bash
mvn -pl backend -am install
mvn -f backend/pom.xml org.apache.maven.plugins:maven-dependency-plugin:3.6.1:copy-dependencies -DincludeScope=runtime
```

En PowerShell:

```powershell
java -cp "backend/target/control-presencia-backend-0.1.0-SNAPSHOT.jar;backend/target/dependency/*" software.ulpgc.register.infrastructure.configuration.ServerMain
```

En Linux o macOS, el separador del classpath es `:`:

```bash
java -cp "backend/target/control-presencia-backend-0.1.0-SNAPSHOT.jar:backend/target/dependency/*" software.ulpgc.register.infrastructure.configuration.ServerMain
```

El backend utiliza `PORT=7070` y `DATA_DIR=data/events` de forma predeterminada. Para una prueba manual aislada en PowerShell, puede fijarse `$env:DATA_DIR = "data/session11-demo"` antes de iniciarlo. Esa carpeta no accede al volumen Docker. No deben arrancarse dos backend en el mismo puerto ni varios procesos sobre los mismos archivos de datos.

### 3. Abrir el frontend

En otra terminal, desde la raíz del proyecto:

```bash
java -jar frontend-register/target/control-presencia-frontend-register-0.1.0-SNAPSHOT.jar
```

La dirección predeterminada es `http://localhost:7070`. Se puede configurar mediante `API_BASE_URL` o pasando una dirección como argumento; el argumento tiene prioridad:

```powershell
$env:API_BASE_URL = "http://localhost:7070"
java -jar frontend-register/target/control-presencia-frontend-register-0.1.0-SNAPSHOT.jar
java -jar frontend-register/target/control-presencia-frontend-register-0.1.0-SNAPSHOT.jar http://localhost:7070
java -jar frontend-register/target/control-presencia-frontend-register-0.1.0-SNAPSHOT.jar --help
```

La dirección es la base del servidor; no hay que añadir `/api/v1/punches`.

### Uso y alcance

1. Introducir identificador y nombre del empleado.
2. Introducir identificador y nombre del centro de trabajo.
3. Pulsar **Entrada** o **Salida**.
4. Esperar la confirmación con la marca temporal asignada por el servidor.

El formulario envía `employeeId`, `employeeName`, `placeId`, `placeName` y `type` (`IN` o `OUT`) a `POST /api/v1/punches`. Los nombres son obligatorios según el contrato actual, aunque el almacenamiento de fichajes conserva solamente los identificadores.

Los campos vacíos y los caracteres de control se rechazan antes del envío. La API también protege los identificadores frente a caracteres de control para evitar alterar el formato de los archivos. Los conflictos de secuencia y los errores de conexión se muestran en la interfaz; los datos del formulario se conservan. El cliente no reenvía automáticamente un fichaje: tras un error de red o una respuesta no válida puede desconocerse si el servidor llegó a guardarlo. Antes de volver a fichar, consultar los registros mediante `GET /api/v1/punches`.

Esta pantalla identifica al empleado mediante datos introducidos manualmente. **No proporciona autenticación ni comprueba que exista en el catálogo administrativo.** Tampoco muestra un historial ni calcula asistencia; esas funciones corresponden a las siguientes aplicaciones.

### Recorrido manual reproducible

Con el backend de prueba aislado, usar un identificador nuevo y realizar este recorrido:

1. Intentar enviar con un campo obligatorio vacío: debe aparecer un error sin registrar un fichaje.
2. Registrar una entrada y comprobar la confirmación.
3. Intentar otra entrada: debe rechazarse por conflicto.
4. Registrar una salida y comprobar la confirmación.
5. Consultar los dos eventos mediante la API, usando la fecha UTC de sus marcas temporales.
6. Reiniciar el backend con el mismo `DATA_DIR` y comprobar que siguen disponibles.
7. Detener el backend e intentar un fichaje con otro empleado: debe mostrarse el error y quedar el formulario disponible.

Este recorrido describe resultados esperados; las comprobaciones efectivamente ejecutadas se distinguen en la sección de verificación.

---

## Ejecución mediante Docker Compose

El backend puede ejecutarse en un contenedor Docker construido a partir del código del proyecto. El `Dockerfile` reconoce el nuevo módulo del reactor y construye únicamente `backend` con sus dependencias mediante `-pl backend -am`. La aplicación Swing se ejecuta en el escritorio y no se incluye en el contenedor.

### Requisitos

- Docker Engine.
- Docker Compose.
- Motor de contenedores Linux disponible.

### Iniciar el backend

Desde la carpeta original `control-presencia`, con Docker Desktop y el motor Linux en ejecución:

```bash
docker compose up --build -d backend
```

### Comprobar los contenedores

```bash
docker compose ps
```

### Consultar los registros del backend

```bash
docker compose logs --tail=50 backend
```

### Comprobar el servidor

Abrir en el navegador:

`http://localhost:7070/api/v1/health`

La respuesta esperada es:

```json
{
  "status": "UP"
}
```

### Detener el backend

```bash
docker compose down
```

### Volumen persistente

Docker Compose utiliza `presence-data:/data` para almacenar los datos del backend. La sesión 11 conserva sin cambios `compose.yaml`, el montaje y `DATA_DIR=/data/events`.

Los registros se conservan al detener o recrear el contenedor mientras se mantenga dicho volumen.

Ejecutar Compose desde la misma carpeta y con el mismo nombre de proyecto utilizado anteriormente. Si se utilizó `-p` o `COMPOSE_PROJECT_NAME`, mantener ese valor. Cambiar de carpeta o de nombre de proyecto puede hacer que Compose seleccione otro volumen aunque el anterior siga existiendo. La copia usada para desarrollar esta sesión no debe iniciarse como un despliegue nuevo sobre los datos existentes.

**Advertencia:** `docker compose down -v` elimina también los volúmenes gestionados por Compose y puede provocar la pérdida de los datos almacenados.

El puerto `7070` se publica en la interfaz local del equipo para desarrollo.

### Verificación histórica de persistencia

Se realizaron las siguientes comprobaciones:

1. Registro de fichajes de entrada y salida.
2. Consulta de los fichajes registrados.
3. Rechazo de una entrada consecutiva.
4. Recreación del contenedor y recuperación de los fichajes anteriores.
5. Registro y consulta de un empleado.
6. Registro y consulta de tres tipos de horario.
7. Registro y consulta de una asignación.
8. Rechazo de una asignación solapada.
9. Recreación del contenedor.
10. Recuperación del empleado, los horarios y la asignación previamente registrados.

Estas operaciones funcionaron con los datos utilizados en las comprobaciones manuales.

En la sesión 11 el motor Docker no estaba disponible al comenzar la verificación. La construcción de la imagen y el recorrido con contenedores requieren comprobarse con el motor en ejecución; las pruebas de integración Java utilizan almacenamiento temporal independiente.

---

## Tecnologías utilizadas

| Tecnología | Utilización |
|---|---|
| Java 27 | Implementación del sistema |
| Maven | Construcción y gestión multimódulo |
| JUnit 5 | Pruebas automatizadas |
| AssertJ | Aserciones expresivas en pruebas |
| Javalin 7 | Servidor y API HTTP |
| Jackson | Conversión de datos JSON |
| Swing | Formulario de escritorio para registrar fichajes |
| Java HttpClient | Comunicación del frontend con la API |
| Docker | Contenedorización del backend |
| Docker Compose | Configuración y ejecución del contenedor |
| Git | Control de versiones |
| GitHub | Publicación del repositorio |

---

## Compilación y pruebas

### Requisitos para ejecutar Maven

- JDK 27.
- Maven, o IntelliJ IDEA con integración Maven.
- Dependencias disponibles en el repositorio local o descargables desde Maven Central.

Desde la raíz del proyecto:

```bash
mvn clean verify
```

El proyecto padre construye los cuatro módulos en el orden establecido por las dependencias.

También puede utilizarse IntelliJ IDEA:

1. Abrir el proyecto.
2. Comprobar que está configurado JDK 27.
3. Abrir el panel **Maven**.
4. Seleccionar el proyecto padre `control-presencia`.
5. Ejecutar **Lifecycle → verify**.

### Verificación de la sesión 11

El 15 de noviembre de 2026 se ejecutó `mvn -B clean verify` con JDK 27 y se obtuvo **BUILD SUCCESS** en el proyecto padre y los cuatro módulos.

| Módulo | Pruebas | Fallos | Errores | Omitidas |
|---|---:|---:|---:|---:|
| `domain` | 30 | 0 | 0 | 0 |
| `application` | 23 | 0 | 0 | 0 |
| `backend` | 24 | 0 | 0 | 0 |
| `frontend-register` | 64 | 0 | 0 | 0 |
| **Total** | **141** | **0** | **0** | **0** |

Las 64 pruebas del frontend comprueban validación de campos (10), configuración de arranque (3), controlador (6), presentación Swing y ejecución en su hilo de eventos (3), cliente HTTP (31) e integración con Javalin real (11). Incluyen entrada/salida, conflictos, persistencia tras reiniciar, Unicode, respuestas incorrectas, desconexión, tiempo de espera y prevención del doble envío.

Se comprobó también el arranque del JAR con `--help`, su carpeta `lib/` con únicamente las tres bibliotecas Jackson necesarias y el diseño del formulario mediante renderizado sin ventana. La configuración Compose pasó `docker compose config --quiet`. Esto no sustituye la prueba manual del escritorio ni la construcción de la imagen Docker, que no se ejecutó porque el motor estaba detenido.

Se corrigió una línea vacía anterior a la declaración XML del POM raíz que impedía leer el proyecto con Maven. La API ahora rechaza identificadores con caracteres de control antes de guardarlos en el archivo de fichajes.

Las pruebas de integración arrancan el servidor en un puerto disponible y cierran sus recursos al terminar. No necesitan Docker ni utilizan `data/events` o el volumen persistente del despliegue.

### Resultado histórico de la sesión anterior

El 14 de noviembre de 2026 se ejecutó Maven `verify` desde el proyecto padre.

Resultado:

| Módulo | Pruebas | Fallos | Errores | Omitidas |
|---|---:|---:|---:|---:|
| `domain` | 30 | 0 | 0 | 0 |
| `application` | 23 | 0 | 0 | 0 |
| `backend` | 24 | 0 | 0 | 0 |
| **Total** | **77** | **0** | **0** | **0** |

El proyecto padre y los tres módulos terminaron con:

```text
BUILD SUCCESS
```

### Cobertura funcional de las pruebas

Las pruebas automatizadas verifican, entre otros comportamientos:

- Reglas de horarios continuos.
- Validaciones de horarios partidos.
- Turnos y guardias del modelo de dominio.
- Registro y recuperación de eventos.
- Validación de secuencias de entrada y salida.
- Fichajes que cruzan medianoche.
- Cálculo diario de asistencia.
- Registro de empleados y horarios.
- Validación de asignaciones.
- Detección de periodos solapados.
- Persistencia JSONL de empleados.
- Persistencia de horarios continuos, partidos y nocturnos.
- Recuperación de asignaciones.
- Lectura de archivos persistentes desde nuevas instancias de repositorios.

Las pruebas de persistencia utilizan directorios temporales para evitar modificar los datos de ejecución.

### Pruebas manuales de integración

Además de las 77 pruebas automatizadas, se realizaron comprobaciones HTTP con PowerShell y `Invoke-RestMethod`.

Entre los escenarios comprobados se encuentran:

- Registro y consulta de empleados.
- Rechazo de empleados duplicados.
- Registro y consulta de los tres tipos de horario.
- Creación y consulta de asignaciones.
- Rechazo de periodos solapados.
- Recuperación de registros después de recrear el contenedor Docker.

La sesión 11 añade pruebas HTTP automatizadas de fichajes. Las pruebas HTTP completas de administración y de las futuras aplicaciones siguen pendientes.

---

## Estrategia de control de versiones

El repositorio utiliza dos ramas principales:

- `main`: versiones estables integradas.
- `develop`: desarrollo e incorporación progresiva de funcionalidades.

Los cambios se organizan mediante commits descriptivos.

La implementación ha avanzado mediante hitos de dominio, persistencia, arquitectura multimódulo, backend HTTP, Docker y administración.

Los hitos estables pueden identificarse mediante etiquetas de versión.

Antes de integrar cambios se comprueba la construcción y se ejecutan las pruebas disponibles.

### Integrar la sesión 11 en `develop`

En la carpeta original del repositorio, comprobar primero que la rama activa es `develop` y revisar los cambios de esta sesión:

```bash
git branch --show-current
git status -sb
git diff --check
git diff --stat
mvn clean verify
```

Si hay trabajo ajeno a esta sesión, conservarlo y revisar qué se incluye antes de preparar el commit. Para incorporar los archivos de la sesión 11:

```bash
git add pom.xml frontend-register Dockerfile README.md backend/src/main/java/software/ulpgc/register/infrastructure/adapter/in/http/ApiServer.java
git diff --cached --stat
git diff --cached --check
git commit -m "feat: add Swing punch registration frontend"
```

Antes de publicar, actualizar las referencias y comprobar si hay commits remotos nuevos:

```bash
git fetch origin
git log --oneline --left-right develop...origin/develop
```

Si aparecen commits exclusivos de `origin/develop`, integrarlos y repetir la verificación antes de publicar. Cuando `develop` contenga los cambios remotos y las pruebas hayan pasado:

```bash
git push origin develop
```

Estos comandos no integran cambios en `main`. No se necesita eliminar contenedores ni volúmenes para registrar los cambios en Git.

---

## Limitaciones actuales y trabajo pendiente

El proyecto continúa en desarrollo y no constituye todavía una solución de producción.

### Aplicaciones de usuario

La aplicación de registro de entradas y salidas está implementada en `frontend-register`. Quedan pendientes:

- Aplicación para consultar asistencia e incidencias.
- Aplicación para administrar horarios, asignaciones y guardias.

### Integración funcional

- Validar los fichajes contra el catálogo persistente de empleados.
- Integrar automáticamente los horarios y asignaciones almacenados en el cálculo de asistencia.
- Exponer los resultados de asistencia calculada mediante HTTP.
- Completar la consulta y gestión de incidencias.
- Incorporar una gestión completa de guardias y sus activaciones.
- Incorporar rotaciones automáticas de varias semanas.

### Calendarios y reglas adicionales

- Festivos y calendarios laborales.
- Permisos y ausencias justificadas.
- Zonas horarias específicas de los centros.
- Cambios de horario de verano e invierno.
- Reconstrucción de jornadas abiertas durante periodos prolongados.
- Reglas adicionales de integridad y concurrencia.

### Persistencia y seguridad

- Autenticación y autorización.
- Actualización y eliminación de entidades.
- Auditoría administrativa con información del actor y la operación.
- Garantías transaccionales y de concurrencia.
- Recuperación robusta ante escrituras interrumpidas.

### Verificación y despliegue

- Ampliar las pruebas HTTP a todos los endpoints administrativos.
- Pruebas de integración de las aplicaciones de consulta y administración.
- Verificar la imagen Docker actualizada con el motor de contenedores en ejecución.
- Integración completa de todos los componentes.
- Evaluación de un mecanismo de mensajería si aporta valor a la arquitectura.
- Documentación y capturas de las tres aplicaciones terminadas.

El desarrollo continuará priorizando las funcionalidades exigidas, la simplicidad del diseño, la corrección del dominio y la reproducibilidad de las pruebas.

---

## Objetivo de las siguientes iteraciones

El siguiente hito es conectar la validación de fichajes con el catálogo persistente de empleados y desarrollar la aplicación de consulta de asistencia.

A continuación se incorporará la aplicación de administración de horarios y asignaciones.

Una vez desarrolladas estas interfaces, se completarán las integraciones funcionales, las pruebas y la documentación de entrega.

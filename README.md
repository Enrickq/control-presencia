# Control de Presencia

Proyecto individual de **Ingeniería del Software II** de la Universidad de Las Palmas de Gran Canaria.

## Descripción

Sistema de control de presencia orientado al registro de entradas y salidas de empleados, la definición de horarios laborales y el cálculo de asistencia.

El proyecto se desarrolla de forma incremental, partiendo del modelo de registro trabajado en clase y ampliándolo mediante principios de diseño orientado a objetos, pruebas automatizadas y separación de responsabilidades.

**Estado:** proyecto en desarrollo. Actualmente se han implementado componentes del modelo de dominio, persistencia de fichajes y validaciones de registro. Las aplicaciones de usuario y la integración completa del sistema están pendientes.

## Funcionalidades implementadas

### Registro de presencia

- Representación de empleados y lugares mediante `Employee` y `Place`.
- Modelado de fichajes de entrada y salida con `TrackingEvent` y `TrackingType`.
- Persistencia de fichajes mediante `FileEventStore`.
- Lectura de eventos almacenados por fecha UTC.
- Recuperación de eventos entre distintas instancias del almacén.
- Validación de secuencias de entrada y salida mediante `RegisterTrackingEvent`.
- Detección de entradas consecutivas, salidas sin entrada previa y fichajes fuera de orden.
- Soporte básico para fichajes que cruzan medianoche.

### Modelo de horarios

- Tramos horarios representados mediante `TimeSlot`.
- Horarios continuos mediante `ContinuousSchedule`.
- Horarios partidos mediante `SplitSchedule`.
- Turnos semanales mediante `ShiftSchedule`.
- Asignaciones temporales mediante `ScheduleAssignment`.
- Guardias presenciales y localizadas mediante `Duty` y `DutyMode`.

Estas funcionalidades corresponden al modelo y a los casos de uso implementados hasta ahora. Todavía no constituyen un sistema completo de gestión y consulta de asistencia.

## Arquitectura

El proyecto utiliza **arquitectura hexagonal** y está organizado como un proyecto **Maven multimódulo**.

La separación entre dominio, aplicación e infraestructura permite mantener las reglas de negocio independientes de tecnologías externas, facilitar las pruebas y reducir el acoplamiento.

### Organización de módulos

El proyecto está dividido en tres módulos:

- `domain`: contiene el modelo del dominio, incluyendo empleados, fichajes, horarios, guardias y resultados de asistencia.
- `application`: contiene los casos de uso y los puertos de entrada y salida.
- `backend`: contiene los adaptadores de infraestructura y alojará los adaptadores HTTP y la configuración de ejecución.

La estructura principal es:

```text
control-presencia/
├── pom.xml
├── domain/
│   ├── pom.xml
│   └── src/
│       ├── main/java/software/ulpgc/register/domain/model/
│       │   ├── attendance/
│       │   └── schedule/
│       └── test/java/
├── application/
│   ├── pom.xml
│   └── src/
│       ├── main/java/software/ulpgc/register/application/
│       │   ├── port/
│       │   │   ├── in/
│       │   │   └── out/
│       │   └── usecase/
│       └── test/java/
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/software/ulpgc/register/infrastructure/
│       │   └── adapter/out/persistence/
│       └── test/java/
├── .gitignore
└── README.md
```

### Dirección de dependencias

Las dependencias entre módulos siguen esta organización:

```text
backend ──────► application ──────► domain
   │
   └─────────────────────────────► domain
```

- `domain` no depende de `application` ni de `backend`.
- `application` depende del dominio y define contratos para interactuar con sistemas externos.
- `backend` implementa los adaptadores necesarios para conectar la aplicación con la infraestructura.

Esta separación aplica el principio de inversión de dependencias.

### Puertos de entrada

Los puertos de entrada representan las operaciones disponibles para los consumidores de la aplicación.

Actualmente, `DailyAttendanceQuery` define el contrato para consultar un resumen diario de asistencia.

`CalculateDailyAttendance` implementa dicho contrato.

### Puertos de salida

Los puertos de salida representan servicios externos que necesitan los casos de uso.

`EventStore` define las operaciones necesarias para registrar y consultar eventos de presencia.

Los casos de uso trabajan con este contrato sin depender de una tecnología concreta de persistencia.

### Adaptadores de infraestructura

`FileEventStore` es un adaptador de salida ubicado en `infrastructure.adapter.out.persistence`.

Implementa `EventStore` mediante almacenamiento en archivos.

Esta separación permite sustituir el mecanismo de almacenamiento sin modificar las reglas del dominio ni los casos de uso que utilizan el puerto.

### Casos de uso

- `RegisterTrackingEvent`: valida y registra fichajes de entrada y salida.
- `CalculateDailyAttendance`: calcula indicadores diarios de asistencia a partir de los fichajes y el horario asignado.

Los casos de uso coordinan operaciones de aplicación, mientras que los modelos del dominio representan los conceptos y reglas de negocio.

### Estado de la arquitectura

La separación multimódulo está implementada y se verifica mediante Maven.

El adaptador HTTP, la configuración de arranque del servidor, las aplicaciones frontend y la integración mediante contenedores y mensajería todavía están pendientes.

## Backend HTTP y Docker

El proyecto incorpora un backend HTTP desarrollado con Javalin.

El servidor se ejecuta por defecto en el puerto `7070` y expone operaciones JSON para registrar y consultar eventos de presencia.

### Endpoints disponibles

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/v1/health` | Comprueba el estado del servidor |
| POST | `/api/v1/punches` | Registra un fichaje |
| GET | `/api/v1/punches` | Consulta fichajes por empleado y fecha |

### Ejemplo de registro

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

La marca temporal del evento se genera en el servidor.

### Consulta de eventos

Ejemplo:

```text
GET /api/v1/punches?employeeId=EMP-DEMO-001&date=2026-11-11
```

La respuesta contiene los eventos registrados para el empleado en la fecha solicitada.

### Respuestas y errores

- `201 Created`: fichaje registrado correctamente.
- `400 Bad Request`: petición inválida.
- `409 Conflict`: secuencia de fichajes incoherente.

### Ejecución mediante Docker Compose

Requisitos: Docker Engine y Docker Compose.

Desde la raíz del proyecto:

```bash
docker compose up --build -d
```

Para comprobar el estado:

```bash
docker compose ps
```

Para consultar los registros:

```bash
docker compose logs --tail=50 backend
```

El backend estará disponible en:

`http://localhost:7070`

Para detener el sistema:

```bash
docker compose down
```

### Persistencia

`FileEventStore` almacena los fichajes en archivos de texto.

Docker Compose utiliza un volumen persistente para conservar los eventos al reiniciar o recrear el contenedor.

El directorio configurado dentro del contenedor es `/data/events`.

### Limitaciones actuales

La API permite registrar y consultar fichajes, pero todavía no dispone de autenticación, catálogo persistente de empleados, administración HTTP de horarios ni consulta HTTP del cálculo de asistencia.

El servicio se publica únicamente en la interfaz local del equipo durante el desarrollo.

## Administración de empleados, horarios y asignaciones

El sistema incorpora una API HTTP/JSON que permite registrar empleados, definir horarios de trabajo y asignarlos durante periodos de fechas determinados.

La implementación sigue los principios de la arquitectura hexagonal. Los adaptadores HTTP utilizan los puertos de entrada de la aplicación, los casos de uso aplican las reglas de negocio y los adaptadores de persistencia implementan los puertos de salida.

### Endpoints disponibles

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/v1/employees` | Registrar un empleado |
| GET | `/api/v1/employees/{id}` | Consultar un empleado |
| POST | `/api/v1/schedules` | Registrar un horario |
| GET | `/api/v1/schedules/{id}` | Consultar un horario |
| POST | `/api/v1/assignments` | Asignar un horario a un empleado |
| GET | `/api/v1/employees/{id}/assignments` | Consultar las asignaciones de un empleado |

La dirección base para la ejecución local es `http://localhost:7070`.

### Registro de empleados

Ejemplo de petición `POST /api/v1/employees`:

```json
{
  "id": "EMP-S10-001",
  "name": "Empleado de prueba"
}
```

El sistema permite registrar empleados y recuperar sus datos mediante el identificador.

Se ha comprobado que intentar registrar nuevamente un identificador existente produce una respuesta HTTP `409 Conflict`, con el mensaje `Employee already exists`.

### Tipos de horarios

El sistema permite definir y consultar tres tipos de horarios mediante la API.

**Horario continuo (`CONTINUOUS`)**

Representa un único tramo de trabajo durante determinados días de la semana.

Ejemplo: horario de oficina de lunes a viernes, de 08:00 a 16:00.

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

**Horario partido (`SPLIT`)**

Permite establecer varios tramos de trabajo separados durante una misma jornada.

Ejemplo: horario de restaurante de 10:00 a 14:00 y de 18:00 a 22:00.

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

**Horario por turnos (`SHIFT`)**

Permite definir tramos de trabajo según el día de la semana, incluidos turnos que cruzan la medianoche.

Ejemplo: turno de hospital que comienza el lunes a las 22:00 y termina el martes a las 06:00.

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

Los tres tipos de horarios se han registrado y recuperado correctamente mediante peticiones HTTP.

### Asignación de horarios

Un horario puede asignarse a un empleado existente para un periodo determinado.

Ejemplo de petición `POST /api/v1/assignments`:

```json
{
  "employeeId": "EMP-S10-001",
  "scheduleId": "OFFICE-S10-001",
  "validFrom": "2026-11-16",
  "validThrough": "2026-11-30"
}
```

El sistema comprueba que el empleado y el horario existan antes de guardar la asignación.

También impide asignaciones con periodos solapados para el mismo empleado. Se ha comprobado que repetir una asignación incompatible devuelve HTTP `409 Conflict`, con el mensaje `Schedule assignments overlap`.

Las asignaciones registradas pueden recuperarse mediante `GET /api/v1/employees/{id}/assignments`.

### Persistencia de los datos

Los datos administrativos se almacenan mediante adaptadores de infraestructura que utilizan archivos JSON Lines (JSONL).

| Archivo | Información almacenada |
|---|---|
| `employees.jsonl` | Empleados registrados |
| `schedules.jsonl` | Definiciones de horarios |
| `assignments.jsonl` | Asignaciones de horarios a empleados |

Los archivos se almacenan en el directorio configurado mediante la variable de entorno `DATA_DIR`.

En Docker Compose se utiliza `/data/events`, respaldado por un volumen persistente.

**Comprobación de persistencia realizada:**

1. Se registró un empleado.
2. Se crearon horarios continuos, partidos y nocturnos.
3. Se asignó un horario al empleado.
4. Se eliminó el contenedor mediante `docker compose down`.
5. Se creó nuevamente mediante `docker compose up -d`.
6. Se consultaron los registros mediante HTTP.

Después de recrear el contenedor se recuperaron correctamente el empleado, los tres horarios y su asignación.

Esta prueba confirma que los datos comprobados se mantienen entre recreaciones del contenedor, siempre que se conserve el volumen Docker.

### Validaciones y pruebas

Se han comprobado manualmente los siguientes comportamientos:

- Consulta de empleados registrados.
- Rechazo de identificadores de empleado duplicados.
- Registro y consulta de horarios continuos.
- Registro y consulta de horarios partidos.
- Registro y consulta de turnos nocturnos.
- Creación y consulta de asignaciones de horarios.
- Rechazo de periodos de asignación solapados.
- Recuperación de datos después de recrear el contenedor Docker.

Estas comprobaciones complementan las pruebas automatizadas del proyecto y no las sustituyen.

### Limitaciones actuales

La administración todavía no incluye autenticación, autorización, edición o eliminación de registros.

El almacenamiento JSONL utiliza archivos de solo adición y no proporciona transacciones entre repositorios ni garantías completas de concurrencia entre procesos.

Todavía está pendiente incorporar metadatos de auditoría administrativa, como el usuario responsable de cada operación y su marca temporal.

El registro de fichajes aún no valida automáticamente los empleados contra el catálogo administrativo. La API de asistencia calculada tampoco está conectada todavía a los horarios y asignaciones persistentes.

Quedan pendientes las pruebas HTTP automatizadas, la administración de rotaciones y guardias, y las tres aplicaciones frontend exigidas por el proyecto.


## Almacenamiento de eventos

`FileEventStore` almacena los fichajes en archivos de texto organizados por año y fecha UTC.

Cada registro contiene:

- Marca temporal.
- Identificador del empleado.
- Identificador del lugar.
- Tipo de fichaje.

Los campos se almacenan en líneas de texto delimitadas por tabuladores.

Los datos se recuperan mediante objetos `StoredTrackingEvent`.

El almacenamiento permite leer eventos de una fecha determinada y recuperar información previamente persistida.

Este mecanismo constituye la persistencia inicial de fichajes. La persistencia de horarios, asignaciones y guardias todavía no se ha implementado.

## Validación de fichajes

`RegisterTrackingEvent` comprueba la coherencia de una secuencia de fichajes antes de registrar nuevos eventos.

### Fichajes nocturnos

Para validar un nuevo evento, el caso de uso consulta los registros correspondientes a su fecha UTC y a la fecha UTC anterior.

Esto permite reconocer una entrada realizada antes de medianoche y registrar su salida durante el día siguiente.

Ejemplo:

```text
04/11/2026 22:00 UTC → Entrada
05/11/2026 06:00 UTC → Salida
```

Esta validación cubre el escenario básico de una jornada nocturna.

No constituye todavía un mecanismo general para detectar jornadas abiertas durante varios días ni garantiza operaciones concurrentes atómicas.


## Cálculo diario de asistencia

`CalculateDailyAttendance` calcula un resumen de asistencia
para un empleado, una fecha y una asignación de horario.

El caso de uso utiliza el puerto `EventStore` para obtener los
fichajes y el contrato `Schedule` para consultar los tramos
de trabajo previstos.

El resultado se representa mediante `AttendanceSummary`.

### Indicadores

- Minutos previstos.
- Minutos trabajados.
- Minutos de retraso.
- Diferencia entre minutos trabajados y previstos.
- Estado de asistencia.

Los estados disponibles son `PRESENT`, `ABSENT`, `OFF`,
`UNSCHEDULED` e `INCOMPLETE`.

### Ejemplo

Horario previsto: 08:00–16:00.

Fichajes registrados:

- Entrada: 08:10.
- Salida: 16:05.

Resultado:

- Minutos previstos: 480.
- Minutos trabajados: 475.
- Retraso: 10 minutos.
- Diferencia: -5 minutos.
- Estado: PRESENT.

### Alcance actual

Los periodos de trabajo se atribuyen a la fecha UTC de
entrada. Para completar un turno nocturno se consultan los
fichajes de esa fecha y de la siguiente.

Se comprueba que la asignación corresponda al empleado,
al horario y al periodo solicitado.

El cálculo todavía no gestiona zonas horarias específicas
de los centros, festivos, permisos, incidencias detalladas
ni reconstrucción general de fichajes históricos.

Los minutos de una jornada incompleta no deben considerarse
un resultado definitivo.

### Reglas implementadas

- Una entrada no puede seguir a otra entrada que todavía no se ha cerrado.
- Una salida requiere una entrada anterior.
- No se permiten dos salidas consecutivas.
- Los nuevos eventos deben respetar el orden cronológico respecto al último evento consultado.
- La validación se realiza independientemente para cada empleado.

## Modelo de horarios

### Contrato `Schedule`

La interfaz `Schedule` define las operaciones compartidas por los distintos tipos de horario.

Entre ellas se encuentran la obtención de tramos para una fecha y el cálculo de minutos previstos.

Las implementaciones reutilizan este contrato, permitiendo utilizar diferentes horarios mediante polimorfismo.

### Tramos horarios

`TimeSlot` representa un intervalo mediante una hora de inicio y una hora de finalización.

Permite calcular su duración y reconocer intervalos que cruzan medianoche.

### Horarios continuos

`ContinuousSchedule` representa jornadas con un único tramo horario en los días laborables establecidos.

Ejemplo:

```text
Lunes a viernes
08:00–16:00
Duración prevista: 8 horas
```

### Horarios partidos

`SplitSchedule` representa jornadas divididas en dos o más tramos.

Ejemplo de restaurante:

```text
10:00–14:00 → 4 horas
18:00–22:00 → 4 horas

Total previsto: 8 horas
```

La implementación permite ordenar los tramos cronológicamente, rechazar solapamientos y exigir un descanso entre intervalos.

También protege las colecciones utilizadas para definir los horarios frente a modificaciones externas.

### Turnos semanales

`ShiftSchedule` permite asociar un tramo diferente a cada día de la semana.

Ejemplos:

```text
Turno de mañana: 06:00–14:00
Turno de tarde:  14:00–22:00
Turno nocturno:  22:00–06:00
```

Los turnos nocturnos se atribuyen a la fecha en la que comienzan.

El modelo actual permite definir una distribución semanal de turnos, pero todavía no incorpora rotaciones automáticas de varias semanas.

### Asignaciones de horarios

`ScheduleAssignment` relaciona un empleado con un horario durante un intervalo de fechas.

El inicio y el final del intervalo se consideran incluidos.

La clase representa la asignación, pero todavía están pendientes su persistencia y las validaciones de integridad entre empleados, horarios y periodos de asignación.

### Guardias

`Duty` representa una guardia asociada a un empleado, una fecha de inicio, un tramo horario y una modalidad.

Se utilizan dos modalidades:

- `ON_SITE`: guardia presencial.
- `ON_CALL`: guardia localizada.

En el modelo actual, una guardia presencial aporta sus minutos previstos de presencia.

Una guardia localizada representa un periodo de disponibilidad, que no se considera automáticamente tiempo de presencia efectiva.

Todavía no se gestionan activaciones de guardias localizadas ni su cálculo como trabajo efectivo.

## Tecnologías

- Java 27.
- Maven.
- JUnit 5.
- AssertJ.
- Git y GitHub.

## Compilación y pruebas

### Requisitos

Disponer de JDK 27 y Maven.

Desde la raíz del proyecto, ejecutar:

```bash
mvn clean verify
```

Al ejecutar mvn clean verify desde la raíz, Maven construye los módulos domain, application y backend en el orden establecido por sus dependencias.

Las pruebas automatizadas están distribuidas entre los tres módulos. La compilación se considera correcta cuando todos los módulos finalizan con SUCCESS y Maven muestra BUILD SUCCESS.

También es posible ejecutar la verificación desde IntelliJ IDEA utilizando el ciclo verify del proyecto Maven padre.

Este comando compila el proyecto, ejecuta las pruebas automatizadas y verifica la construcción.

También puede utilizarse IntelliJ IDEA:

1. Abrir el proyecto.
2. Comprobar que está configurado JDK 27.
3. Abrir el panel de Maven.
4. Ejecutar `Lifecycle → verify`.

Las pruebas existentes cubren el almacenamiento y la recuperación de eventos, las secuencias de fichajes y las reglas de los modelos horarios.

La compilación y las pruebas deben verificarse antes de integrar cambios en la rama principal.

## Estrategia de versiones

El repositorio utiliza dos ramas principales:

- `main`: versiones estables e integradas.
- `develop`: incorporación y verificación de nuevas funcionalidades.

Los cambios se incorporan progresivamente mediante commits descriptivos.

Los hitos estables pueden identificarse mediante etiquetas de versión.

## Limitaciones actuales

El proyecto continúa en desarrollo. Entre las funcionalidades pendientes se encuentran:

- Backend HTTP y contratos JSON.
- Aplicación de registro de entradas y salidas.
- Aplicación de consulta de presencia e incidencias.
- Aplicación de administración de horarios y asignaciones.
- Persistencia de horarios, asignaciones y guardias.
- Cálculo completo de horas trabajadas, retrasos, ausencias y desviaciones.
- Rotaciones de turnos de varias semanas.
- Gestión de festivos y calendarios laborales.
- Tratamiento de zonas horarias y cambios de hora de los centros.
- Activaciones de guardias localizadas.
- Validación de jornadas abiertas durante más de dos fechas UTC.
- Comprobación de solapamientos entre asignaciones.
- Validación de referencias a empleados y horarios existentes.
- Garantías de concurrencia en el registro de fichajes.
- Contenedorización e integración de los componentes del sistema.

Estas limitaciones se irán abordando conforme avance la implementación.
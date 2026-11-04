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

El proyecto aplica una separación inicial entre dominio, casos de uso, puertos y adaptadores, siguiendo los principios de la arquitectura hexagonal.

La lógica de aplicación depende de contratos y no de implementaciones concretas de persistencia.

### Organización del código

```text
src/
├── main/
│   └── java/software/ulpgc/register/
│       ├── model/
│       │   └── schedule/
│       ├── application/
│       │   ├── port/
│       │   └── usecase/
│       └── io/
└── test/
    └── java/software/ulpgc/register/
```

Responsabilidades principales:

- `model`: entidades, eventos y conceptos del dominio.
- `model.schedule`: horarios, tramos, asignaciones y guardias.
- `application.port`: contratos utilizados por la lógica de aplicación.
- `application.usecase`: casos de uso y validaciones.
- `io`: implementaciones de persistencia.
- `src/test`: pruebas automatizadas.

### Abstracción de persistencia

`EventStore` define las operaciones necesarias para registrar y consultar eventos.

`FileEventStore` proporciona una implementación basada en archivos.

El caso de uso `RegisterTrackingEvent` utiliza la interfaz `EventStore`, evitando depender directamente de la implementación concreta de almacenamiento.

Esta separación permite sustituir el adaptador de persistencia sin introducir cambios innecesarios en la lógica de aplicación.

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

### Reglas implementadas

- Una entrada no puede seguir a otra entrada que todavía no se ha cerrado.
- Una salida requiere una entrada anterior.
- No se permiten dos salidas consecutivas.
- Los nuevos eventos deben respetar el orden cronológico respecto al último evento consultado.
- La validación se realiza independientemente para cada empleado.

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
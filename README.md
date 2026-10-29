# Control de Presencia

Proyecto individual de Ingeniería del Software II de la Universidad de Las Palmas de Gran Canaria.

## Descripción

Sistema de control de presencia orientado al registro de entradas y salidas de empleados, la gestión de horarios y la
consulta de asistencia.

## Estado actual

Se ha desarrollado el modelo inicial de registro de presencia, tomando como punto de partida el código trabajado en
clase.

La implementación actual incluye:

- Representación de empleados y lugares.
- Modelado de eventos de entrada y salida.
- Abstracción del almacenamiento mediante `EventStore`.
- Persistencia de eventos en archivos mediante `FileEventStore`.
- Pruebas unitarias con JUnit y AssertJ.
- Lectura de eventos almacenados por fecha.
- Recuperación de fichajes persistidos entre distintas instancias de almacenamiento.
- Validación de secuencias de entrada y salida mediante un caso de uso.
- Detección de entradas duplicadas, salidas incoherentes y eventos fuera de orden.

## Validación de fichajes

La aplicación incorpora el caso de uso `RegisterTrackingEvent`,
encargado de validar los eventos antes de registrarlos.

La validación se realiza por empleado y fecha UTC.

Reglas implementadas:

- Se permite una entrada cuando no existe otra entrada pendiente.
- Una salida requiere una entrada anterior.
- No se permiten dos entradas consecutivas.
- No se permiten dos salidas consecutivas.
- Los fichajes deben respetar el orden cronológico.
- Los registros de distintos empleados se validan independientemente.

El caso de uso utiliza la interfaz `EventStore` como puerto de salida,
manteniendo independiente la lógica de aplicación de la persistencia
concreta.

La implementación actual no contempla todavía jornadas que crucen
medianoche, concurrencia de registros ni asignaciones de horarios.

## Tecnologías

- Java 27
- Maven
- JUnit 5
- AssertJ

## Almacenamiento de eventos

La interfaz `EventStore` define las operaciones de registro y
lectura de eventos de presencia.

`FileEventStore` implementa estas operaciones mediante archivos
de texto organizados por año y fecha UTC.

Cada evento almacena la marca temporal, el identificador del
empleado, el identificador del lugar y el tipo de fichaje.

La lectura devuelve objetos `StoredTrackingEvent` y permite:

- Recuperar los eventos correspondientes a una fecha.
- Mantener el orden de registro.
- Obtener una lista vacía cuando no existen eventos.
- Recuperar información utilizando una nueva instancia del almacén.

La implementación se verifica mediante pruebas JUnit y AssertJ.

## Compilación y pruebas

Para ejecutar las pruebas desde la raíz del proyecto:

```bash
mvn clean test
```

## Estructura

- `model`: representación de los datos del dominio.
- `io`: interfaz y adaptación de la persistencia.
- `src/test`: pruebas automatizadas.

## Alcance actual

Esta primera versión implementa el registro persistente de eventos. La gestión de horarios, la API HTTP y las interfaces
de usuario todavía no forman parte de esta versión.